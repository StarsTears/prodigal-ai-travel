package com.prodigal.travel.agent;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 智能体状态枚举
 * @since 2026/4/6
 */
public enum AgentState {
    /**
     * 空闲
     */
    IDLE,
    /**
     * 运行中
     */
    RUNNING,
    /**
     * 已完成
     */
    FINISHED,
    /**
     * 错误
     */
    ERROR;
}
