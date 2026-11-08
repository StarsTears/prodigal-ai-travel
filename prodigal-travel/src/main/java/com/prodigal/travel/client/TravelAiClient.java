package com.prodigal.travel.client;

import com.prodigal.travel.advisor.LoggerAdvisor;
import com.prodigal.travel.advisor.SystemMessageFirstAdvisor;
import com.prodigal.travel.chatmemroy.MySQLChatMemory;
import com.prodigal.travel.constants.TravelConstant;
import com.prodigal.travel.rag.RagKnowledgeRetriever;
import com.prodigal.travel.service.alert.AiModelAlertService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

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

    private final ChatClient chatClient;

    /**
     * 会话记忆。RAG 链路只用它「写入留存」：{@link #ragChatClient} 不挂记忆 Advisor，
     * 所以写入的历史不会参与下一次 RAG 生成，严格知识库约束不变。
     */
    private final MySQLChatMemory chatMemory;

    /**
     * RAG 专用 ChatClient：不挂记忆 Advisor、不注册任何工具。
     * 普通问答用的 {@link #chatClient} 带 MessageChatMemoryAdvisor 且会注册联网/天气/图片等工具，
     * 直接给 RAG 用会把历史对话与工具结果当成事实来源，等于绕过知识库。
     */
    private final ChatClient ragChatClient;

    /**
     * 可选依赖必须按类型注入（构造器 / @Autowired），不能用 @Resource。
     * @Resource 会把字段名当 Bean 名解析：这里的名字恰好与 PgVectorStoreConfig 的
     * pgVectorStore Bean 相同，Spring 会试图把 PgVectorStore 适配成 ObjectProvider，
     * 启动期直接抛 BeanNotOfRequiredTypeException。
     */
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final ObjectProvider<ToolCallbackProvider> toolCallbackProviders;

    @Resource
    private RagKnowledgeRetriever ragKnowledgeRetriever;
    @Resource
    private ToolCallback[] allTools;
    @Resource
    private AiModelAlertService aiModelAlertService;

    public TravelAiClient(@Qualifier("dashScopeChatModel") ChatModel dashscopeChatModel,
                          MySQLChatMemory chatMemory,
                          ObjectProvider<VectorStore> vectorStoreProvider,
                          ObjectProvider<ToolCallbackProvider> toolCallbackProviders) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.toolCallbackProviders = toolCallbackProviders;
        this.chatMemory = chatMemory;
        this.chatClient = ChatClient.builder(dashscopeChatModel)
                // 不在 builder 上设置 defaultSystem：每个请求显式设置，避免出现两个 SystemMessage
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new SystemMessageFirstAdvisor())
                .build();
        // 严格知识库模式：无默认 Advisor、无工具，唯一外部数据只能是检索片段
        this.ragChatClient = ChatClient.builder(dashscopeChatModel).build();
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
     * 严格知识库问答（流式）：本地规整查询 -> pgvector 检索 -> 证据校验 -> 仅凭证据生成。
     *
     * <p>关键约束：
     * <ul>
     *     <li>用不带记忆 Advisor、不注册任何工具的 {@link #ragChatClient}，杜绝历史对话与工具结果混入；</li>
     *     <li>使用独立的严格系统提示词，禁止模型使用自身知识或联网补充；</li>
     *     <li>知识库无有效证据时直接返回固定拒答，连生成模型都不调用。</li>
     * </ul>
     * chatId 是 {@code userId:chatId} 形式的会话键。RAG 生成阶段不读会话记忆，
     * 但流正常结束后会把「原问题 + 最终答复」写入 MySQL，供刷新/切换会话后回看。
     */
    public Flux<String> doChatWithRag(String message, String chatId) {
        VectorStore store = resolveVectorStore();
        if (store == null) {
            return Flux.error(new IllegalStateException(
                    "VectorStore 未配置：请启用 PgVectorStoreConfig 并配置 PostgreSQL（pgvector）数据源。"));
        }
        // 每次请求独立的累加器；Reactor 保证同一 Flux 的 onNext 串行执行。
        // 只有正常完成（onComplete）才落库：异常、超时、客户端断开都不会写入半截答案。
        StringBuilder answerBuffer = new StringBuilder();
        // 检索是阻塞调用，放到 boundedElastic，避免占用 Servlet 请求线程。
        // 必须用 Optional 包装：Mono.fromCallable 返回 null 会被当作空流，flatMapMany 不会执行，
        // 那样「无证据」就变成前端收不到任何内容，而不是收到拒答。
        return Mono.fromCallable(() -> Optional.ofNullable(
                        ragKnowledgeRetriever.retrieve(message, store)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(evidence -> {
                    if (evidence.isEmpty()) {
                        log.info("RAG 知识库无相关证据，直接拒答（不调用生成模型）, chatId={}", chatId);
                        return Flux.just(TravelConstant.RAG_NO_RESULT_ANSWER);
                    }
                    return ragChatClient.prompt()
                            .system(TravelConstant.RAG_STRICT_SYSTEM_PROMPT + evidence.get())
                            .user(message)
                            .advisors(new LoggerAdvisor())
                            .stream()
                            .content();
                })
                .doOnNext(chunk -> {
                    if (chunk != null) {
                        answerBuffer.append(chunk);
                    }
                })
                .doOnComplete(() -> saveRagTurn(message, chatId, answerBuffer.toString()))
                .doOnError(error -> aiModelAlertService.tryAlert(error, "doChatWithRag"));
    }

    /**
     * 只写不读：把完整的一轮 RAG 问答落到 MySQL 会话记忆（Redis 同步刷新缓存）。
     *
     * <p>与 Manus / 普通问答保持同一套 {@code chatMemory.add} 写入格式，因此切换模式时上下文连贯，
     * 但 {@link #ragChatClient} 没有记忆 Advisor，这些历史不会被回灌进 RAG 生成，
     * 不会出现「上一次的答案变成这一次的事实依据」。写库失败只告警，不影响已经完成的流式响应。
     */
    private void saveRagTurn(String message, String conversationId, String answer) {
        if (!StringUtils.hasText(answer)) {
            return;
        }
        // DB 写入是阻塞 IO，不能放在 Reactor 完成信号所在线程（可能是 Netty event loop）
        Mono.fromRunnable(() -> {
                    try {
                        chatMemory.add(conversationId,
                                List.of(new UserMessage(message), new AssistantMessage(answer)));
                    } catch (Exception e) {
                        log.warn("写入 RAG 会话记忆失败 conversationId={}: {}", conversationId, e.getMessage());
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(ignored -> {},
                        e -> log.warn("写入 RAG 会话记忆调度失败 conversationId={}: {}",
                                conversationId, e.getMessage()));
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
            ToolCallbackProvider provider = toolCallbackProviders.getIfAvailable();
            if (provider != null) {
                spec = spec.toolCallbacks(provider);
            }
            if (allTools != null) {
                spec = spec.toolCallbacks(allTools);
            }
        }
        return spec;
    }

    /**
     * 解析向量库：没有配置返回 null，配置了多个实现时取排序最前的一个，避免 RAG 接口直接 500。
     * 包级可见，便于不依赖外部服务的注入回归测试直接验证。
     */
    VectorStore resolveVectorStore() {
        try {
            return vectorStoreProvider.getIfAvailable();
        } catch (NoUniqueBeanDefinitionException e) {
            log.warn("检测到多个 VectorStore Bean，使用第一个可用实例：{}", e.getMessage());
            return vectorStoreProvider.orderedStream().findFirst().orElse(null);
        }
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
