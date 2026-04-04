package com.prodigal.travel.security;

import cn.hutool.jwt.JWTUtil;
import com.prodigal.travel.config.properties.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class JwtTokenHelper {

    private static final String PREFIX = "Bearer ";

    private final JwtProperties jwtProperties;

    public String extractBearerToken(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)
                || !authorizationHeader.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            return null;
        }
        String token = authorizationHeader.substring(PREFIX.length()).trim();
        return StringUtils.hasText(token) ? token : null;
    }

    /**
     * @return 校验通过的用户主键；否则 null
     */
    public Long parseVerifiedUserId(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        byte[] key = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (!JWTUtil.verify(token, key)) {
            return null;
        }
        Long userId = JWTUtil.parseToken(token).getPayloads().getLong("userId");
        if (userId == null || userId <= 0) {
            return null;
        }
        return userId;
    }
}
