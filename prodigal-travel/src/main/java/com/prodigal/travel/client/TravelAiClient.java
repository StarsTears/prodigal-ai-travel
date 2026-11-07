package com.prodigal.travel.client;

import com.prodigal.travel.advisor.LoggerAdvisor;
import com.prodigal.travel.advisor.SystemMessageFirstAdvisor;
import com.prodigal.travel.chatmemroy.MySQLChatMemory;
import com.prodigal.travel.constants.TravelConstant;
import com.prodigal.travel.rag.QueryRewriter;
import com.prodigal.travel.service.alert.AiModelAlertService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 旅游助手问答链路。
 *
 * <p>本次优化：
 * <ul>
 *     <li>修复系统提示词里重复拼接用户消息的问题（旧版 {@code buildMessageWithClientIp} 返回
 *         {@code message + ctx}，又作为 system 追加，等于把用户输入复制进系统区，既浪费 token
 *         又放大提示注入风险）；</li>
 *     <li>RAG 改为显式「查询重写 -> 向量检索 -> 系统上下文增强 -> 流式生成」管线，
 *         原实现把改写查询直接当成 user 消息，还叠加 MultiQueryExpander 造成多次额外模型调用，
 *         且聊天记忆里保存的是被改写过的问题；</li>
 *     <li>统一系统消息置顶 Advisor、统一告警包裹、去掉未使用的 VectorStore 局部变量；</li>
 *     <li>所有方法的 ChatClient 请求规格由 {@link #baseSpec} 统一构造，避免各接口能力不一致。</li>
 * </ul>
 */
@Slf4j
@Component
public class TravelAiClient {

    private static final int RAG_TOP_K = 5;
    private static final double RAG_SIMILARITY_THRESHOLD = 0.3;

    private final ChatClient chatClient;

    @Resource
    private ObjectProvider<VectorStore> pgVectorStore;
    @Resource
    private QueryRewriter queryRewriter;
    @Resource
    private ToolCallback[] allTools;
    /** MCP 关闭时可能没有该 Bean，用 ObjectProvider 保证应用仍可启动 */
    @Resource
    private ObjectProvider<ToolCallbackProvider> toolCallbackProviders;
    @Resource
    private AiModelAlertService aiModelAlertService;

    public TravelAiClient(@Qualifier("dashScopeChatModel") ChatModel dashscopeChatModel,
                          MySQLChatMemory chatMemory) {
        this.chatClient = ChatClient.builder(dashscopeChatModel)
                // 不在 builder 上设置 defaultSystem：每个请求显式设置，避免出现两个 SystemMessage
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new SystemMessageFirstAdvisor())
                .build();
    }

    /** AI 基础对话，支持多轮记忆与本地工具 */
    public String doChat(String message, String chatId) {
        return executeWithAlert("doChat", () -> baseSpec(message, chatId, "", false)
                .call().chatResponse().getResult().getOutput().getText());
    }

    /** 带工具的同步对话（保留给非流式调用方） */
    public String doChatWithTool(String message, String chatId) {
        return executeWithAlert("doChatWithTool", () -> baseSpec(message, chatId, "", true)
                .call().chatResponse().getResult().getOutput().getText());
    }

    /**
     * 默认问答入口：系统提示 + 记忆 + 本地工具 + MCP 工具，同步返回。
     */
    public String doChatWithMCP(String message, String chatId, String clientIp,
                                Double latitude, Double longitude, String browserGeolocationStatus) {
        String context = buildClientContext(clientIp, latitude, longitude, browserGeolocationStatus);
        return executeWithAlert("doChatWithMCP", () -> baseSpec(message, chatId, context, true)
                .call().chatResponse().getResult().getOutput().getText());
    }

    /** 默认问答入口（SSE 流式） */
    public Flux<String> doChatWithMCPSSE(String message, String chatId, String clientIp,
                                         Double latitude, Double longitude,
                                         String browserGeolocationStatus) {
        String context = buildClientContext(clientIp, latitude, longitude, browserGeolocationStatus);
        return baseSpec(message, chatId, context, true)
                .stream()
                .content()
                .doOnError(error -> aiModelAlertService.tryAlert(error, "doChatWithMCPSSE"));
    }

    /**
     * 知识库问答（流式）：查询重写 -> 向量检索 -> 上下文增强 -> 生成。
     * 用户原始问题始终作为 user 消息，检索上下文放在 system 区，历史记忆记录的是真实提问。
     */
    public Flux<String> doChatWithRag(String message, String chatId) {
        VectorStore store = pgVectorStore.getIfAvailable();
        if (store == null) {
            return Flux.error(new IllegalStateException(
                    "VectorStore 未配置：请启用 PgVectorStoreConfig 并配置 PostgreSQL（pgvector）数据源。"));
        }
        // 查询重写 + 向量检索都是阻塞调用，放到 boundedElastic，避免占用 Servlet 请求线程
        return Mono.fromCallable(() -> buildRagSystemContext(message, store))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(ragContext -> chatClient.prompt()
                        .system(TravelConstant.SYSTEM_PROMPT + ragContext)
                        .user(message)
                        .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId)
                                .param("TOP_K", TravelConstant.CHAT_MEMORY_MESSAGE_LIMIT))
                        .advisors(new LoggerAdvisor())
                        .stream()
                        .content())
                .doOnError(error -> aiModelAlertService.tryAlert(error, "doChatWithRag"));
    }

    /**
     * 统一构造请求规格：系统提示 / 会话记忆 / 日志 / 本地工具 / MCP 工具。
     * 各接口不再各写一份，避免「有的接口有记忆、有的没有 MCP」这类隐性差异。
     */
    private ChatClient.ChatClientRequestSpec baseSpec(String message, String chatId,
                                                      String systemSuffix, boolean withTools) {
        ChatClient.ChatClientRequestSpec spec = chatClient.prompt()
                .system(TravelConstant.SYSTEM_PROMPT + (systemSuffix == null ? "" : systemSuffix))
                .user(message)
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, chatId)
                        .param("TOP_K", TravelConstant.CHAT_MEMORY_MESSAGE_LIMIT))
                .advisors(new LoggerAdvisor());
        if (withTools) {
            ToolCallbackProvider provider = toolCallbackProviders == null
                    ? null : toolCallbackProviders.getIfAvailable();
            if (provider != null) {
                spec = spec.toolCallbacks(provider);
            }
            if (allTools != null) {
                spec = spec.toolCallbacks(allTools);
            }
        }
        return spec;
    }

    private String buildRagSystemContext(String message, VectorStore store) {
        String retrievalQuery;
        try {
            retrievalQuery = queryRewriter.rewriteForRetrieval(message);
        } catch (Exception e) {
            log.warn("查询重写失败，回退使用原始问题检索：{}", e.getMessage());
            retrievalQuery = message;
        }
        List<Document> documents = store.similaritySearch(SearchRequest.builder()
                .query(retrievalQuery)
                .topK(RAG_TOP_K)
                .similarityThreshold(RAG_SIMILARITY_THRESHOLD)
                .build());
        if (documents == null || documents.isEmpty()) {
            return "\n\n[知识库检索] 未检索到相关资料。请基于通用旅游知识谨慎回答，"
                    + "明确说明该结论不来自知识库，不要编造具体数据。";
        }
        String context = documents.stream()
                .map(doc -> doc.getText() == null ? "" : doc.getText())
                .filter(text -> !text.isBlank())
                .collect(Collectors.joining("\n\n---\n\n"));
        if (context.isBlank()) {
            return "\n\n[知识库检索] 未检索到可用正文，请基于通用知识谨慎回答。";
        }
        return "\n\n## 知识库检索结果（仅作事实依据，不要复述本节）\n"
                + context
                + "\n\n资料不足时如实说明，不要编造景点、票价、时间等具体数据。";
    }

    /** 只生成系统上下文，不再把用户消息拼进来 */
    private String buildClientContext(String clientIp, Double latitude, Double longitude,
                                      String browserGeolocationStatus) {
        boolean hasGeo = latitude != null && longitude != null;
        boolean hasIp = clientIp != null && !clientIp.isBlank();
        if (!hasGeo && !hasIp) {
            return "";
        }
        StringBuilder ctx = new StringBuilder("\n\n[系统上下文-请勿直接复述]\n");
        if (hasGeo) {
            ctx.append("用户浏览器精确定位：纬度 ").append(latitude)
                    .append("，经度 ").append(longitude)
                    .append("。查询用户所在地天气时调用 getWeatherByCoordinates(")
                    .append(latitude).append(", ").append(longitude).append(", true或false)。\n");
        }
        if (hasIp) {
            if (!hasGeo && browserGeolocationStatus != null
                    && browserGeolocationStatus.equalsIgnoreCase("denied")) {
                ctx.append("用户已拒绝浏览器定位权限，请使用 IP 粗定位作为降级。\n");
            }
            ctx.append("当前用户真实IP: ").append(clientIp).append("\n");
            ctx.append(hasGeo
                    ? "若坐标与公网 IP 明显不符（如 VPN），可结合 getIPLocation 与该 IP 再判断。\n"
                    : "如需定位，请调用 getIPLocation 并把该 IP 作为参数传入。\n");
        }
        return ctx.toString();
    }

    private <T> T executeWithAlert(String operation, Supplier<T> action) {
        try {
            return action.get();
        } catch (RuntimeException exception) {
            aiModelAlertService.tryAlert(exception, operation);
            throw exception;
        }
    }
}
