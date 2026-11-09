package com.prodigal.travel.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 重命名会话请求
 * @since 2026/11/9
 */
@Data
@Schema(description = "重命名会话请求")
public class ConversationRenameRequest implements Serializable {

    @Schema(description = "会话id", example = "0eb394f9-d60b-47ad-b0b5-e231153fc48c")
    @NotBlank(message = "会话ID不能为空")
    private String chatId;

    @Schema(description = "新的会话标题", example = "三亚五日游规划")
    @NotBlank(message = "会话标题不能为空")
    private String title;
}
