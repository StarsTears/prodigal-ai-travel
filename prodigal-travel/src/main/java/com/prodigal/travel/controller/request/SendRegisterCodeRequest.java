package com.prodigal.travel.controller.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "发送注册验证码")
public class SendRegisterCodeRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email
    private String email;
}
