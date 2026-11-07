package com.prodigal.travel.agent;

import cn.hutool.core.util.StrUtil;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionResult;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 手动编排工具调用的 ReAct 代理。
 *
 * <p>相比旧版的关键修正：
 * <ol>
 *     <li>直接调用 {@code ChatModel} 而非套一层带 {@code MessageChatMemoryAdvisor} 的
 *         {@code ChatClient}，避免「手工 messageList」与「Advisor 注入历史」两份上下文互相叠加；</li>
 *     <li>工具定义显式写入 {@code DashScopeChatOptions.toolCallbacks}，
 *         {@code ToolCallingManager} 才能解析并执行本地工具 + MCP 工具（旧版 act() 重新 new Prompt 时丢了工具定义）；</li>
 *     <li>think() 异常不再被吞掉后空转；无工具调用且无正文时按「空转次数」退出并生成兜底答复；</li>
 *     <li>拦截完全相同的连续工具调用；doTerminate 后仍会生成最终答复；</li>
 *     <li>从 conversationHistory 中按类型查找 ToolResponseMessage，不再对最后一个元素强转；</li>
 *     <li>所有状态放在 {@link AgentRunContext}，本类无请求级可变字段。</li>
 * </ol>
 */
@Slf4j
@Data
@EqualsAndHashCode(callSuper = true)
public class ToolCallAgent extends ReActAgent {

    private static final int TOOL_LOG_MAX_LENGTH = 800;

    private ToolCallback[] availableTools = new ToolCallback[0];

    private ToolCallbackProvider toolCallbackProvider;

    /** 本地工具 + MCP 工具合并后的完整列表 */
    private final List<ToolCallback> allCallbacks = new ArrayList<>();

    private final ToolCallingManager toolCallingManager = ToolCallingManager.builder().build();

    public ToolCallAgent() {
        // 由 Spring/工厂通过 setter 注入依赖，避免构造期顺序耦合
    }

    public void setAvailableTools(ToolCallback[] availableTools) {
        this.availableTools = availableTools == null ? new ToolCallback[0] : availableTools;
        rebuildCallbacks();
    }

    public void setToolCallbackProvider(ToolCallbackProvider toolCallbackProvider) {
        this.toolCallbackProvider = toolCallbackProvider;
        rebuildCallbacks();
    }

    private void rebuildCallbacks() {
        allCallbacks.clear();
        allCallbacks.addAll(Arrays.asList(availableTools));
        if (toolCallbackProvider != null && toolCallbackProvider.getToolCallbacks() != null) {
            allCallbacks.addAll(Arrays.asList(toolCallbackProvider.getToolCallbacks()));
        }
        log.debug("Agent 工具数：{}（本地 {} / MCP {}）", allCallbacks.size(),
                availableTools.length,
                toolCallbackProvider == null || toolCallbackProvider.getToolCallbacks() == null
                        ? 0 : toolCallbackProvider.getToolCallbacks().length);
    }

    /**
     * DashScope 的模型路由参数沿用旧实现；internalToolExecutionEnabled=false 表示工具由本类手动执行。
     */
    private DashScopeChatOptions buildOptions(boolean withTools) {
        DashScopeChatOptions options = DashScopeChatOptions.builder()
                .internalToolExecutionEnabled(false)
                .enableThinking(true)
                .multiModel(true)
                .build();
        if (withTools) {
            options.setToolCallbacks(new ArrayList<>(allCallbacks));
        }
        return options;
    }

    @Override
    public boolean think(AgentRunContext ctx, Consumer<AgentStepEvent> sink) {
        Prompt prompt = new Prompt(ctx.getMessages(), buildOptions(!ctx.isAnswerOnly()));
        ChatResponse response = getChatModel().call(prompt);
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            ctx.recordNoProgress();
            sink.accept(AgentStepEvent.of(AgentStepEvent.Type.NOTICE, ctx.getCurrentStep(),
                    "模型未返回有效响应，重试一次。"));
            return false;
        }
        AssistantMessage assistant = response.getResult().getOutput();
        List<AssistantMessage.ToolCall> toolCalls = assistant.getToolCalls();

        if (toolCalls == null || toolCalls.isEmpty()) {
            String text = assistant.getText();
            if (StrUtil.isBlank(text)) {
                int empty = ctx.recordNoProgress();
                sink.accept(AgentStepEvent.of(AgentStepEvent.Type.NOTICE, ctx.getCurrentStep(),
                        "模型本轮没有给出内容，也没有调用工具。"));
                if (empty >= 2) {
                    ctx.setState(AgentState.FINISHED);
                }
                return false;
            }
            ctx.getMessages().add(assistant);
            ctx.setFinalAnswer(text);
            ctx.setState(AgentState.FINISHED);
            sink.accept(AgentStepEvent.of(AgentStepEvent.Type.FINAL, ctx.getCurrentStep(), text));
            return false;
        }

