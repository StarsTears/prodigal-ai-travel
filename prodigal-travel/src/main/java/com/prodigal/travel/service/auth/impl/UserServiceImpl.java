package com.prodigal.travel.service.auth.impl;

import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.jwt.JWTUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.prodigal.travel.config.rabbitmq.UserRabbiMQConfig;
import com.prodigal.travel.config.properties.JwtProperties;
import com.prodigal.travel.exception.BusinessException;
import com.prodigal.travel.exception.ResponseStatus;
import com.prodigal.travel.exception.ThrowUtils;
import com.prodigal.travel.mapper.UserMapper;
import com.prodigal.travel.controller.vo.LoginResponse;
import com.prodigal.travel.model.entity.User;
import com.prodigal.travel.model.event.UserEvent;
import com.prodigal.travel.security.LoginTokenCache;
import com.prodigal.travel.service.auth.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 用户数据与登录响应构造：{@link #buildLoginResponse} 统一完成「签发 JWT + Redis remember」。
 *
 * @author 35104
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private final JwtProperties jwtProperties;
    private final LoginTokenCache loginTokenCache;
    private final RabbitTemplate rabbitTemplate;


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
        UserEvent event = UserEvent.builder()
                .id(user.getId())
                .build();
        rabbitTemplate.convertAndSend(
                UserRabbiMQConfig.USER_EXCHANGE,
                UserRabbiMQConfig.USER_REGISTERED_EMAIL_QUEUE,
                event
        );
        return Boolean.TRUE;
    }

    /**
     * 支持用户名或邮箱（邮箱按小写匹配）+ 密码；禁用账号直接拒绝。
     */
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

    /**
     * 邮箱验证码等场景：不再校验密码，仍校验账号状态，再走与密码登录相同的签发逻辑。
     */
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

    /**
     * 生成 {@link LoginResponse}：{@link #issueToken} 后必须把 token 写入白名单，否则后续 {@code /travel/*} 会 401。
     */
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

    /**
     * Hutool JWT：payload 含 {@code userId}、{@code username}、{@code exp}（秒），与 {@code prodigal.jwt} 配置一致。
     */
    private String issueToken(User user) {
        long exp = Instant.now().getEpochSecond() + jwtProperties.getExpireHours() * 3600L;
        Map<String, Object> payload = new HashMap<>(4);
        payload.put("userId", user.getId());
        payload.put("username", user.getUsername());
        payload.put("exp", exp);
        return JWTUtil.createToken(payload, jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

}
