package com.prodigal.travel.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Agent Loop 纯逻辑测试：不启动 Spring 容器、不需要模型 API Key。
 * 覆盖旧实现最容易出错的三处：正常结束被误判为超步数、状态无法复位、重复工具调用。
 */
class ProdigalManusTest {

    /** 无状态 stub：步数完全取自每次运行独立的 Context */
    private static class StubAgent extends BaseAgent {
        private final int finishAt;

        private StubAgent(int finishAt) {
            this.finishAt = finishAt;
            setName("stub");
            setMaxStep(20);
        }

        @Override
        public void step(AgentRunContext ctx, Consumer<AgentStepEvent> sink) {
            int step = ctx.getCurrentStep();
            if (finishAt > 0 && step == finishAt) {
                ctx.setFinalAnswer("最终答复");
                ctx.setState(AgentState.FINISHED);
                sink.accept(AgentStepEvent.of(AgentStepEvent.Type.FINAL, step, "最终答复"));
            }
        }
    }

    @Test
    void stopsImmediatelyWhenAgentFinished() {
        StubAgent agent = new StubAgent(2);
        List<AgentStepEvent> events = new ArrayList<>();
        AgentRunContext ctx = agent.run(List.<Message>of(), "帮我规划行程", events::add);

        assertEquals(2, ctx.getCurrentStep());
        assertEquals(AgentState.FINISHED, ctx.getState());
        assertEquals("最终答复", ctx.getFinalAnswer());
        assertFalse(events.stream().anyMatch(e -> e.text().contains("最大执行步数")),
                "正常结束不应再输出达到最大步数的提示");
    }

    @Test
    void stopsAtMaxStepWithoutSpinningForever() {
        StubAgent agent = new StubAgent(-1);
        agent.setMaxStep(3);
        List<AgentStepEvent> events = new ArrayList<>();
        AgentRunContext ctx = agent.run(List.<Message>of(), "复杂任务", events::add);

        assertEquals(3, ctx.getCurrentStep());
        assertEquals(AgentState.FINISHED, ctx.getState());
        assertTrue(events.stream().anyMatch(e -> e.text().contains("最大执行步数")));
    }

    @Test
    void contextIsIsolatedPerRun() {
        // 复用同一个 Agent 实例两次，验证状态不会跨运行残留
        StubAgent agent = new StubAgent(1);
        AgentRunContext first = agent.run(List.<Message>of(), "第一个问题", event -> {});
        AgentRunContext second = agent.run(List.<Message>of(), "第二个问题", event -> {});

        assertEquals(1, first.getCurrentStep());
        assertEquals(1, second.getCurrentStep(), "每次运行都应从第 1 步开始，不受上次运行影响");
        assertEquals(AgentState.FINISHED, second.getState());
        assertEquals("最终答复", second.getFinalAnswer());
    }

    @Test
    void detectsRepeatedToolCall() {
        AgentRunContext ctx = new AgentRunContext(5, 60_000);
        assertFalse(ctx.isRepeatedToolCall("searchImage|黄山"));
        assertTrue(ctx.isRepeatedToolCall("searchImage|黄山"));
        assertFalse(ctx.isRepeatedToolCall("searchImage|西湖"));
    }
}
