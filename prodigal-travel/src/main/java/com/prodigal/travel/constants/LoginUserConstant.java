package com.prodigal.travel.constants;

import com.prodigal.travel.security.JwtAuthenticationFilter;

/**
 * 登录态在请求中的属性名（由 {@link JwtAuthenticationFilter} 写入）
 */
public class LoginUserConstant {

    public static final String REQUEST_ATTR_USER_ID = "X-User-Id";
}
