package com.prodigal.travel.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.jwt.JWTUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.prodigal.travel.config.properties.JwtProperties;
import com.prodigal.travel.exception.BusinessException;
import com.prodigal.travel.exception.ResponseStatus;
import com.prodigal.travel.exception.ThrowUtils;
import com.prodigal.travel.mapper.UserMapper;
import com.prodigal.travel.controller.vo.LoginResponse;
import com.prodigal.travel.model.entity.User;
import com.prodigal.travel.security.LoginTokenCache;
import com.prodigal.travel.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * @author 35104
 * @description 针对表【user(用户表)】的数据库操作Service实现
 * @createDate 2026-04-04 13:02:34
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final JwtProperties jwtProperties;
    private final LoginTokenCache loginTokenCache;

    public UserServiceImpl(JwtProperties jwtProperties, LoginTokenCache loginTokenCache) {
        this.jwtProperties = jwtProperties;
        this.loginTokenCache = loginTokenCache;
    }

    @Override
    public boolean register(String email, String username, String rawPassword, String nickname) {

        //验证用户是否存在、邮箱是否已存在
        ThrowUtils.throwIf(this.exists(new LambdaQueryWrapper<User>().eq(User::getUsername, username)), ResponseStatus.USER_USERNAME_EXISTS);
        ThrowUtils.throwIf(this.exists(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) , ResponseStatus.USER_EMAIL_EXISTS);

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(BCrypt.hashpw(rawPassword, BCrypt.gensalt()));
        user.setNickname(StringUtils.hasText(nickname) ? nickname : username);
        boolean ok = this.save(user);
        ThrowUtils.throwIf(!ok, ResponseStatus.OPERATION_ERROR);
        return Boolean.TRUE;
    }

    @Override
    public LoginResponse login(String account, String rawPassword) {
        String key = account.trim();

        User user = getOne(new LambdaQueryWrapper<User>()
                .and(w -> w.eq(User::getUsername, key).or().eq(User::getEmail, key.toLowerCase(Locale.ROOT)))
                .last("LIMIT 1"));
        ThrowUtils.throwIf(user == null, new BusinessException(ResponseStatus.USER_PASSWORD_ERROR));

        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ResponseStatus.USER_ACCOUNT_DISABLED);
        }
        if (!BCrypt.checkpw(rawPassword, user.getPasswordHash())) {
            throw new BusinessException(ResponseStatus.USER_PASSWORD_ERROR);
        }
        return buildLoginResponse(user);
    }

    @Override
    public User findByEmail(String normalizedEmail) {
        return getOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, normalizedEmail)
                .last("LIMIT 1"));
    }

    @Override
    public LoginResponse loginWithoutPassword(User user) {
        ThrowUtils.throwIf(user == null, ResponseStatus.USER_NOT_FOUND);
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BusinessException(ResponseStatus.USER_ACCOUNT_DISABLED);
        }
        return buildLoginResponse(user);
    }

    @Override
    public boolean exists(String email) {
        return this.exists(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
    }

    private LoginResponse buildLoginResponse(User user) {
        String token = issueToken(user);
        loginTokenCache.remember(user.getId(), token);
        return LoginResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .build();
    }

    private String issueToken(User user) {
        long exp = Instant.now().getEpochSecond() + jwtProperties.getExpireHours() * 3600L;
        Map<String, Object> payload = new HashMap<>(4);
        payload.put("userId", user.getId());
        payload.put("username", user.getUsername());
        payload.put("exp", exp);
        return JWTUtil.createToken(payload, jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

}
