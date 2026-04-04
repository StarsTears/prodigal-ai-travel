package com.prodigal.travel.security;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigal.travel.common.BaseResult;
import com.prodigal.travel.common.ResultUtils;
import com.prodigal.travel.config.properties.JwtProperties;
import com.prodigal.travel.constants.LoginUserConstant;
import com.prodigal.travel.exception.ResponseStatus;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 校验 {@code Authorization: Bearer &lt;jwt&gt;}，通过后将用户主键写入 request 属性。
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final JwtProperties jwtProperties;
    private final LoginTokenCache loginTokenCache;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || !path.startsWith("/travel/")) {
            return true;
        }
        // CORS 预检不带 Authorization，需放行
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String auth = request.getHeader("Authorization");
        if (!StringUtils.hasText(auth) || !auth.regionMatches(true, 0, PREFIX, 0, PREFIX.length())) {
            writeUnauthorized(response, "请先登录，并在请求头携带 Authorization: Bearer &lt;token&gt;");
            return;
        }

        String token = auth.substring(PREFIX.length()).trim();
        if (!StringUtils.hasText(token)) {
            writeUnauthorized(response, "Token 不能为空");
            return;
        }

        byte[] key = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (!JWTUtil.verify(token, key)) {
            writeUnauthorized(response, "Token 无效或已过期");
            return;
        }

        if (!loginTokenCache.isActive(token)) {
            writeUnauthorized(response, "登录已失效，请重新登录");
            return;
        }

        JWT jwt = JWTUtil.parseToken(token);
        Long userId = jwt.getPayloads().getLong("userId");
        if (userId == null || userId <= 0) {
            writeUnauthorized(response, "Token 载荷无效");
            return;
        }

        request.setAttribute(LoginUserConstant.REQUEST_ATTR_USER_ID, userId);
        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        BaseResult<?> body = ResultUtils.error(ResponseStatus.USER_NOT_LOGIN.getCode(), msg);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