        ctx.recordProgress();
        ctx.setPendingResponse(response);
        if (StrUtil.isNotBlank(assistant.getText())) {
            sink.accept(AgentStepEvent.of(AgentStepEvent.Type.THOUGHT, ctx.getCurrentStep(),
                    abbreviate(assistant.getText(), TOOL_LOG_MAX_LENGTH)));
        }
        for (AssistantMessage.ToolCall call : toolCalls) {
            log.info("[{}] think: 调用工具 {} 参数 {}", getName(), call.name(), call.arguments());
            sink.accept(AgentStepEvent.of(AgentStepEvent.Type.TOOL_CALL, ctx.getCurrentStep(),
                    "tool name: " + call.name() + " , tool arguments: " + abbreviate(call.arguments(), TOOL_LOG_MAX_LENGTH),
                    call.name()));
        }
        return true;
    }

    @Override
    public void act(AgentRunContext ctx, Consumer<AgentStepEvent> sink) {
        ChatResponse response = ctx.getPendingResponse();
        ctx.setPendingResponse(null);
        if (response == null || !response.hasToolCalls()) {
            return;
        }
        AssistantMessage assistant = response.getResult().getOutput();
        List<AssistantMessage.ToolCall> toolCalls = assistant.getToolCalls();

        String signature = toolCalls.stream()
                .map(call -> call.name() + "|" + call.arguments())
                .collect(Collectors.joining("||"));
        if (ctx.isRepeatedToolCall(signature)) {
            // 拦截重复调用：仍要补一条合法的 ToolResponseMessage，保持 assistant(toolCalls) 与工具响应配对
            List<Message> skipped = new ArrayList<>(ctx.getMessages());
            skipped.add(assistant);
            List<ToolResponseMessage.ToolResponse> replacements = toolCalls.stream()
                    .map(call -> new ToolResponseMessage.ToolResponse(call.id(), call.name(),
                            "检测到与上一轮完全相同的工具调用，已跳过执行。请直接基于已有结果作答，不要重复调用该工具。"))
                    .toList();
            skipped.add(ToolResponseMessage.builder().responses(replacements).build());
            ctx.setMessages(skipped);
            ctx.setAnswerOnly(true);
            sink.accept(AgentStepEvent.of(AgentStepEvent.Type.NOTICE, ctx.getCurrentStep(),
                    "检测到重复工具调用，已跳过执行，转为生成最终答复。"));
            return;
        }

        ToolExecutionResult result = toolCallingManager.executeToolCalls(
                new Prompt(ctx.getMessages(), buildOptions(true)), response);
        List<Message> history = result.conversationHistory() == null
                ? Collections.emptyList() : result.conversationHistory();
        ctx.setMessages(new ArrayList<>(history));

        ToolResponseMessage toolResponseMessage = findLastToolResponse(history);
        if (toolResponseMessage == null) {
            sink.accept(AgentStepEvent.of(AgentStepEvent.Type.NOTICE, ctx.getCurrentStep(),
                    "工具执行未返回结果。"));
            return;
        }
        for (ToolResponseMessage.ToolResponse res : toolResponseMessage.getResponses()) {
            String line = "tool name: " + res.name() + " , tool response: "
                    + abbreviate(res.responseData(), TOOL_LOG_MAX_LENGTH);
            log.info("[{}] act: {}", getName(), line);
            sink.accept(AgentStepEvent.of(AgentStepEvent.Type.TOOL_RESULT, ctx.getCurrentStep(), line, res.name()));
        }
        if (toolResponseMessage.getResponses().stream()
                .anyMatch(res -> res.name() != null && res.name().endsWith("doTerminate"))) {
            ctx.setTerminatedByTool(true);
            ctx.setState(AgentState.FINISHED);
            sink.accept(AgentStepEvent.of(AgentStepEvent.Type.NOTICE, ctx.getCurrentStep(),
                    "任务已标记完成，正在整理最终答复。"));
        }
    }

    private static ToolResponseMessage findLastToolResponse(List<Message> history) {
        for (int i = history.size() - 1; i >= 0; i--) {
            if (history.get(i) instanceof ToolResponseMessage toolResponseMessage) {
                return toolResponseMessage;
            }
        }
        return null;
    }

    /**
     * 循环结束但没有正文时（达到步数/时间上限、异常、doTerminate、连续空转），
     * 用不带工具的一次调用整理出最终答复，避免用户只看到一堆步骤日志。
     */
    @Override
    protected void onLoopEnd(AgentRunContext ctx, Consumer<AgentStepEvent> sink) {
        if (StrUtil.isNotBlank(ctx.getFinalAnswer()) || ctx.isCancelled()) {
            return;
        }
        try {
            List<Message> messages = new ArrayList<>(ctx.getMessages());
            messages.add(new UserMessage(
                    "请基于以上对话和工具结果，用简体中文直接给出对用户的最终答复，不要再调用任何工具，也不要复述内部过程。"));
            ChatResponse response = getChatModel().call(new Prompt(messages, buildOptions(false)));
            String text = response == null || response.getResult() == null
                    || response.getResult().getOutput() == null
                    ? null : response.getResult().getOutput().getText();
            if (StrUtil.isBlank(text)) {
                text = "抱歉，本次任务未能在限定步数内整理出完整答复，请缩小问题范围后再试一次。";
            }
            ctx.setFinalAnswer(text);
            sink.accept(AgentStepEvent.of(AgentStepEvent.Type.FINAL, ctx.getCurrentStep(), text));
        } catch (Exception e) {
            log.error("[{}] 生成兜底答复失败", getName(), e);
            String fallback = "抱歉，执行过程中出现异常：" + rootMessage(e);
            ctx.setFinalAnswer(fallback);
            sink.accept(AgentStepEvent.of(AgentStepEvent.Type.ERROR, ctx.getCurrentStep(), fallback));
        }
    }
}
