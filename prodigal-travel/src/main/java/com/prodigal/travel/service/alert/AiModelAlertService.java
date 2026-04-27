package com.prodigal.travel.service.alert;

import com.prodigal.travel.config.properties.AiAlertProperties;
import com.prodigal.travel.model.dto.MailContentDTO;
import com.prodigal.travel.service.auth.MailService;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 识别大模型额度耗尽/到期/权限受限等异常并发送邮件告警。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelAlertService {

    private static final Pattern CODE_PATTERN = Pattern.compile("\"code\"\\s*:\\s*\"([^\"]+)\"");

    private static final List<String> ALERT_KEYWORDS = List.of(
            "AllocationQuota.FreeTierOnly",
            "free tier",
            "exhausted",
            "quota",
            "余额",
            "额度",
            "expired",
            "过期",
            "insufficient",
            "forbidden"
    );

    private final MailService mailService;
    private final AiAlertProperties aiAlertProperties;

    @Value("${spring.application.name:prodigal-ai-travel}")
    private String applicationName;

    @Value("${spring.profiles.active:unknown}")
    private String activeProfile;

    private final Map<String, Long> alertCooldownCache = new ConcurrentHashMap<>();

    public void tryAlert(Throwable throwable, String operation) {
        if (!aiAlertProperties.isEnabled() || aiAlertProperties.getRecipients().isEmpty()) {
            return;
        }
        Optional<WebClientResponseException> webClientException = findWebClientException(throwable);
        String body = webClientException.map(WebClientResponseException::getResponseBodyAsString).orElse("");
        String message = throwable.getMessage() == null ? "" : throwable.getMessage();
        String mergedText = (message + " " + body).toLowerCase();

        HttpStatusCode statusCode = webClientException.map(WebClientResponseException::getStatusCode).orElse(null);
        int status = statusCode == null ? 0 : statusCode.value();
        boolean statusMatched = status == 401 || status == 402 || status == 403 || status == 429;
        boolean keywordMatched = ALERT_KEYWORDS.stream().anyMatch(k -> mergedText.contains(k.toLowerCase()));
        if (!statusMatched && !keywordMatched) {
            return;
        }

        String errorCode = extractErrorCode(body).orElse("UNKNOWN");
        String dedupeKey = status + "|" + errorCode + "|" + operation;
        if (isInCooldown(dedupeKey)) {
            return;
        }
        doSendMail(operation, status, errorCode, body, message);
    }

    private Optional<WebClientResponseException> findWebClientException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof WebClientResponseException webClientResponseException) {
                return Optional.of(webClientResponseException);
            }
            current = current.getCause();
        }
        return Optional.empty();
    }

    private Optional<String> extractErrorCode(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return Optional.empty();
        }
        Matcher matcher = CODE_PATTERN.matcher(responseBody);
        if (!matcher.find()) {
            return Optional.empty();
        }
        return Optional.ofNullable(matcher.group(1));
    }

    private boolean isInCooldown(String key) {
        long now = System.currentTimeMillis();
        long cooldownMs = aiAlertProperties.getCooldownMinutes() * 60_000L;
        Long lastSent = alertCooldownCache.get(key);
        if (lastSent != null && now - lastSent < cooldownMs) {
            return true;
        }
        alertCooldownCache.put(key, now);
        return false;
    }

    private void doSendMail(String operation, int status, String errorCode, String body, String message) {
        String subject = String.format("[AI告警] 大模型调用异常: %s", errorCode);
        String content = buildContent(operation, status, errorCode, body, message);
        for (String recipient : aiAlertProperties.getRecipients()) {
            if (!StringUtils.hasText(recipient)) {
                continue;
            }
            try {
                mailService.sendEmail(MailContentDTO.builder()
                        .to(recipient.trim())
                        .subject(subject)
                        .content(content)
                        .isHtml(true)
                        .build());
            } catch (MessagingException | RuntimeException e) {
                log.error("AI 告警邮件发送失败, recipient={}", recipient, e);
            }
        }
    }

    private String buildContent(String operation, int status, String errorCode, String body, String message) {
        return "<html><body style=\"font-family:Arial,Helvetica,sans-serif;line-height:1.6;color:#222;\">"
                + "<h3 style=\"margin:0 0 12px 0;color:#c0392b;\">检测到大模型调用异常，请尽快处理</h3>"
                + "<table style=\"border-collapse:collapse;\">"
                + "<tr><td style=\"padding:4px 10px 4px 0;\"><strong>应用</strong></td><td>" + escapeHtml(applicationName) + "</td></tr>"
                + "<tr><td style=\"padding:4px 10px 4px 0;\"><strong>环境</strong></td><td>" + escapeHtml(activeProfile) + "</td></tr>"
                + "<tr><td style=\"padding:4px 10px 4px 0;\"><strong>时间</strong></td><td>" + escapeHtml(String.valueOf(LocalDateTime.now())) + "</td></tr>"
                + "<tr><td style=\"padding:4px 10px 4px 0;\"><strong>操作</strong></td><td>" + escapeHtml(operation) + "</td></tr>"
                + "<tr><td style=\"padding:4px 10px 4px 0;\"><strong>HTTP状态</strong></td><td>" + status + "</td></tr>"
                + "<tr><td style=\"padding:4px 10px 4px 0;\"><strong>错误码</strong></td><td>" + escapeHtml(errorCode) + "</td></tr>"
                + "</table>"
                + "<p style=\"margin:12px 0 6px 0;\"><strong>异常信息</strong></p>"
                + "<pre style=\"white-space:pre-wrap;background:#f6f8fa;padding:10px;border-radius:6px;\">"
                + escapeHtml(message)
                + "</pre>"
                + "<p style=\"margin:12px 0 6px 0;\"><strong>响应体</strong></p>"
                + "<pre style=\"white-space:pre-wrap;background:#f6f8fa;padding:10px;border-radius:6px;\">"
                + escapeHtml(body)
                + "</pre>"
                + "</body></html>";
    }

    private String escapeHtml(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
