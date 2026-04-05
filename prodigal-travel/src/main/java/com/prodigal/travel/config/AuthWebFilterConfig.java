package com.prodigal.travel.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigal.travel.config.properties.JwtProperties;
import com.prodigal.travel.security.JwtAuthenticationFilter;
import com.prodigal.travel.security.LoginTokenCache;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * 注册 {@link JwtAuthenticationFilter}，仅匹配 {@code /travel/*}，优先级最高。
 *
 * <p>注意：{@code /auth/*} 不受该过滤器保护，登录接口无需带 token。
 */
@Configuration
public class AuthWebFilterConfig {

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtProperties jwtProperties, LoginTokenCache loginTokenCache, ObjectMapper objectMapper) {
        return new JwtAuthenticationFilter(jwtProperties, loginTokenCache, objectMapper);
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtAuthenticationFilterRegistration(
            JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> bean = new FilterRegistrationBean<>(filter);
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        bean.addUrlPatterns("/travel/*");
        return bean;
    }
}
