package com.prodigal.travel.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 邮箱注册验证码相关配置
 */
@Data
@ConfigurationProperties(prefix = "prodigal.auth.register")
public class AuthRegisterProperties {

    /**
     * 验证码有效期（分钟）
     */
    private int codeTtlMinutes = 10;

    /**
     * 同一邮箱两次发送最小间隔（秒）
     */
    private int sendIntervalSeconds = 60;
}
