## Prodigal Manus 自助规划智能体

按 `think → act → observation` 循环执行，直到模型给出最终答复或触发退出条件。完整设计见
[docs/OPTIMIZATION.md](../../../../../../../docs/OPTIMIZATION.md)。

| 类 | 职责 |
|---|---|
| `BaseAgent` | 无状态循环骨架：上下文初始化、四重退出条件、异常收敛、循环结束钩子 |
| `ReActAgent` | 一步 = `think()` 决策 + `act()` 执行 |
| `ToolCallAgent` | 手动编排工具调用：工具定义注入、重复调用拦截、doTerminate、兜底总结 |
| `ProdigalManus` | 旅游场景提示词、工具集与运行参数组装（每次请求一个轻量实例） |
| `AgentRunContext` | 单次运行的全部可变状态，保证并发/多轮之间完全隔离 |
| `AgentStepEvent` | 结构化步骤事件，由 `ManusAgentService` 映射为 SSE 命名事件 |
| `ManusAgentService` | 会话记忆读写、专用线程池调度、SSE 输出与断连取消 |

要点：

- **状态外置**：Agent 实例不保存请求级字段，不再有单例串台或「跑完一次就锁死」的问题。
- **手动控制工具执行**：`ChatModel` 只负责产出 `ToolCall`，工具由 `ToolCallingManager` 在本层执行，
  以便把每次调用与结果作为 `step` 事件实时推给前端。
- **上下文**：系统提示词只注入一次，历史消息由 `MySQLChatMemory` 提供，工具响应按类型写回。
- **退出条件**：正常完成 / 最大步数 / 时间预算 / 客户端取消；任何情况下都会整理出一段最终答复。
- **SSE 协议**：`event: step`（思考与工具日志）、`event: delta`（最终答复）、`event: done`（结束），
  data 统一为 JSON，前端按事件名分流。
