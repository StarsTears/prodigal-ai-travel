package com.prodigal.travel.security;

import com.prodigal.travel.config.properties.JwtProperties;
import com.prodigal.travel.constants.CacheKeyConstant;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * 已签发且仍有效的 JWT 服务端白名单（Redis）。
 *
 * <p>与 JWT 的 {@code exp} 配合：token 键带 TTL（与 {@code prodigal.jwt.expire-hours} 一致）；
 * 用户维度维护 token 集合，便于注销时一次性吊销。
 */
@Component
public class LoginTokenCache {
    private final StringRedisTemplate redis;
    private final Duration tokenTtl;

    public LoginTokenCache(StringRedisTemplate redisTemplate, JwtProperties jwtProperties) {
        this.redis = redisTemplate;
        this.tokenTtl = Duration.ofHours(jwtProperties.getExpireHours());
    }

    /**
     * 登录成功签发 JWT 后调用：登记 token → 用户 id，并把 token 加入该用户的集合。
     */
    public void remember(Long userId, String token) {
        String tokenKey = CacheKeyConstant.TOKEN_KEY_PREFIX + token;
        String userKey = CacheKeyConstant.USER_TOKENS_KEY_PREFIX + userId;
        redis.opsForValue().set(tokenKey, String.valueOf(userId), tokenTtl);
        redis.opsForSet().add(userKey, token);
    }

    /**
     * 退出登录：移除单个 token 及其在用户集合中的引用。
     */
    public void revoke(String token) {
        String tokenKey = CacheKeyConstant.TOKEN_KEY_PREFIX + token;
        String userIdStr = redis.opsForValue().get(tokenKey);
        redis.delete(tokenKey);
        if (userIdStr != null) {
            redis.opsForSet().remove(CacheKeyConstant.USER_TOKENS_KEY_PREFIX + userIdStr, token);
        }
    }

    public void revokeAllForUser(Long userId) {
        String userKey = CacheKeyConstant.USER_TOKENS_KEY_PREFIX + userId;
        Set<String> tokens = redis.opsForSet().members(userKey);
        redis.delete(userKey);
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        for (String t : tokens) {
            redis.delete(CacheKeyConstant.TOKEN_KEY_PREFIX + t);
        }
    }

    /**
     * 请求鉴权用：Redis 中仍存在该 token 键则视为会话未退出且未因 TTL 过期清理。
     */
    public boolean isActive(String token) {
        return Boolean.TRUE.equals(redis.hasKey(CacheKeyConstant.TOKEN_KEY_PREFIX + token));
    }
}
