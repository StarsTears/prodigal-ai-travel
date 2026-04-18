package com.prodigal.travel.client;

import com.prodigal.travel.advisor.LoggerAdvisor;
import com.prodigal.travel.chatmemroy.MySQLChatMemory;
import com.prodigal.travel.constants.TravelConstant;
import com.prodigal.travel.rag.QueryRewriter;
import com.prodigal.travel.rag.TravelRagCustomAdvisorFactory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
//import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;



/**
 * 旅游助手专用 {@link ChatClient}、向量库与本地工具注册。
 * <p>
 *
 */
@Slf4j
@Component
public class TravelAiClient {
    private final ChatClient chatClient;
    /**
     * 由 {@link com.prodigal.travel.rag.PgVectorStoreConfig} 提供；未启用 PostgreSQL/pgvector 时不注册 Bean，此处可选注入以免拖垮整应用。
     */
    @Autowired
    private ObjectProvider<VectorStore> pgVectorStore;
    @Resource
    private QueryRewriter queryRewriter;
    @Resource
    private ToolCallback[] allTools;

    private final ChatModel dashscopeChatModel;

    /**
     * 基于内存的对话记忆
     * @param dashscopeChatModel
     */
//    public TravelAiClient(ChatModel dashscopeChatModel) {
//        this.dashscopeChatModel = dashscopeChatModel;
//        //1、基于内存的对话记忆
//        ChatMemory chatMemory = new InMemoryChatMemory();
//        chatClient = ChatClient.builder(dashscopeChatModel)
//                .defaultSystem(TravelConstant.SYSTEM_PROMPT)
//                .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory))
//                .build();
//    }

    /**
     * 基于 MySQL 的会话记忆
     * @param dashscopeChatModel
     * @param chatMemory
     */
    public TravelAiClient(@Qualifier("dashScopeChatModel") ChatModel dashscopeChatModel, MySQLChatMemory chatMemory) {
        this.dashscopeChatModel = dashscopeChatModel;
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(TravelConstant.SYSTEM_PROMPT)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    /**
     * AI 基础对话，支持多轮对话
     *
     * @param message 用户输入
     * @param chatId  会话Id
     * @return
     */
    public String doChat(String message, String chatId) {
        ChatResponse chatResponse = chatClient.prompt()
                .user(message)
                //对话Id、关联对话数
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId)
                        .param("TOP_K", TravelConstant.CHAT_MEMORY_MESSAGE_LIMIT))
                //开启日志
                .advisors(new LoggerAdvisor())
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        return chatResponse.getResult().getOutput().getText();
    }

    /**
     * 带 RAG 的对话-使用知识库回答
     * 1、重写查询
     * 2、基于 pgVector知识库回答
     * 3、基于 pgVector知识库的检索增强
     *
     * @param message 用户输入
     * @param chatId  当前会话Id
     * @return
     */
    public String doChatWithRag(String message, String chatId) {
        VectorStore store = pgVectorStore.getIfAvailable();
        if (store == null) {
            throw new IllegalStateException(
                    "VectorStore 未配置：请取消注释 PgVectorStoreConfig 的 @Configuration，并配置 PostgreSQL（pgvector）数据源。");
        }
        //重写查询
        String rewriterMessage = queryRewriter.doRewriteQuery(message);

        //对向量数据库中的所有文档执行相似性搜索 该与 RetrievalAugmentationAdvisor 检索功能重叠
        //造成token 浪费
//        QuestionAnswerAdvisor questionAnswerAdvisor = QuestionAnswerAdvisor.builder(pgVectorStore)
//                .searchRequest(SearchRequest.builder().similarityThreshold(0.7d).topK(3).build())
//                .build();

        ChatResponse chatResponse = chatClient.prompt()
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId) //对话id
                        .param("TOP_K", TravelConstant.CHAT_MEMORY_MESSAGE_LIMIT) //关联会话的条数
                )
                .user(rewriterMessage)
                //开启日志
                .advisors(new LoggerAdvisor())
                //基于 pgVector知识库回答
