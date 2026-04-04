package com.prodigal.travel.service.auth;

import com.prodigal.travel.config.MailConfig;
import com.prodigal.travel.exception.BusinessException;
import com.prodigal.travel.exception.ResponseStatus;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private static final String PERSONAL = "Prodigal AI 旅游助手";

    private final JavaMailSender javaMailSender;
    private final MailConfig mailConfig;

    public void sendEmailCode(String toEmail, String code, int ttlMinutes) throws MessagingException {
        if (!StringUtils.hasText(mailConfig.getHost()) || !StringUtils.hasText(mailConfig.getUsername())) {
            throw new IllegalStateException("未配置 spring.mail，无法发送验证码邮件");
        }
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(mailConfig.getUsername(), PERSONAL);
            helper.setTo(toEmail);
            helper.setSubject("【AI 旅游助手】邮箱登录验证码");
            helper.setText(buildEmailCodeBody(code, ttlMinutes), false);
            javaMailSender.send(message);
        } catch (UnsupportedEncodingException e) {
            throw new BusinessException(ResponseStatus.EMAIL_SEND_ERROR);
        }
    }

    private static String buildEmailCodeBody(String code, int ttlMinutes) {
        return "您的登录验证码为：" + code + "\n\n"
                + "验证码 " + ttlMinutes + " 分钟内有效，请勿泄露给他人。\n"
                + "如非本人操作，请立即修改密码并联系客服。";
    }
}
