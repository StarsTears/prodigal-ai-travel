package com.prodigal.travel.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 大模型调用异常告警配置。
 */
@Data
@ConfigurationProperties(prefix = "prodigal.ai.alert")
public class AiAlertProperties {

    /**
     * 是否启用告警。
     */
    private boolean enabled = false;

    /**
     * 告警接收邮箱列表。
     */
    private List<String> recipients = new ArrayList<>();

    /**
     * 相同错误最小告警间隔（分钟）。
     */
    private int cooldownMinutes = 30;
}
