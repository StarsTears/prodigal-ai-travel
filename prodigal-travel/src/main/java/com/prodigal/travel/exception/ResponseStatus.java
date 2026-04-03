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
    SUCCESS(0, "操作成功"),
    PARAMS_ERROR(40000, "参数错误"),
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
