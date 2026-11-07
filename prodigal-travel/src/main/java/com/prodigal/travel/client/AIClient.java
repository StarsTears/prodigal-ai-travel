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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 备用（MiniMax）ChatClient。
 *
 * <p>说明：这个 Bean 使用进程内会话记忆，仅用于本地对比/调试，不承载线上主链路；
 * 线上问答统一走 {@link TravelAiClient}（MySQL 持久化记忆）。
 * MCP 客户端未启用时 {@code ToolCallbackProvider} 可能不存在，这里改为可选注入，
 * 避免「关闭 MCP 就启动失败」。
 */
@Configuration
public class AIClient {

    @Resource
    private ToolCallback[] allTools;

    @Resource
    private ObjectProvider<ToolCallbackProvider> toolCallbackProviders;

    @Bean
    ChatClient minimaxAiClient(MiniMaxChatModel chatModel) {
        InMemoryChatMemoryRepository repository = new InMemoryChatMemoryRepository();
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(TravelConstant.CHAT_MEMORY_MESSAGE_LIMIT)
                .build();

        ChatClient.Builder builder = ChatClient.builder(chatModel)
                .defaultSystem(TravelConstant.SYSTEM_PROMPT)
                .defaultAdvisors(new LoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build(),
                        new SystemMessageFirstAdvisor())
                .defaultToolCallbacks(allTools);
        ToolCallbackProvider provider = toolCallbackProviders == null
                ? null : toolCallbackProviders.getIfAvailable();
        if (provider != null) {
            builder.defaultToolCallbacks(provider);
        }
        return builder.build();
    }
}
