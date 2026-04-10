package com.prodigal.travel.service.auth;

import com.prodigal.travel.controller.vo.LoginResponse;

/**
 * 认证编排：登录验证码、密码/验证码登录、退出与注销。
 *
 * <p>实际签发 JWT 与 Redis 白名单由 {@link UserService} 完成。
 */
public interface UserAuthService {

    /**
     * 向已注册邮箱发送登录用 6 位验证码；Redis 记录验证码 TTL 与发送间隔，发信失败会回滚相关键。
     */
    String sendEmailCode(String email);

    /**
     * 委托 {@link UserService#login(String, String)}：账号或邮箱 + 密码。
     */
    LoginResponse login(String account, String rawPassword);

    /**
     * 校验 Redis 中验证码后 {@link UserService#loginWithoutPassword}；成功后删除验证码键。
     */
    LoginResponse loginByEmailCode(String email, String code);

    /**
     * 解析 Bearer 后 {@link com.prodigal.travel.security.LoginTokenCache#revoke(String)}；前端应同步清除本地 token。
     */
    void logout(String authorizationHeader);

    /**
     * 校验 token、删用户成功后 {@link com.prodigal.travel.security.LoginTokenCache#revokeAllForUser(Long)}。
     */
    void deregister(String authorizationHeader);
}
