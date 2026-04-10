package com.prodigal.travel.service.auth.impl;

import cn.hutool.core.util.RandomUtil;
import com.prodigal.travel.config.properties.AuthRegisterProperties;
import com.prodigal.travel.exception.BusinessException;
import com.prodigal.travel.exception.ResponseStatus;
import com.prodigal.travel.controller.vo.LoginResponse;
import com.prodigal.travel.model.entity.User;
import com.prodigal.travel.security.JwtTokenHelper;
import com.prodigal.travel.security.LoginTokenCache;
import com.prodigal.travel.service.auth.MailService;
import com.prodigal.travel.service.auth.UserAuthService;
import com.prodigal.travel.service.auth.UserService;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

/**
 * {@link UserAuthService} 实现：邮件验证码与频控走 Redis；登录成功路径委托 {@link UserService}。
 */
@Slf4j
@Service
public class UserAuthServiceImpl implements UserAuthService {

    private static final String EMAIL_CODE_KEY_PREFIX = "prodigal:auth:email-code:";
    private static final String EMAIL_THROTTLE_KEY_PREFIX = "prodigal:auth:email-throttle:";

    private final MailService mailService;
    private final UserService userService;
    private final AuthRegisterProperties authRegisterProperties;
    private final JwtTokenHelper jwtTokenHelper;
    private final LoginTokenCache loginTokenCache;
    private final StringRedisTemplate redis;

    public UserAuthServiceImpl(MailService mailService, UserService userService,
                               AuthRegisterProperties authRegisterProperties,
                               JwtTokenHelper jwtTokenHelper, LoginTokenCache loginTokenCache,
                               StringRedisTemplate redisTemplate) {
        this.mailService = mailService;
        this.userService = userService;
        this.authRegisterProperties = authRegisterProperties;
        this.jwtTokenHelper = jwtTokenHelper;
        this.loginTokenCache = loginTokenCache;
        this.redis = redisTemplate;
    }

    /**
     * 仅对已注册邮箱发码；间隔内重复请求会抛「发送过于频繁」；邮件异常时清除已写入的验证码与节流键。
     */
    @Override
    public String sendEmailCode(String email) {
        String normalized = normalizeEmail(email);
        if (!userService.exists(normalized)) {
            throw new BusinessException(ResponseStatus.USER_NOT_FOUND, "该邮箱未注册，请先完成注册");
        }

        String throttleKey = EMAIL_THROTTLE_KEY_PREFIX + normalized;
        if (Boolean.TRUE.equals(redis.hasKey(throttleKey))) {
            throw new BusinessException(ResponseStatus.EMAIL_SEND_TOO_FREQUENT);
        }

        String code = RandomUtil.randomNumbers(6);
        String codeKey = EMAIL_CODE_KEY_PREFIX + normalized;
        Duration codeTtl = Duration.ofMinutes(authRegisterProperties.getCodeTtlMinutes());
        Duration throttleTtl = Duration.ofSeconds(authRegisterProperties.getSendIntervalSeconds());
        redis.opsForValue().set(codeKey, code, codeTtl);
        redis.opsForValue().set(throttleKey, "1", throttleTtl);

        try {
            mailService.sendEmailCode(normalized, code, authRegisterProperties.getCodeTtlMinutes());
            return code;
        } catch (MessagingException e) {
            log.error("发送登录验证码失败: {}", normalized, e);
            redis.delete(codeKey);
            redis.delete(throttleKey);
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
        String codeKey = EMAIL_CODE_KEY_PREFIX + normalizedEmail;
        String expected = redis.opsForValue().get(codeKey);
        if (expected == null || !expected.equals(code.trim())) {
            throw new BusinessException(ResponseStatus.EMAIL_CODE_INVALID);
        }
        User user = userService.findByEmail(normalizedEmail);
        if (user == null) {
            redis.delete(codeKey);
            throw new BusinessException(ResponseStatus.USER_NOT_FOUND, "该邮箱未注册，请先完成注册");
        }

        LoginResponse resp = userService.loginWithoutPassword(user);
        redis.delete(codeKey);
        return resp;
    }

    /**
     * 无 Authorization 或格式错误时静默跳过（不抛错），与可选请求头语义一致。
     */
    @Override
    public void logout(String authorizationHeader) {
        String token = jwtTokenHelper.extractBearerToken(authorizationHeader);
        if (token != null) {
            loginTokenCache.revoke(token);
        }
    }

    /**
     * 必须带合法 Bearer；先校验 JWT 再删库，最后吊销该用户全部已登记 token。
     */
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

    /** 去空白并转小写，作为 Redis 键与库中邮箱比对的一致形式。 */
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
