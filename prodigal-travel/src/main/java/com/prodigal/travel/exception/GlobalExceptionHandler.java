package com.prodigal.travel.exception;

import com.prodigal.travel.common.BaseResult;
import com.prodigal.travel.common.ResultUtils;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 全局异常处理
 * @since 2026/3/31
 */
@Slf4j
@Hidden
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException.class)
    public BaseResult<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        return ResultUtils.error(e.getCode(),e.getMessage());
    }
    @ExceptionHandler(RuntimeException.class)
    public BaseResult<?> RuntimeExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtils.error(ResponseStatus.SYSTEM_ERROR);
    }
}
