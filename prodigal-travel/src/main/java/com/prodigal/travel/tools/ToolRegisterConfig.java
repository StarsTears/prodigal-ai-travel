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
    @Value("${prodigal.pexels.api-key}")
    private  String imageSearchApiKey;
    @Value("${prodigal.baidu.app-id}")
    private String baiduAppId;
    @Value("${prodigal.baidu.api-key}")
    private String baiduApiKey;
    @Resource
    private MailConfig mailConfig;

    @Resource
    ClientIpResolver clientIpResolver;

    @Bean
    public ToolCallback[] allTools(JavaMailSender javaMailSender, WeatherTool weatherTool) {
        //文件读取工具
        FileOperationTool fileOperationTool = new FileOperationTool();
        //网页搜索工具
        WebSearchTool webSearchTool = new WebSearchTool(searchApiKey);
        //邮件发送工具
        EmailTool emailTool = new EmailTool(javaMailSender, mailConfig.getUsername(), mailConfig.getHost());
        //时间工具
        DateTimeTool dateTimeTool = new DateTimeTool();
        //IP工具
        IPTool ipTool = new IPTool(clientIpResolver);
        //图片搜索工具
        ImageSearchTool imageSearchTool = new ImageSearchTool(imageSearchApiKey);
        //百度翻译工具
        BaiduTranslateTool baiduTranslateTool = new BaiduTranslateTool(baiduAppId, baiduApiKey);
        //PDF生成工具
        PDFGenerationTool pdfGenerationTool = new PDFGenerationTool();
        //停止工具
        TerminateTool terminateTool = new TerminateTool();
        return ToolCallbacks.from(
                fileOperationTool,
                webSearchTool,
                emailTool,
                weatherTool,
                dateTimeTool,
                ipTool,
                pdfGenerationTool,
                terminateTool,
                imageSearchTool,
                baiduTranslateTool
        );
    }
}
