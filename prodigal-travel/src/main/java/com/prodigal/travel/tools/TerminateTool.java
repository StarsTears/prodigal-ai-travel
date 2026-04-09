package com.prodigal.travel.tools;

import org.springframework.ai.tool.annotation.Tool;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 终止工具
 * @since 2026/4/6
 */
public class TerminateTool {
    @Tool(description = "End the agent turn when the user's request is fully satisfied (including after a single tool already returned the answer), " +
            "or when you cannot proceed. Prefer answering in plain text without tools when possible; use this tool to stop the step loop after you are done.")
    public String doTerminate(String conversationId) {
        return "terminate the task!!";
    }
}
