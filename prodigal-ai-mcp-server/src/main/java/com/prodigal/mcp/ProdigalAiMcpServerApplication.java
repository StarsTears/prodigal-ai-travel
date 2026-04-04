package com.prodigal.mcp;

import com.prodigal.mcp.tools.ImageSearchTool;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ProdigalAiMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProdigalAiMcpServerApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider toolCallbackProvider(ImageSearchTool imageSearchTool) {

        return MethodToolCallbackProvider.builder().toolObjects( imageSearchTool).build();
    }
}
