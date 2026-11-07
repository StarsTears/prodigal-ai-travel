package com.prodigal.travel.agent;

import lombok.Data;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * 单次 Agent 运行的全部可变状态。
 *
 * <p>旧实现把 state / messageList / currentStep / toolChatResponse 放在 Agent 对象本身上，
 * 而 Agent 又是 Spring 单例（或每次 new 但共享内存记忆），导致：
 * 多用户上下文串台、第一次运行后 state 停留在 FINISHED 再也跑不动、历史消息无限膨胀。
 *
 * <p>现在每次请求新建一个 Context，Agent 对象只保存无状态配置与依赖，可安全复用。
 */
@Data
public class AgentRunContext {

    private AgentState state = AgentState.IDLE;

    private int currentStep = 0;

    private final int maxStep;

    private final long startedAt = System.currentTimeMillis();

    /** 单次运行的墙钟预算，避免模型/工具卡死把 SSE 连接一直挂住 */
    private final long maxRunMillis;

    /** 完整对话上下文：第一条恒为 SystemMessage，其后为 user/assistant/tool 消息 */
    private List<Message> messages = new ArrayList<>();

    /** 最近一次带工具调用的响应，供 act() 手动执行工具时使用 */
    private ChatResponse pendingResponse;

    /** 最终答复；只有它才会进入前端「综合回复」并写入会话记忆 */
    private String finalAnswer;

    /** 连续「模型既不给正文也不调工具」的次数，用于打破空转 */
    private int noProgressSteps = 0;

    /** 上一轮工具调用签名，用于拦截完全相同的重复调用 */
    private String lastToolSignature;

    /** 置为 true 后下一轮不再向模型暴露工具，强制其输出最终答复 */
    private boolean answerOnly = false;

    /** 是否因调用 doTerminate 结束 */
    private boolean terminatedByTool = false;

    /** 客户端断开 / SSE 超时时置位，循环会尽快退出 */
    private volatile boolean cancelled = false;

    public AgentRunContext(int maxStep, long maxRunMillis) {
        this.maxStep = maxStep;
        this.maxRunMillis = maxRunMillis;
    }

    public int nextStep() {
        return ++currentStep;
    }

    public boolean isStepBudgetExhausted() {
        return currentStep >= maxStep;
    }

    public boolean isTimeBudgetExhausted() {
        return System.currentTimeMillis() - startedAt >= maxRunMillis;
    }

    public boolean shouldStop() {
        return cancelled || state != AgentState.RUNNING
                || isStepBudgetExhausted() || isTimeBudgetExhausted();
    }

    public void recordProgress() {
        noProgressSteps = 0;
    }

    public int recordNoProgress() {
        return ++noProgressSteps;
    }

    /**
     * @return true 表示与上一轮工具调用完全相同，应跳过执行
     */
    public boolean isRepeatedToolCall(String signature) {
        boolean repeated = signature != null && signature.equals(lastToolSignature);
        lastToolSignature = signature;
        return repeated;
    }
}
