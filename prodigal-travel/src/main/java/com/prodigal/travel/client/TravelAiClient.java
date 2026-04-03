package com.prodigal.travel.client;

import com.prodigal.travel.advisor.LoggerAdvisor;
import com.prodigal.travel.constants.TravelConstant;
import com.prodigal.travel.rag.QueryRewriter;
import com.prodigal.travel.rag.TravelRagCustomAdvisorFactory;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;


import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

/**
 * 旅游助手专用 {@link ChatClient}、向量库与本地工具注册。
 * <p>
 * MCP：在 {@code application.yml} 中设置 {@code spring.ai.mcp.client.enabled=true} 并配置 SSE 连接后，
 * Spring AI 会将 MCP 工具与下方 {@link ToolCallbackProvider} 一并交给模型调度（具体行为以当前版本自动配置为准）。
 */
@Slf4j
@Component
public class TravelAiClient {
    private final ChatClient chatClient;
    @Resource
    private VectorStore pgVectorStore;
    @Resource
    private QueryRewriter queryRewriter;
    @Resource
    private ToolCallback[] allTools;

    private final ChatModel dashscopeChatModel;

    public TravelAiClient(ChatModel dashscopeChatModel) {
        this.dashscopeChatModel = dashscopeChatModel;
        //1、基于内存的对话记忆
        ChatMemory chatMemory = new InMemoryChatMemory();
        chatClient = ChatClient.builder(dashscopeChatModel)
                .defaultSystem(TravelConstant.SYSTEM_PROMPT)
                .defaultAdvisors(new MessageChatMemoryAdvisor(chatMemory))
                .build();
        //
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
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId)
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 20))
                //开启日志
                .advisors(new LoggerAdvisor())
                .tools(allTools)
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
        //重写查询
        String rewriterMessage = queryRewriter.doRewriteQuery(message);

        //对向量数据库中的所有文档执行相似性搜索 该与 RetrievalAugmentationAdvisor 检索功能重叠
        //造成token 浪费
//        QuestionAnswerAdvisor questionAnswerAdvisor = QuestionAnswerAdvisor.builder(pgVectorStore)
//                .searchRequest(SearchRequest.builder().similarityThreshold(0.7d).topK(3).build())
//                .build();

        ChatResponse chatResponse = chatClient.prompt()
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId) //对话id
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10) //关联会话的条数
                )
                .user(rewriterMessage)
                //开启日志
                .advisors(new LoggerAdvisor())
                //基于 pgVector知识库回答
//                .advisors(questionAnswerAdvisor)
                //检索增强顾问
                .advisors(TravelRagCustomAdvisorFactory.createRetrievalAugmentationAdvisor(pgVectorStore,dashscopeChatModel))
                //添加工具
                .tools(allTools)
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
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId) //对话id
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10) //关联会话的条数
                )
                //开启日志
                .advisors(new LoggerAdvisor())
                .tools(allTools)
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
    public String doChatWithMCP(String message, String chatId) {
        ChatResponse chatResponse = chatClient.prompt()
                .user(message)
                .advisors(spec -> spec.param(CHAT_MEMORY_CONVERSATION_ID_KEY, chatId) //对话id
                        .param(CHAT_MEMORY_RETRIEVE_SIZE_KEY, 10) //关联会话的条数
                )
                //开启日志
                .advisors(new LoggerAdvisor())
                .tools(toolCallbackProvider)
                .call()
                .chatResponse();
        return chatResponse.getResult().getOutput().getText();
    }
}
