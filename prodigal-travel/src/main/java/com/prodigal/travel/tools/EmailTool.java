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
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.File;
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
        String checkResult = checkBaseConfig(to);
        if (checkResult != null) return checkResult;
        if (subject == null) {
            subject = "";
        }
        if (content == null) {
            content = "";
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());

            String[] recipients = parseRecipients(to);
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

    @Tool(description = "Send an email with text and one attachment file.")
    public String sendEmailWithAttachment(
            @ToolParam(description = "recipient mailboxes, multiple separated by English commas（eg：a@x.com,b@y.com）") String to,
            @ToolParam(description = "email header") String subject,
            @ToolParam(description = "the content of the email body") String content,
            @ToolParam(description = "Whether the email body is HTML or not, fill in true/false (if not, it will default to false)") Boolean html,
            @ToolParam(description = "local file path of attachment, e.g. /tmp/plan.pdf") String attachmentPath,
            @ToolParam(description = "optional attachment filename shown in email (if blank, use file original name)") String attachmentName
    ) {
        log.info("EmailTool.sendEmailWithAttachment invoked. to={}, subject={}, html={}, attachmentPath={}",
                to, subject, html, attachmentPath);
        String checkResult = checkBaseConfig(to);
        if (checkResult != null) return checkResult;
        if (attachmentPath == null || attachmentPath.isBlank()) {
            return "邮件发送失败：附件路径（attachmentPath）不能为空";
        }
        if (subject == null) {
            subject = "";
        }
        if (content == null) {
            content = "";
        }

        File file = new File(attachmentPath);
        if (!file.exists() || !file.isFile()) {
            return "邮件发送失败：附件不存在或不是文件 -> " + attachmentPath;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());

            String[] recipients = parseRecipients(to);
            if (recipients.length == 0) {
                return "邮件发送失败：收件人解析为空，请检查 to 参数格式";
            }

            helper.setFrom(fromAddress, personal);
            helper.setTo(recipients);
            helper.setSubject(subject);
            helper.setText(content, html != null && html);

            String finalAttachmentName = (attachmentName == null || attachmentName.isBlank())
                    ? file.getName()
                    : attachmentName.trim();
            helper.addAttachment(finalAttachmentName, new FileSystemResource(file));

            mailSender.send(mimeMessage);
            return "邮件发送成功（含附件）：to=" + to + ", attachment=" + finalAttachmentName;
        } catch (MessagingException e) {
            return "邮件发送失败（邮件内容构建错误）：" + e.getMessage();
        } catch (Exception e) {
            return "邮件发送失败：" + e.getMessage();
        }
    }

    private String checkBaseConfig(String to) {
        if (to == null || to.isBlank()) {
            return "邮件发送失败：收件人（to）不能为空";
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
        return null;
    }

    private String[] parseRecipients(String to) {
        return Arrays.stream(to.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toArray(String[]::new);
    }
}
