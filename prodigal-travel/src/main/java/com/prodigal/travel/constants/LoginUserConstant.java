package com.prodigal.travel.constants;

import com.prodigal.travel.security.JwtAuthenticationFilter;

/**
 * 登录态在 {@link jakarta.servlet.http.HttpServletRequest} 中的属性名。
 *
 * <p>仅当请求已通过 {@link com.prodigal.travel.security.JwtAuthenticationFilter} 时，Controller 可通过
 * {@link org.springframework.web.bind.annotation.RequestAttribute} 注入当前用户主键。
 */
public interface LoginUserConstant {

    /** 当前登录用户 id（{@code Long}） */
    String REQUEST_ATTR_USER_ID = "X-User-Id";
}
