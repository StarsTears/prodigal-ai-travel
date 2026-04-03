package com.prodigal.travel.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "贵州旅游助手对话请求")
public class TravelChatRequest {

    @Schema(description = "用户问题或需求描述", example = "帮我规划五天行程，想去黄果树和西江")
    private String message;

    @Schema(description = "用户出发城市（可选，便于智能规划大交通与首末日节奏）", example = "上海")
    private String departureCity;

    @Schema(description = "计划在贵州游玩的天数（可选）", example = "5")
    private Integer tripDays;

    @Schema(description = "意向景点或区域关键词（可选），如：梵净山、荔波", example = "黄果树")
    private String tripFocus;
}
