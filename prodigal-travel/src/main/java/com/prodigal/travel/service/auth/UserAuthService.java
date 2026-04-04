package com.prodigal.travel.service.auth;

import com.prodigal.travel.controller.vo.LoginResponse;

/**
 * 认证编排：验证码、注册、登录
 */
public interface UserAuthService {

    String sendEmailCode(String email);

    LoginResponse login(String account, String rawPassword);

    /**
     * 邮箱 + 验证码登录（邮箱必须已注册）
     */
    LoginResponse loginByEmailCode(String email, String code);

    /**
     * 退出：从服务端 token 缓存中移除当前 JWT（前端应同时清除本地 token）
     */
    void logout(String authorizationHeader);

    /**
     * 注销：逻辑删除当前用户，并吊销该用户在本服务登记的全部 JWT
     */
    void deregister(String authorizationHeader);
}