//                .advisors(questionAnswerAdvisor)
                //检索增强顾问
                .advisors(TravelRagCustomAdvisorFactory.createRetrievalAugmentationAdvisor(store, dashscopeChatModel))
                //添加工具
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        return chatResponse.getResult().getOutput().getText();
    }

    /**
     * 工具
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithTool(String message, String chatId) {
        ChatResponse chatResponse = chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId) //对话id
                        .param("TOP_K", TravelConstant.CHAT_MEMORY_MESSAGE_LIMIT) //关联会话的条数
                )
                //开启日志
                .advisors(new LoggerAdvisor())
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        return chatResponse.getResult().getOutput().getText();
    }

    @Resource
    private ToolCallbackProvider toolCallbackProvider;
    /**
     * MCP
     * @param message
     * @param chatId
     * @return
     */
    public String doChatWithMCP(
            String message,
            String chatId,
            String clientIp,
            Double latitude,
            Double longitude,
            String browserGeolocationStatus) {
        String finalSystemPrompt = buildMessageWithClientIp(
                message, clientIp, latitude, longitude, browserGeolocationStatus);

        ChatClient.ChatClientRequestSpec clientRequestSpec = chatClient.prompt()
                .user(message);
        ChatResponse chatResponse = clientRequestSpec
                .system(TravelConstant.SYSTEM_PROMPT + finalSystemPrompt)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId) //对话id
                        .param("TOP_K", TravelConstant.CHAT_MEMORY_MESSAGE_LIMIT) //关联会话的条数
                )
                //开启日志
                .advisors(new LoggerAdvisor())
                //MCP 的工具
                .toolCallbacks(toolCallbackProvider)
                //工具调用
                .toolCallbacks(allTools)
                .call()
                .chatResponse();
        return chatResponse.getResult().getOutput().getText();
    }

    /**
     * * 使用 SSE 流式传输
     * @param message
     * @param chatId
     * @return
     */
    public Flux<String> doChatWithMCPSSE(
            String message,
            String chatId,
            String clientIp,
            Double latitude,
            Double longitude,
            String browserGeolocationStatus) {
        String finalSystemPrompt = buildMessageWithClientIp(
                message, clientIp, latitude, longitude, browserGeolocationStatus);

        ChatClient.ChatClientRequestSpec clientRequestSpec = chatClient.prompt()
                .user(message);

        VectorStore store = pgVectorStore.getIfAvailable();

        return clientRequestSpec
                .system(TravelConstant.SYSTEM_PROMPT+finalSystemPrompt)
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, chatId) //对话id
                        .param("TOP_K", TravelConstant.CHAT_MEMORY_MESSAGE_LIMIT) //关联会话的条数
                )
                //开启日志
                .advisors(new LoggerAdvisor())
                //检索增强顾问
//                .advisors(TravelRagCustomAdvisorFactory.createRetrievalAugmentationAdvisor(store, dashscopeChatModel))
                //MCP 的工具
                .toolCallbacks(toolCallbackProvider)
                //工具调用
                .toolCallbacks(allTools)
                .stream()
                .content();
    }

    private String buildMessageWithClientIp(
            String message,
            String clientIp,
            Double latitude,
            Double longitude,
            String browserGeolocationStatus) {
        boolean hasGeo = latitude != null && longitude != null;
        boolean hasIp = clientIp != null && !clientIp.isBlank();
        if (!hasGeo && !hasIp) {
            return message;
        }
        StringBuilder ctx = new StringBuilder();
        ctx.append("\n\n[系统上下文-请勿直接复述]\n");
        if (hasGeo) {
            ctx.append("用户浏览器精确定位：纬度 ")
                    .append(latitude)
                    .append("，经度 ")
                    .append(longitude)
                    .append("。查询与用户所在地相关天气时，应调用 getWeatherByCoordinates(")
                    .append(latitude)
                    .append(", ")
                    .append(longitude)
                    .append(", true或false)。\n");
        }
        if (hasIp) {
            if (!hasGeo && browserGeolocationStatus != null
                    && browserGeolocationStatus.equalsIgnoreCase("denied")) {
                ctx.append("用户已拒绝浏览器定位权限，请使用 IP 粗定位作为降级。\n");
            }
            ctx.append("当前用户真实IP: ").append(clientIp).append("\n");
            if (hasGeo) {
                ctx.append("若坐标与公网 IP 明显不符（如 VPN），可结合 getIPLocation 与该 IP 再判断。\n");
            } else {
                ctx.append("如需定位，请调用 getIPLocation 并把该 IP 作为参数传入。\n");
            }
        }
        return message + ctx;
    }
}
