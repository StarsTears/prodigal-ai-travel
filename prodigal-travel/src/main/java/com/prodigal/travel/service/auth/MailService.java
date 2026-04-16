package com.prodigal.travel.service.auth;

import com.prodigal.travel.config.MailConfig;
import com.prodigal.travel.constants.TravelConstant;
import com.prodigal.travel.exception.BusinessException;
import com.prodigal.travel.exception.ResponseStatus;
import com.prodigal.travel.model.dto.MailContentDTO;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    private final JavaMailSender javaMailSender;
    private final MailConfig mailConfig;

    public void sendEmail(MailContentDTO mailContentDTO) throws MessagingException {
        if (!StringUtils.hasText(mailConfig.getHost()) || !StringUtils.hasText(mailConfig.getUsername())) {
            throw new IllegalStateException("未配置 spring.mail，无法发送邮件");
        }
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(mailConfig.getUsername(), TravelConstant.PERSONAL);
            helper.setTo(mailContentDTO.getTo());
            helper.setSubject(mailContentDTO.getSubject());
            helper.setText(mailContentDTO.getContent(), mailContentDTO.isHtml());
            javaMailSender.send(message);
        } catch (UnsupportedEncodingException e) {
            log.error("邮件发送失败", e);
            throw new BusinessException(ResponseStatus.EMAIL_SEND_ERROR);
        }
    }
}
