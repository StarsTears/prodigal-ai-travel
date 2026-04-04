package com.prodigal.travel.service.auth;

import cn.hutool.core.util.RandomUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.prodigal.travel.config.properties.AuthRegisterProperties;
import com.prodigal.travel.exception.BusinessException;
import com.prodigal.travel.exception.ResponseStatus;
import com.prodigal.travel.controller.vo.LoginResponse;
import com.prodigal.travel.model.entity.User;
import com.prodigal.travel.security.JwtTokenHelper;
import com.prodigal.travel.security.LoginTokenCache;
import com.prodigal.travel.service.UserService;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserAuthServiceImpl implements UserAuthService {

    private final MailService mailService;
    private final UserService userService;
    private final AuthRegisterProperties authRegisterProperties;
    private final JwtTokenHelper jwtTokenHelper;
    private final LoginTokenCache loginTokenCache;

    private final Cache<String, Long> emailCodeSendAt = Caffeine.newBuilder()
            .maximumSize(200_000)
            .build();

    private final Cache<String, String> emailCodeCache;


    public UserAuthServiceImpl(MailService mailService, UserService userService,
                               AuthRegisterProperties authRegisterProperties,
                               JwtTokenHelper jwtTokenHelper, LoginTokenCache loginTokenCache) {
        this.mailService = mailService;
        this.userService = userService;
        this.authRegisterProperties = authRegisterProperties;
        this.emailCodeCache = Caffeine.newBuilder()
                .maximumSize(200_000)
                .expireAfterWrite(authRegisterProperties.getCodeTtlMinutes(), TimeUnit.MINUTES)
                .build();
        this.jwtTokenHelper = jwtTokenHelper;
        this.loginTokenCache = loginTokenCache;
    }

    @Override
    public String sendEmailCode(String email) {
        String normalized = normalizeEmail(email);
        if (!userService.exists(normalized)) {
            throw new BusinessException(ResponseStatus.USER_NOT_FOUND, "该邮箱未注册，请先完成注册");
        }

        long now = System.currentTimeMillis();
        long intervalMs = TimeUnit.SECONDS.toMillis(authRegisterProperties.getSendIntervalSeconds());
        Long last = emailCodeSendAt.getIfPresent(normalized);
        if (last != null && now - last < intervalMs) {
            throw new BusinessException(ResponseStatus.EMAIL_SEND_TOO_FREQUENT);
        }

        String code = RandomUtil.randomNumbers(6);
        emailCodeCache.put(normalized, code);
        emailCodeSendAt.put(normalized, now);

        try {
            mailService.sendEmailCode(normalized, code, authRegisterProperties.getCodeTtlMinutes());
            return code;
        } catch (MessagingException e) {
            log.error("发送登录验证码失败: {}", normalized, e);
            emailCodeCache.invalidate(normalized);
            emailCodeSendAt.invalidate(normalized);
            throw new BusinessException(ResponseStatus.OPERATION_ERROR, "验证码邮件发送失败，请稍后重试");
        }
    }

    @Override
    public LoginResponse login(String account, String rawPassword) {
        return userService.login(account, rawPassword);
    }

    @Override
    public LoginResponse loginByEmailCode(String email, String code) {
        String normalizedEmail = normalizeEmail(email);
        String expected = emailCodeCache.getIfPresent(normalizedEmail);
        if (expected == null || !expected.equals(code.trim())) {
            throw new BusinessException(ResponseStatus.EMAIL_CODE_INVALID);
        }
        User user = userService.findByEmail(normalizedEmail);
        if (user == null) {
            emailCodeCache.invalidate(normalizedEmail);
            throw new BusinessException(ResponseStatus.USER_NOT_FOUND, "该邮箱未注册，请先完成注册");
        }

        LoginResponse resp = userService.loginWithoutPassword(user);
        emailCodeCache.invalidate(normalizedEmail);
        return resp;
    }

    @Override
    public void logout(String authorizationHeader) {
        String token = jwtTokenHelper.extractBearerToken(authorizationHeader);
        if (token != null) {
            loginTokenCache.revoke(token);
        }
    }

    @Override
    public void deregister(String authorizationHeader) {
        String token = jwtTokenHelper.extractBearerToken(authorizationHeader);
        if (token == null) {
            throw new BusinessException(ResponseStatus.USER_NOT_LOGIN, "请先登录");
        }
        Long userId = jwtTokenHelper.parseVerifiedUserId(token);
        if (userId == null) {
            throw new BusinessException(ResponseStatus.USER_NOT_LOGIN, "Token 无效或已过期");
        }
        User user = userService.getById(userId);
        if (user == null) {
            throw new BusinessException(ResponseStatus.USER_NOT_FOUND);
        }
        boolean removed = userService.removeById(userId);
        if (!removed) {
            throw new BusinessException(ResponseStatus.OPERATION_ERROR, "注销失败，请稍后重试");
        }
        loginTokenCache.revokeAllForUser(userId);
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
