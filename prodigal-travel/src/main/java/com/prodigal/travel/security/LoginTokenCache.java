package com.prodigal.travel.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.prodigal.travel.config.properties.JwtProperties;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 已签发且未退出的 JWT 白名单。退出/注销时移除；请求鉴权时须命中缓存。
 */
@Component
public class LoginTokenCache {

    private final Cache<String, Boolean> activeTokens;
    private final ConcurrentHashMap<String, Long> tokenToUserId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Set<String>> userIdToTokens = new ConcurrentHashMap<>();

    public LoginTokenCache(JwtProperties jwtProperties) {
        this.activeTokens = Caffeine.newBuilder()
                .maximumSize(500_000)
                .expireAfterWrite(jwtProperties.getExpireHours(), TimeUnit.HOURS)
                .removalListener((String token, Boolean val, RemovalCause cause) -> {
                    if (token == null) {
                        return;
                    }
                    Long uid = tokenToUserId.remove(token);
                    if (uid == null) {
                        return;
                    }
                    Set<String> set = userIdToTokens.get(uid);
                    if (set != null) {
                        set.remove(token);
                        if (set.isEmpty()) {
                            userIdToTokens.remove(uid);
                        }
                    }
                })
                .build();
    }

    public void remember(Long userId, String token) {
        activeTokens.put(token, Boolean.TRUE);
        tokenToUserId.put(token, userId);
        userIdToTokens.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(token);
    }

    public void revoke(String token) {
        activeTokens.invalidate(token);
    }

    public void revokeAllForUser(Long userId) {
        Set<String> tokens = userIdToTokens.remove(userId);
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        for (String t : tokens) {
            activeTokens.invalidate(t);
        }
    }

    public boolean isActive(String token) {
        return activeTokens.getIfPresent(token) != null;
    }
}
