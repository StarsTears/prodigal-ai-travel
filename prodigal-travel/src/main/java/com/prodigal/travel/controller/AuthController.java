package com.prodigal.travel.controller;

import com.prodigal.travel.common.BaseResult;
import com.prodigal.travel.common.ResultUtils;
import com.prodigal.travel.controller.request.LoginByCodeRequest;
import com.prodigal.travel.controller.request.LoginRequest;
import com.prodigal.travel.controller.request.RegisterRequest;
import com.prodigal.travel.controller.request.SendRegisterCodeRequest;
import com.prodigal.travel.controller.vo.LoginResponse;
import com.prodigal.travel.service.UserService;
import com.prodigal.travel.service.auth.UserAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证 HTTP 入口（context-path 下一般为 {@code /api/auth/**}）。
 *
 * <p>登录成功返回的 token 需由前端在访问 {@code /travel/**} 时通过 {@code Authorization: Bearer} 携带；
 * 该路径由 {@link com.prodigal.travel.security.JwtAuthenticationFilter} 鉴权。流程说明见
 * {@code com.prodigal.travel.security} 包的 {@code package-info.java}。
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "auth", description = "注册；登录；退出（吊销 token 缓存）；注销（逻辑删用户）")
public class AuthController {

    private final UserAuthService userAuthService;

    private final UserService userService;

    @Operation(summary = "发送验证码", description = "向邮箱发送 6 位数字验证码，同一邮箱有发送间隔限制")
    @PostMapping("/send-code")
    public BaseResult<String> sendEmailCode(@Valid @RequestBody SendRegisterCodeRequest request) {
        String result = userAuthService.sendEmailCode(request.getEmail());
        return ResultUtils.success(result);
    }

    @Operation(summary = "注册", description = "校验邮箱验证码后创建账号")
    @PostMapping("/register")
    public BaseResult<String> register(@Valid @RequestBody RegisterRequest request) {
        userService.register(
                request.getEmail(),
                request.getUsername(),
                request.getPassword(),
                request.getNickname());
        return ResultUtils.success("register success");
    }

    @Operation(summary = "登录（账号/邮箱 + 密码）")
    @PostMapping("/login")
    public BaseResult<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResultUtils.success(userAuthService.login(request.getAccount(), request.getPassword()));
    }

    @Operation(summary = "登录（邮箱 + 验证码）", description = "邮箱必须先注册；验证码通过「发送登录验证码」获取")
    @PostMapping("/login/by-code")
    public BaseResult<LoginResponse> loginByCode(@Valid @RequestBody LoginByCodeRequest request) {
        return ResultUtils.success(userAuthService.loginByEmailCode(request.getEmail(), request.getCode()));
    }

    @Operation(summary = "退出登录", description = "从服务端移除当前 JWT 白名单记录；请同时清除前端本地 token")
    @PostMapping("/logout")
    public BaseResult<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        userAuthService.logout(authorization);
        return ResultUtils.success(null);
    }

    @Operation(summary = "注销账号", description = "逻辑删除当前用户，并吊销其在服务端登记的全部 JWT；需携带有效 Bearer token")
    @PostMapping("/deregister")
    public BaseResult<Void> deregister(@RequestHeader("Authorization") String authorization) {
        userAuthService.deregister(authorization);
        return ResultUtils.success(null);
    }
}
