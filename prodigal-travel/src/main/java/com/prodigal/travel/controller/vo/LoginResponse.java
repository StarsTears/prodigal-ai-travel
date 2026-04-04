package com.prodigal.travel.controller.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "登录 / 注册成功返回")
public class LoginResponse {

    @Schema(description = "JWT，前端可存 localStorage；需要鉴权的接口可带 Authorization: Bearer")
    private String token;

    @Schema(description = "用户主键；聊天等接口从 JWT 解析，无需再传 X-User-Id")
    private Long userId;

    private String username;

    private String nickname;

    private String email;
}
