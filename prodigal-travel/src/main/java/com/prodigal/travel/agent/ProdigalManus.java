package com.prodigal.travel.agent;

import com.prodigal.travel.advisor.LoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
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
    public ProdigalManus(ToolCallback[] availableTools, ChatModel dashScopeChatModel) {
        super(availableTools);
        this.setName("ProdigalManus");
        String SYSTEM_PROMPT = """
                You are ProdigalManus, an all-capable AI assistant, aimed at solving any task presented by the user. 
                You have various tools at your disposal that you can call upon to efficiently complete complex requests. 
                """;
        String NEXT_STEP_PROMPT = """
                Based on user needs, proactively select the most appropriate tool or combination of tools. 
                For complex tasks, you can break down the problem and use different tools step by step to solve it. 
                After using each tool, clearly explain the execution results and suggest the next steps.
                If you want to stop the interaction at any point, use the `terminate` tool/function call.
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
