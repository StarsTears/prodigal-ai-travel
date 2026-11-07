package com.prodigal.travel.agent;

import cn.hutool.core.util.StrUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 抽象基础代理：只保存无状态配置与依赖，负责 Agent Loop 的骨架。
 *
 * <p>相比旧版的关键修正：
 * <ol>
 *     <li>所有可变状态移到 {@link AgentRunContext}，Agent 实例可复用，不再有单例串台问题；</li>
 *     <li>{@code nextStepPrompt} 只在系统消息里出现一次，不再每步重复追加 UserMessage；</li>
 *     <li>正常结束（模型给出正文 / 调用终止工具）不会再被误报为「达到最大步骤」；</li>
 *     <li>支持步数、墙钟时间、连续空转、客户端取消四重退出条件；</li>
 *     <li>循环结束仍没有最终答复时，由子类钩子生成兜底总结，保证用户一定拿到答案。</li>
 * </ol>
 */
@Slf4j
@Data
public abstract class BaseAgent {

    private String name;

    private String systemPrompt;

    /** 每轮的行动准则，合并进系统提示词，只注入一次 */
    private String nextStepPrompt;

    private int maxStep = 10;

    private long maxRunMillis = 180_000L;

    private ChatModel chatModel;

    /**
     * 构造一次独立运行的上下文。消息顺序固定为：系统消息（含本轮准则）→ 历史会话 → 本次用户输入。
     *
     * @param history    由会话记忆载入的历史消息
     * @param userPrompt 本次用户输入
     */
    public AgentRunContext createContext(List<Message> history, String userPrompt) {
        AgentRunContext ctx = new AgentRunContext(maxStep, maxRunMillis);
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(mergeSystemPrompt()));
        if (history != null) {
            for (Message message : history) {
                if (message == null || StrUtil.isBlank(message.getText())) {
                    continue;
                }
                MessageType type = message.getMessageType();
                // 只回放 user/assistant，避免历史里的 system 出现在中间，或 tool 消息破坏顺序
                if (type == MessageType.USER || type == MessageType.ASSISTANT) {
                    messages.add(message);
                }
            }
        }
        messages.add(new UserMessage(userPrompt));
        ctx.setMessages(messages);
        return ctx;
    }

    private String mergeSystemPrompt() {
        StringBuilder sb = new StringBuilder(systemPrompt == null ? "" : systemPrompt);
        if (StrUtil.isNotBlank(nextStepPrompt)) {
            sb.append("\n\n## 行动准则\n").append(nextStepPrompt);
        }
        return sb.toString();
    }

    /**
     * 运行一次完整的 Agent Loop（同步，由调用方放到专用线程池）。
     *
     * @param history    会话历史
     * @param userPrompt 用户输入
     * @param sink       结构化事件消费者（通常是 SSE 发送器）
     * @return 本次运行上下文，含最终答复
     */
    public AgentRunContext run(List<Message> history, String userPrompt, Consumer<AgentStepEvent> sink) {
        if (StrUtil.isBlank(userPrompt)) {
            throw new IllegalArgumentException("Agent 用户输入不能为空");
        }
        return run(createContext(history, userPrompt), sink);
    }

    /**
     * 使用外部已创建的上下文运行（调用方可持有 ctx 以便在 SSE 超时/断开时设置 cancelled）。
     */
    public AgentRunContext run(AgentRunContext ctx, Consumer<AgentStepEvent> sink) {
        ctx.setState(AgentState.RUNNING);
        try {
            while (!ctx.shouldStop()) {
                int step = ctx.nextStep();
                log.info("[{}] step {}/{}", name, step, maxStep);
                step(ctx, sink);
            }
            if (!ctx.isCancelled()) {
                if (ctx.isStepBudgetExhausted() && ctx.getState() == AgentState.RUNNING) {
                    sink.accept(AgentStepEvent.of(AgentStepEvent.Type.NOTICE, ctx.getCurrentStep(),
                            "已达到最大执行步数（" + maxStep + "），停止继续调用工具。"));
                } else if (ctx.isTimeBudgetExhausted() && ctx.getState() == AgentState.RUNNING) {
                    sink.accept(AgentStepEvent.of(AgentStepEvent.Type.NOTICE, ctx.getCurrentStep(),
                            "已达到本次执行时间上限，停止继续调用工具。"));
                }
                onLoopEnd(ctx, sink);
            }
            if (ctx.getState() == AgentState.RUNNING) {
                ctx.setState(AgentState.FINISHED);
            }
        } catch (Exception e) {
            log.error("[{}] agent 执行异常", name, e);
            ctx.setState(AgentState.ERROR);
            sink.accept(AgentStepEvent.of(AgentStepEvent.Type.ERROR, ctx.getCurrentStep(),
                    "执行错误：" + rootMessage(e)));
            if (StrUtil.isBlank(ctx.getFinalAnswer())) {
                onLoopEnd(ctx, sink);
            }
        }
        return ctx;
    }

    /** 单个步骤：由 ReActAgent 拆成 think + act，并通过 sink 发出多个事件 */
    public abstract void step(AgentRunContext ctx, Consumer<AgentStepEvent> sink);

    /** 循环结束钩子：默认不做处理，子类可生成兜底最终答复 */
    protected void onLoopEnd(AgentRunContext ctx, Consumer<AgentStepEvent> sink) {
        // no-op
    }

    protected static String rootMessage(Throwable e) {
        Throwable cur = e;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return StrUtil.isBlank(msg) ? cur.getClass().getSimpleName() : msg;
    }

    /** 截断过长的工具结果，避免 SSE 帧和日志失控 */
    protected static String abbreviate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...（已截断）";
    }
}
