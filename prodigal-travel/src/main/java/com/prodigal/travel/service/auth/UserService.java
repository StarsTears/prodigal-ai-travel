package com.prodigal.travel.service.auth;

import com.baomidou.mybatisplus.extension.service.IService;
import com.prodigal.travel.controller.vo.LoginResponse;
import com.prodigal.travel.model.entity.User;

/**
 * 用户表持久化与<strong>登录产物</strong>（签发 JWT + 写入 {@link com.prodigal.travel.security.LoginTokenCache}）。
 *
 * @author 35104
 */
public interface UserService extends IService<User> {

    /**
     * 注册入库；调用方负责邮箱验证码等业务校验；邮箱建议已规范化（小写）。
     */
    boolean register(String email, String username, String rawPassword, String nickname);

    /**
     * 用户名或邮箱 + 密码：查库 → BCrypt 校验 → 签发 JWT 并登记 Redis 白名单。
     */
    LoginResponse login(String account, String rawPassword);

    /**
     * 按规范化邮箱查一条用户；逻辑删除行由 MyBatis-Plus 过滤。
     */
    User findByEmail(String normalizedEmail);

    /**
     * 免密签发会话：在身份已由其它方式确认后调用（如邮箱验证码校验通过），仍会做状态校验并登记白名单。
     */
    LoginResponse loginWithoutPassword(User user);

    /**
     * 是否存在该邮箱用户（用于发登录验证码前校验）。
     */
    boolean exists(String email);
}
