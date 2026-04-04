package com.prodigal.travel.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 签发配置
 */
@Data
@ConfigurationProperties(prefix = "prodigal.jwt")
public class JwtProperties {

    /**
     * HS256 密钥，生产环境务必通过环境变量覆盖
     */
    private String secret = "prodigal-20260404-asdfghjkl";

    /**
     * 过期时间（小时）
     */
    private int expireHours = 10;
}
