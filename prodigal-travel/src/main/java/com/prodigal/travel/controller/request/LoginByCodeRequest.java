package com.prodigal.travel.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "邮箱 + 验证码登录（邮箱须已注册）")
public class LoginByCodeRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email
    private String email;

    @NotBlank
    @Size(min = 4, max = 12)
    @Schema(description = "邮箱收到的 6 位数字验证码")
    private String code;
}
