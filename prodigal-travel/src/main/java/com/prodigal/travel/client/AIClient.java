package com.prodigal.travel.client;

import com.prodigal.travel.advisor.LoggerAdvisor;
import com.prodigal.travel.advisor.SystemMessageFirstAdvisor;
import com.prodigal.travel.constants.TravelConstant;
import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.minimax.MiniMaxChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 *
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @since 2026/4/9
 */
@Configuration
public class AIClient {
    @Resource
    private ToolCallback[] allTools;
    @Resource
    private ToolCallbackProvider toolCallbackProvider;

    @Bean
    ChatClient minimaxAiClient(MiniMaxChatModel chatModel) {
        // 基于内存会话记忆
        InMemoryChatMemoryRepository inMemoryChatMemoryRepository = new InMemoryChatMemoryRepository();
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory
                .builder()
                .chatMemoryRepository(inMemoryChatMemoryRepository)
                .build();
        MessageChatMemoryAdvisor messageChatMemoryAdvisor = MessageChatMemoryAdvisor.builder(chatMemory).build();
        return ChatClient.builder(chatModel)
                .defaultSystem(TravelConstant.SYSTEM_PROMPT)
                .defaultAdvisors(new LoggerAdvisor(), messageChatMemoryAdvisor, new SystemMessageFirstAdvisor())
                .defaultToolCallbacks(allTools)
                .defaultToolCallbacks(toolCallbackProvider)
                .build();
    }

//    @Bean
//    ChatClient ollamaAiClient(OllamaChatModel chatModel, MySQLChatMemory chatMemory) {
//        return ChatClient.builder(chatModel)
//                .defaultSystem(TravelConstant.SYSTEM_PROMPT)
//                .defaultAdvisors(new LoggerAdvisor())
////                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
//                .defaultToolCallbacks(allTools)
//                .defaultToolCallbacks(toolCallbackProvider)
//                .build();
//    }

}
