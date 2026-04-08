package com.prodigal.travel.tools;

import com.prodigal.travel.config.MailConfig;
import jakarta.annotation.Resource;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 工具注册类
 * @since 2026/4/2
 */
@Configuration
public class ToolRegisterConfig {
    @Value("${prodigal.search-api.api-key}")
    private String searchApiKey;
    @Value("${prodigal.amap.api-key}")
    private String amapApiKey;
    @Resource
    private MailConfig mailConfig;

    @Bean
    public ToolCallback[] allTools(JavaMailSender javaMailSender){
        FileOperationTool fileOperationTool = new FileOperationTool();
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        EmailTool emailTool = new EmailTool(javaMailSender, mailConfig.getUsername(), mailConfig.getHost());
        WeatherTool weatherTool = new WeatherTool(amapApiKey);
        DateTimeTool dateTimeTool = new DateTimeTool();
        ImageSearchTool imageSearchTool = new ImageSearchTool();
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        TerminateTool terminateTool = new TerminateTool();
        return ToolCallbacks.from(
                fileOperationTool,
                webSearchTool,
                emailTool,
                weatherTool,
                dateTimeTool,
                pdfGenerationTool,
                terminateTool,
                imageSearchTool
        );
    }
}
