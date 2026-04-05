package com.prodigal.travel.tools;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 邮件发送工具类
 * @since 2026/4/2
 */
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.Header;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.prodigal.travel.constants.TravelConstant;
import lombok.extern.slf4j.Slf4j;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class EmailTool {

    private static final int MAX_IMAGE_ATTACHMENTS = 5;
    private static final int MAX_BYTES_PER_IMAGE = 8 * 1024 * 1024;
    private static final int IMAGE_DOWNLOAD_TIMEOUT_MS = 25_000;

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final String smtpHost;

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

            helper.setFrom(fromAddress, TravelConstant.PERSONAL);
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

            helper.setFrom(fromAddress, TravelConstant.PERSONAL);
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

    @Tool(description = "Send an email attaching one or more images downloaded from HTTPS URLs (comma-separated). Use when the user asks to email the scenery photos or images shown above to a mailbox; pass URLs from searchImage or the same session, never fabricated links.")
    public String sendEmailWithImageUrls(
            @ToolParam(description = "recipient mailboxes, multiple separated by English commas（eg：a@x.com,b@y.com）") String to,
            @ToolParam(description = "email header") String subject,
            @ToolParam(description = "the content of the email body") String content,
            @ToolParam(description = "Whether the email body is HTML or not, fill in true/false (if not, it will default to false)") Boolean html,
            @ToolParam(description = "Comma-separated HTTPS image URLs only (e.g. return value of searchImage), max 5") String imageUrls
    ) {
        log.info("EmailTool.sendEmailWithImageUrls invoked. to={}, subject={}, html={}, imageUrls.length={}",
                to, subject, html, imageUrls == null ? 0 : imageUrls.length());
        String checkResult = checkBaseConfig(to);
        if (checkResult != null) {
            return checkResult;
        }
        if (imageUrls == null || imageUrls.isBlank()) {
            return "邮件发送失败：图片地址（imageUrls）不能为空，请传入 HTTPS 图片链接（可与 searchImage 返回格式一致，逗号分隔）";
        }
        if (subject == null) {
            subject = "";
        }
        if (content == null) {
            content = "";
        }

        String[] raw = imageUrls.split(",");
        List<String> urlList = new ArrayList<>();
        for (String s : raw) {
            String u = s == null ? "" : s.trim();
            if (!u.isEmpty()) {
                urlList.add(u);
            }
        }
        if (urlList.isEmpty()) {
            return "邮件发送失败：未能解析出任何图片 URL";
        }
        if (urlList.size() > MAX_IMAGE_ATTACHMENTS) {
            return "邮件发送失败：图片数量超过上限（最多 " + MAX_IMAGE_ATTACHMENTS + " 张），请减少 imageUrls 条数";
        }

        List<NamedBytes> attachments = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        int index = 1;
        for (String url : urlList) {
            if (!isPermittedHttpsImageUrl(url)) {
                failures.add("URL 未通过安全校验（仅允许 https，且禁止内网/本机地址）：" + truncateForLog(url));
                continue;
            }
            try {
                HttpResponse resp = HttpRequest.get(url)
                        .timeout(IMAGE_DOWNLOAD_TIMEOUT_MS)
                        .execute();
                if (!resp.isOk()) {
                    failures.add("HTTP " + resp.getStatus() + "：" + truncateForLog(url));
                    continue;
                }
                byte[] bytes = resp.bodyBytes();
                if (bytes == null || bytes.length == 0) {
                    failures.add("空响应：" + truncateForLog(url));
                    continue;
                }
                if (bytes.length > MAX_BYTES_PER_IMAGE) {
                    failures.add("单张超过 " + (MAX_BYTES_PER_IMAGE / 1024 / 1024) + "MB：" + truncateForLog(url));
                    continue;
                }
                String ct = resp.header(Header.CONTENT_TYPE);
                if (StrUtil.isNotBlank(ct)) {
                    String lower = ct.split(";")[0].trim().toLowerCase();
                    if (!lower.startsWith("image/")) {
                        failures.add("非图片 Content-Type（" + lower + "）：" + truncateForLog(url));
                        continue;
                    }
                }
                String fileName = buildImageAttachmentName(url, index++, ct);
                attachments.add(new NamedBytes(fileName, bytes));
            } catch (Exception e) {
                log.warn("download image failed: {}", url, e);
                failures.add("下载异常：" + truncateForLog(url) + " — " + e.getMessage());
            }
        }

        if (attachments.isEmpty()) {
            return "邮件发送失败：没有成功下载任何图片。详情：" + String.join("；", failures);
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, StandardCharsets.UTF_8.name());

            String[] recipients = parseRecipients(to);
            if (recipients.length == 0) {
                return "邮件发送失败：收件人解析为空，请检查 to 参数格式";
            }

            helper.setFrom(fromAddress, TravelConstant.PERSONAL);
            helper.setTo(recipients);
            helper.setSubject(subject);
            helper.setText(content, html != null && html);

            for (NamedBytes nb : attachments) {
                helper.addAttachment(nb.name(), new ByteArrayResource(nb.data()));
            }

            mailSender.send(mimeMessage);
            StringBuilder msg = new StringBuilder("邮件发送成功（含 ")
                    .append(attachments.size())
                    .append(" 张配图附件）：to=")
                    .append(to);
            if (!failures.isEmpty()) {
                msg.append("；部分 URL 未附加：").append(String.join("；", failures));
            }
            return "邮件发送成功（含附件）：to=" + to ;
        } catch (MessagingException e) {
            return "邮件发送失败（邮件内容构建错误）：" + e.getMessage();
        } catch (Exception e) {
            return "邮件发送失败：" + e.getMessage();
        }
    }

    private static String truncateForLog(String url) {
        if (url == null) {
            return "";
        }
        return url.length() > 120 ? url.substring(0, 117) + "..." : url;
    }

    private static boolean isPermittedHttpsImageUrl(String urlString) {
        try {
            URI u = URI.create(urlString.trim());
            if (!"https".equalsIgnoreCase(u.getScheme())) {
                return false;
            }
            String host = u.getHost();
            if (StrUtil.isBlank(host)) {
                return false;
            }
            String h = host.toLowerCase();
            if ("localhost".equals(h) || "[::1]".equals(h) || "127.0.0.1".equals(h)) {
                return false;
            }
            if (h.startsWith("192.168.") || h.startsWith("10.")) {
                return false;
            }
            return !h.matches("^172\\.(1[6-9]|2\\d|3[0-1])\\..*");
        } catch (Exception e) {
            return false;
        }
    }

    private static String buildImageAttachmentName(String url, int index, String contentType) {
        try {
            URI uri = URI.create(url.trim());
            String path = uri.getPath();
            if (StrUtil.isNotBlank(path)) {
                int slash = path.lastIndexOf('/');
                if (slash >= 0 && slash < path.length() - 1) {
                    String last = path.substring(slash + 1);
                    if (last.matches("[a-zA-Z0-9._-]+\\.(?i)(jpe?g|png|gif|webp|bmp)")) {
                        return last;
                    }
                }
            }
        } catch (Exception ignored) {
            // fall through
        }
        String ext = extensionFromContentType(contentType);
        return "travel_image_" + index + ext;
    }

    private static String extensionFromContentType(String contentType) {
        if (StrUtil.isBlank(contentType)) {
            return ".jpg";
        }
        String base = contentType.split(";")[0].trim().toLowerCase();
        return switch (base) {
            case "image/png" -> ".png";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            case "image/bmp" -> ".bmp";
            default -> ".jpg";
        };
    }

    private record NamedBytes(String name, byte[] data) {}

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
