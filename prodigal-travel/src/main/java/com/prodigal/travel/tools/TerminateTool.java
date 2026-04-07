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
    @Tool(description = "Terminate the interaction when the request is met OR if the assistant cannot proceed further with the task." +
            "When you have finished all the tasks, call this tool to end the work.")
    public String doTerminate(String conversationId) {
        return "terminate the task!!";
    }
}
