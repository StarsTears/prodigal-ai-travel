package com.prodigal.travel.agent;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description: 实现思考-执行的步骤
 * @since 2026/4/6
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public abstract class ReActAgent extends BaseAgent {

    /**
     * 思考 决定是否需要执行下一步做
     *
     * @return true 需要执行 false 不需要
     */
    public abstract boolean think();

    /**
     * 执行
     *
     * @return
     */
    public abstract String act();

    @Override
    public String step() {
        try {
            boolean shouldAct = this.think();
            if (!shouldAct) {
                return noActionStepResult();
            }
            return this.act();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error running agent-Step executing failed: " + e.getMessage();
        }
    }

    /**
     * {@link #think()} 返回 false 时作为本步对外结果（含流式 SSE）。
     * 子类可在「模型已给出纯文本答复」时覆盖为实际正文。
     */
    protected String noActionStepResult() {
        return "Think complete!No need to act";
    }

}
