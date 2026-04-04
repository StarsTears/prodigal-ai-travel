package com.prodigal.travel.exception;

import lombok.Data;
import lombok.Getter;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 响应枚举类
 * @since 2026/3/31
 */
@Getter
public enum ResponseStatus {
    SUCCESS(200, "操作成功"),
    PARAMS_ERROR(40000, "参数错误"),
    EMAIL_SEND_ERROR(40001, "验证码错误或已过期"),
    EMAIL_SEND_TOO_FREQUENT(40002, "验证码发送过于频繁，请稍后再试"),
    EMAIL_CODE_INVALID(40003, "验证码错误或已过期"),
    USER_EMAIL_EXISTS(40004, "该邮箱已注册"),
    USER_USERNAME_EXISTS(40005, "用户名已被占用"),
    USER_ACCOUNT_DISABLED(40006, "账号已禁用"),
    USER_NOT_FOUND(40400, "用户不存在"),
    USER_PASSWORD_ERROR(40100, "用户名或密码错误"),
    USER_NOT_LOGIN(40200, "用户未登录"),
    USER_NOT_AUTHORIZED(40300, "用户未授权"),
    USER_NOT_PERMISSION(40500, "用户无权限"),
    USER_NOT_ROLE(40600, "用户未分配角色"),
    SYSTEM_ERROR(50000, "系统错误"),
    OPERATION_ERROR(50100, "操作失败"),
    NOT_FOUND_ERROR(50200, "未找到该数据"),

    ;
    /**
     * 状态码
     */
    private final int code;
    private final String message;
    ResponseStatus(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
