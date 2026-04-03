package com.prodigal.travel.tools;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 邮件发送工具类
 * @since 2026/4/2
 */
import lombok.extern.slf4j.Slf4j;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Slf4j
public class EmailTool {
    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String smtpHost;
    private final String personal = "Prodigal AI 旅游助手";

    public EmailTool(JavaMailSender mailSender, String fromAddress, String smtpHost) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.smtpHost = smtpHost;
    }

    @Tool(description = "Send an email. If SMTP is not configured, returns a friendly error message instead of failing.")
    public String sendEmail(
            @ToolParam(description = "recipient mailboxes, multiple separated by English commas（eg：a@x.com,b@y.com）") String to,
            @ToolParam(description = "email header") String subject,
            @ToolParam(description = "the content of the email body") String content,
            @ToolParam(description = "Whether the email body is HTML or not, fill in true/false (if not, it will default to false)") Boolean html
    ) {
        log.info("EmailTool.sendEmail invoked. to={}, subject={}, html={}", to, subject, html);
        if (to == null || to.isBlank()) {
            return "邮件发送失败：收件人（to）不能为空";
        }
        if (subject == null) {
            subject = "";
        }
        if (content == null) {
            content = "";
        }

        // 为了避免在未配置 SMTP 时触发运行时异常，这里做显式防护。
        if (smtpHost == null || smtpHost.isBlank()) {
            log.warn("EmailTool sendEmail aborted: spring.mail.host not configured.");
            return "邮件服务未配置：请设置 `spring.mail.host`（以及相关用户名/密码等）。";
        }
        if (fromAddress == null || fromAddress.isBlank()) {
            log.warn("EmailTool sendEmail aborted: prodigal.email/from address not configured.");
            return "邮件发送失败：`from` 地址未配置，请设置 `prodigal.email`。";
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());

            // 解析逗号分隔的收件人
            String[] recipients = Arrays.stream(to.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .toArray(String[]::new);
            if (recipients.length == 0) {
                return "邮件发送失败：收件人解析为空，请检查 to 参数格式";
            }

            helper.setFrom(fromAddress, personal);
            helper.setTo(recipients);
            helper.setSubject(subject);

            boolean isHtml = html != null && html;
            helper.setText(content, isHtml);

            mailSender.send(mimeMessage);
            return "邮件发送成功：to=" + to;
        } catch (MessagingException e) {
            return "邮件发送失败（邮件内容构建错误）：" + e.getMessage();
        } catch (Exception e) {
            return "邮件发送失败：" + e.getMessage();
        }
    }
}
