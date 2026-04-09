package com.prodigal.travel.agent;

/**
 * 超级智能体 SSE：一步内既有模型可见回复又有工具日志时，用该分隔符拼接；
 * {@link BaseAgent#runStream} 会拆成两次 {@code send}，便于前端分别路由到「综合回复」与「思考与执行过程」。
 */
public final class ManusStreamMarkers {

    private ManusStreamMarkers() {}

    public static final String TOOL_LOG_SEPARATOR = "<<<MANUS_TOOL_LOG>>>";
}
