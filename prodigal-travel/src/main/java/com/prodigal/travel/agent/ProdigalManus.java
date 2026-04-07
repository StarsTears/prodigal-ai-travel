package com.prodigal.travel.agent;

import com.prodigal.travel.advisor.LoggerAdvisor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.stereotype.Component;

import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY;
import static org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor.CHAT_MEMORY_RETRIEVE_SIZE_KEY;

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
                """;
        this.setSystemPrompt(SYSTEM_PROMPT);
        this.setNextStepPrompt(NEXT_STEP_PROMPT);
        this.setMaxStep(15);

        //初始化 AI 智能体 客户端
        ChatClient chatClient = ChatClient.builder(dashScopeChatModel)
                .defaultAdvisors(new LoggerAdvisor())
                .build();
        this.setChatClient(chatClient);
    }
}
