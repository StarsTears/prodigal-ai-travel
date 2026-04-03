package com.prodigal.travel.exception;

import lombok.Getter;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 业务异常
 * @since 2026/3/31
 */
@Getter
public class BusinessException extends RuntimeException{
    /**
     * 错误码
     */
    private final int code;

    public BusinessException(int code, String msg) {
        super(msg);
        this.code = code;
    }
    public BusinessException(ResponseStatus status) {
        super(status.getMessage());
        this.code = status.getCode();
    }
    public BusinessException(ResponseStatus status, String msg) {
        super(msg);
        this.code = status.getCode();
    }
}
