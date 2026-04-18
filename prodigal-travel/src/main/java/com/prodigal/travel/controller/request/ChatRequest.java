package com.prodigal.travel.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 聊天请求类
 * @since 2026/4/1
 */
@Data
@Schema(description = "旅游助手对话请求")
public class ChatRequest implements Serializable {
    @Schema(description = "用户问题或需求描述", example = "帮我规划五天行程，想去黄果树和西江")
    @NotBlank
    private String message;

    @Schema(description = "对话id", example = "0eb394f9-d60b-47ad-b0b5-e231153fc48c")
    private String chatId;

    @Schema(description = "浏览器 Geolocation 纬度（十进制度），与 longitude 同时有值时优先用于本地天气")
    private Double latitude;

    @Schema(description = "浏览器 Geolocation 经度（十进制度）")
    private Double longitude;

    @Schema(
            description = "浏览器定位状态：granted=已取到坐标；denied=用户拒绝；unsupported=不支持；timeout=超时；none=未尝试或未就绪",
            example = "granted"
    )
    private String browserGeolocationStatus;
}
