package com.prodigal.travel.agent;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

/**
 * ReAct 骨架：一步 = think（决定是否需要工具）+ act（执行工具并观察结果）。
 *
 * <p>旧实现中 think 抛异常会被 {@code step()} 吞掉并返回一句占位文本，
 * 既不改变状态也不退出循环，导致空转到 maxStep。现在异常统一由
 * {@link BaseAgent#run} 捕获并转成 ERROR 事件；think/act 直接产出结构化事件。
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public abstract class ReActAgent extends BaseAgent {

    /**
     * 思考：调用模型，判断本轮是「给出最终答复」还是「需要调用工具」。
     *
     * @return true 表示需要执行 act()
     */
    public abstract boolean think(AgentRunContext ctx, Consumer<AgentStepEvent> sink);

    /** 执行工具并把观察结果写回上下文 */
    public abstract void act(AgentRunContext ctx, Consumer<AgentStepEvent> sink);

    @Override
    public void step(AgentRunContext ctx, Consumer<AgentStepEvent> sink) {
        boolean needAct = think(ctx, sink);
        if (needAct && ctx.getState() == AgentState.RUNNING && !ctx.isCancelled()) {
            act(ctx, sink);
        }
    }
}
