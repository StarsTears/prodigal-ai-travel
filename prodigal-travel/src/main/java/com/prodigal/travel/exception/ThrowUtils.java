package com.prodigal.travel.exception;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 异常工具类
 * @since 2026/3/31
 */
public class ThrowUtils {
    /**
     * 条件成立则抛异常
     *
     * @param condition        条件
     * @param runtimeException 异常
     */
    public static void throwIf(boolean condition, RuntimeException runtimeException) {
        if (condition) {
            throw runtimeException;
        }
    }

    /**
     * 条件成立则抛异常
     *
     * @param condition 条件
     * @param status 错误码
     */
    public static void throwIf(boolean condition, ResponseStatus status) {
        throwIf(condition, new BusinessException(status));
    }

}
