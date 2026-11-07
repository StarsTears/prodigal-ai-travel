package com.prodigal.travel.agent;

/**
 * Agent 单步执行过程中产生的结构化事件。
 *
 * <p>旧实现把「模型正文」与「工具日志」拼成一个字符串，再用
 * {@code <<<MANUS_TOOL_LOG>>>} 分隔符在流式输出里拆分，语义脆弱且前端无法可靠区分。
 * 现在每一步都产出带类型的事件，由 {@code ManusAgentService} 映射为 SSE 命名事件：
 *
 * <ul>
 *     <li>{@code step}：THOUGHT / TOOL_CALL / TOOL_RESULT / NOTICE / ERROR</li>
 *     <li>{@code delta}：FINAL（最终答复，进入前端「综合回复」区）</li>
 *     <li>{@code done}：DONE（流结束标记）</li>
 * </ul>
 *
 * @param type     事件类型
 * @param step     所属步序号，0 表示不归属具体步骤
 * @param text     事件文本
 * @param toolName 关联的工具名，可为空
 */
public record AgentStepEvent(Type type, int step, String text, String toolName) {

    public enum Type {
        THOUGHT,
        TOOL_CALL,
        TOOL_RESULT,
        NOTICE,
        FINAL,
        ERROR,
        DONE
    }

    public static AgentStepEvent of(Type type, int step, String text) {
        return new AgentStepEvent(type, step, text == null ? "" : text, null);
    }

    public static AgentStepEvent of(Type type, int step, String text, String toolName) {
        return new AgentStepEvent(type, step, text == null ? "" : text, toolName);
    }

    public boolean isFinalAnswer() {
        return type == Type.FINAL && text != null && !text.isBlank();
    }
}
