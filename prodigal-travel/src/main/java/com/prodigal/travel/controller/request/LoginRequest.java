package com.prodigal.travel.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "账号/邮箱 + 密码登录")
public class LoginRequest {

    @NotBlank
    @Size(max = 128)
    @Schema(description = "账号：用户名，或完整邮箱")
    private String account;

    @NotBlank
    @Size(max = 64)
    private String password;
}
