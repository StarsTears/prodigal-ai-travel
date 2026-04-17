package com.prodigal.travel.service.auth.impl;

import cn.hutool.core.util.RandomUtil;
import com.prodigal.travel.config.properties.AuthRegisterProperties;
import com.prodigal.travel.constants.CacheKeyConstant;
import com.prodigal.travel.exception.BusinessException;
import com.prodigal.travel.exception.ResponseStatus;
import com.prodigal.travel.controller.vo.LoginResponse;
import com.prodigal.travel.model.dto.MailContentDTO;
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
 * {@link UserAuthService} 实现：邮件验证码与频控走 redisTemplate；登录成功路径委托 {@link UserService}。
 */
@Slf4j
@Service
public class UserAuthServiceImpl implements UserAuthService {


    private final MailService mailService;
    private final UserService userService;
    private final AuthRegisterProperties authRegisterProperties;
    private final JwtTokenHelper jwtTokenHelper;
    private final LoginTokenCache loginTokenCache;
    private final StringRedisTemplate redisTemplate;

    public UserAuthServiceImpl(MailService mailService, UserService userService,
                               AuthRegisterProperties authRegisterProperties,
                               JwtTokenHelper jwtTokenHelper, LoginTokenCache loginTokenCache,
                               StringRedisTemplate redisTemplate) {
        this.mailService = mailService;
        this.userService = userService;
        this.authRegisterProperties = authRegisterProperties;
        this.jwtTokenHelper = jwtTokenHelper;
        this.loginTokenCache = loginTokenCache;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 仅对已注册邮箱发码；间隔内重复请求会抛「发送过于频繁」；邮件异常时清除已写入的验证码与节流键。
     */
    @Override
    public String sendEmailCode(String email) {
        String toEmail = normalizeEmail(email);
        if (!userService.exists(toEmail)) {
            throw new BusinessException(ResponseStatus.USER_NOT_FOUND, "该邮箱未注册，请先完成注册");
        }

        String throttleKey = CacheKeyConstant.EMAIL_THROTTLE_KEY_PREFIX + toEmail;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(throttleKey))) {
            throw new BusinessException(ResponseStatus.EMAIL_SEND_TOO_FREQUENT);
        }

        String code = RandomUtil.randomNumbers(6);
        String codeKey = CacheKeyConstant.EMAIL_CODE_KEY_PREFIX + toEmail;
        Duration codeTtl = Duration.ofMinutes(authRegisterProperties.getCodeTtlMinutes());
        Duration throttleTtl = Duration.ofSeconds(authRegisterProperties.getSendIntervalSeconds());
        redisTemplate.opsForValue().set(codeKey, code, codeTtl);
        redisTemplate.opsForValue().set(throttleKey, "1", throttleTtl);

        try {
            MailContentDTO mailContentDTO = MailContentDTO.builder()
                    .subject("【AI 旅游助手】邮箱登录验证码")
                    .to(toEmail)
                    .content("您的登录验证码为：" + code + "\n\n"
                            + "验证码 " + authRegisterProperties.getCodeTtlMinutes() + " 分钟内有效，请勿泄露给他人。\n"
                            + "如非本人操作，请立即修改密码并联系客服。")
                    .isHtml(false)
                    .build();
            mailService.sendEmail(mailContentDTO);
            return code;
        } catch (MessagingException e) {
            log.error("发送登录验证码失败: {}", toEmail, e);
            redisTemplate.delete(codeKey);
            redisTemplate.delete(throttleKey);
            throw new BusinessException(ResponseStatus.OPERATION_ERROR, "验证码邮件发送失败，请稍后重试");
        }
    }

    @Override
    public LoginResponse login(String account, String rawPassword) {
        return userService.login(account, rawPassword);
    }

    @Override
    public LoginResponse loginByEmailCode(String email, String code) {
        String toEmail = normalizeEmail(email);
        String codeKey = CacheKeyConstant.EMAIL_CODE_KEY_PREFIX + toEmail;
        String expected = redisTemplate.opsForValue().get(codeKey);
        if (expected == null || !expected.equals(code.trim())) {
            throw new BusinessException(ResponseStatus.EMAIL_CODE_INVALID);
        }
        User user = userService.findByEmail(toEmail);
        if (user == null) {
            redisTemplate.delete(codeKey);
            throw new BusinessException(ResponseStatus.USER_NOT_FOUND, "该邮箱未注册，请先完成注册");
        }

        LoginResponse resp = userService.loginWithoutPassword(user);
        redisTemplate.delete(codeKey);
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

    /**
     * 去空白并转小写，作为 redisTemplate 键与库中邮箱比对的一致形式。
     */
    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
