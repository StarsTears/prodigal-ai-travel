package com.prodigal.travel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.prodigal.travel.controller.vo.LoginResponse;
import com.prodigal.travel.model.entity.User;

/**
* @author 35104
* @description 针对表【user(用户表)】的数据库操作Service
* @createDate 2026-04-04 13:02:34
*/
public interface UserService extends IService<User> {

    /**
     * 创建用户（调用方已校验邮箱验证码等业务）；邮箱统一小写入库
     */
    boolean register(String email, String username, String rawPassword, String nickname);

    /**
     * 使用用户名或邮箱 + 密码登录
     */
    LoginResponse login(String account, String rawPassword);

    /**
     * 按邮箱查询用户（逻辑删除由 MyBatis-Plus 自动过滤）
     */
    User findByEmail(String normalizedEmail);

    /**
     * 免密登录：账号已确认有效且状态正常时签发会话（如邮箱验证码已通过）
     */
    LoginResponse loginWithoutPassword(User user);

    boolean exists(String email);
}
