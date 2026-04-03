package com.prodigal.travel.common;

import com.prodigal.travel.exception.ResponseStatus;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 响应工具类
 * @since 2026/3/31
 */
public class ResultUtils {
    public static <T> BaseResult<T> success(T data) {
        return new BaseResult<T>(0, true, "success", data);
    }
    public static  BaseResult<?> error(ResponseStatus status) {
        return new BaseResult<>(status);
    }
    public static <T> BaseResult<T> error(int code,String msg) {
        return new BaseResult<>(code, false, msg, null);
    }
}
