package com.prodigal.travel.agent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description: 处理工具调用的基础代理，具体实现 think、act
 * @since 2026/4/6
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
public class ToolCallAgent extends ReActAgent {
    //可用工具
    private ToolCallback[] availableTools;
    //MCP 可用工具
    private ToolCallbackProvider toolCallbackProvider;

    //工具调用的响应结果
    private ChatResponse toolChatResponse;

    //工具调用管理者
    private final ToolCallingManager toolCallingManager;

    private final ChatOptions chatOptions;


    public ToolCallAgent(ToolCallback[] availableTools) {
        this(availableTools, null);
    }

    public ToolCallAgent(ToolCallback[] availableTools, ToolCallbackProvider toolCallbackProvider) {
        super();
        this.availableTools = availableTools;
        this.toolCallbackProvider = toolCallbackProvider;
        this.toolCallingManager = ToolCallingManager.builder().build();
        // DashScope：部分模型要求 enable_thinking 必须为 true；仅用 DefaultToolCallingChatOptions 时请求体不带该字段会 400。
        // 与工具调用合并时 multiModel 曾被错误覆盖为 false，会走错 HTTP 端点并返回 InvalidParameter「url error」
        // （见 spring-ai-alibaba #4408 / #4375）。显式开启多模态路由以匹配 qwen3.x 等模型的统一接入。
        this.chatOptions = DashScopeChatOptions.builder()
//                .withProxyToolCalls(true)
                .internalToolExecutionEnabled(false)
                .enableThinking(true)
                .multiModel(true)
                .build();
    }

    @Override
    public boolean think() {
        //校验提示词
        if (StrUtil.isNotBlank(getNextStepPrompt())){
            UserMessage userMessage = new UserMessage(getNextStepPrompt());
            getMessageList().add(userMessage);
        }
        //调用 AI 大模型，获取工具调用结果
        List<Message> messageList = getMessageList();
        Prompt prompt = new Prompt(messageList, this.chatOptions);
        try {
            ChatClient.ChatClientRequestSpec promptCall = getChatClient().prompt(prompt)
                    .system(getSystemPrompt())
                    .toolCallbacks(availableTools);
            if (toolCallbackProvider != null) {
                promptCall = promptCall.toolCallbacks(toolCallbackProvider);
            }
            ChatResponse chatResponse = promptCall.call().chatResponse();
            //记录响应
            this.toolChatResponse = chatResponse;
            //获取所需的工具
            AssistantMessage assistantMessage = chatResponse.getResult().getOutput();
            List<AssistantMessage.ToolCall> toolCalls = assistantMessage.getToolCalls();
            //输出提示信息
            String result = assistantMessage.getText();
            log.info("{} think: {} select {} tools", getName(), result,toolCalls.size());
            String toolStr = toolCalls.stream().map(tool -> String.format("tool name: %s , tool arguments: %s", tool.name(), tool.arguments()))
                    .collect(Collectors.joining("\n"));
            log.info(toolStr);
            if (toolCalls.isEmpty()){
                //不调用工具时，无需记录助手消息；<调用工具会自动记录>
                getMessageList().add(assistantMessage);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("{} was a problem during the thinking process. {}",getName(),e.getMessage());
            getMessageList().add(new AssistantMessage(String.format("An error occurred during processing",e.getMessage())));
            return false;
        }
    }

    @Override
    public String act() {
        if (!toolChatResponse.hasToolCalls()){
            return "No tools need to be called.";
        }
        Prompt prompt = new Prompt(getMessageList(), this.chatOptions);
        //调用工具
        ToolExecutionResult toolExecutionResult = toolCallingManager.executeToolCalls(prompt, toolChatResponse);
        //记录消息上下文
        this.setMessageList(toolExecutionResult.conversationHistory());
        ToolResponseMessage toolResponseMessage = (ToolResponseMessage) CollUtil.getLast(toolExecutionResult.conversationHistory());
        String result = toolResponseMessage.getResponses().stream().map(
                res ->
                        String.format("tool name: %s , tool response: %s", res.name(), res.responseData())
        ).collect(Collectors.joining("\n"));

        //判断是否调用终止工具
        boolean isTerminated = toolResponseMessage.getResponses().stream().anyMatch(res -> "doTerminate".equals(res.name()));
        if (isTerminated){
            log.info("{} was terminated.",getName());
            this.setState(AgentState.FINISHED);
        }

        log.info("{} act: {}",getName(),result);
        return result;
    }
}
