package com.prodigal.travel.agent;

import com.prodigal.travel.advisor.LoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.stereotype.Component;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 智能体
 * @since 2026/4/7
 */
@Component
public class ProdigalManus extends ToolCallAgent{
    public ProdigalManus(ToolCallback[] availableTools, ToolCallbackProvider toolCallbackProvider,
                         ChatModel dashScopeChatModel) {
        super(availableTools, toolCallbackProvider);
        this.setName("ProdigalManus");
        String SYSTEM_PROMPT = """
                You are ProdigalManus, an all-capable AI assistant, aimed at solving any task presented by the user.
                You have various tools at your disposal that you can call upon to efficiently complete complex requests.
                Use as few tool calls as possible. Never call the same tool again for the same user intent when the last tool output already contains the answer.
                After tools return enough information, answer the user in plain text without calling more tools unless something is still missing.
                """;
        String NEXT_STEP_PROMPT = """
                Look at the conversation and the latest tool results (if any).
                If the user's question is already fully answered, respond with a concise final answer in normal assistant text only — do not call any tools.
                Only call a tool when additional information is genuinely required. Never repeat the same tool call for the same purpose.
                For multi-step tasks, use tools step by step; after each tool, decide whether another tool is needed or you should answer in text.
                To explicitly end after completing work, you may call the `doTerminate` tool.
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxStep(15);

        //基于内存的会话记忆
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
        //初始化 AI 智能体 客户端
        ChatClient chatClient = ChatClient.builder(dashScopeChatModel)
                //使用内存会话记忆
                .defaultAdvisors(new LoggerAdvisor())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
        this.setChatClient(chatClient);
    }
}
