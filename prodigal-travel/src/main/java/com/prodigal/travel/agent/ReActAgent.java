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
                return "Think complete!No need to act";
            }
            return this.act();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error running agent-Step executing failed: " + e.getMessage();
        }
    }

}
