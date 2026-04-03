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
@Schema(description = "贵州旅游助手对话请求")
public class ChatRequest implements Serializable {
    @Schema(description = "用户问题或需求描述", example = "帮我规划五天行程，想去黄果树和西江")
    @NotBlank
    private String message;

    @Schema(description = "对话id", example = "0eb394f9-d60b-47ad-b0b5-e231153fc48c")
    private String chatId;
}
