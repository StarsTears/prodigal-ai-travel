package com.prodigal.travel.agent;

/**
 * Manus SSE 事件名常量。
 *
 * <p>旧实现用字符串分隔符 {@code <<<MANUS_TOOL_LOG>>>} 在同一条 SSE 消息里拼接
 * 「模型正文 + 工具日志」，前端只能靠文本前缀猜测内容类型（工具结果恰好叫
 * {@code tool name:} 时才生效），模型正文里出现分隔符还会被误拆。
 * 现在改为 SSE 命名事件，语义由事件名承载：
 *
 * <ul>
 *     <li>{@link #STEP}：思考 / 工具调用 / 观察 / 提示，进入前端「思考与执行过程」</li>
 *     <li>{@link #DELTA}：最终答复，进入前端「综合回复」</li>
 *     <li>{@link #DONE}：结束标记（data 为 [DONE]，前端忽略）</li>
 * </ul>
 */
public final class ManusStreamMarkers {

    private ManusStreamMarkers() {}

    public static final String STEP = "step";
    public static final String DELTA = "delta";
    public static final String DONE = "done";

    /** @deprecated 仅为兼容旧前端/日志保留，不再参与流式拆分 */
    @Deprecated
    public static final String TOOL_LOG_SEPARATOR = "<<<MANUS_TOOL_LOG>>>";
}
