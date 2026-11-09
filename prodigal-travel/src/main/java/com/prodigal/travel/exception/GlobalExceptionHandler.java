package com.prodigal.travel.exception;

import com.prodigal.travel.common.BaseResult;
import com.prodigal.travel.common.ResultUtils;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

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

    /** 对象接收参数（@ModelAttribute @Valid）校验失败 */
    @ExceptionHandler(BindException.class)
    public BaseResult<?> bindExceptionHandler(BindException e) {
        String msg = extractMsg(e);
        log.warn("参数校验失败: {}", msg);
        return ResultUtils.error(ResponseStatus.PARAMS_ERROR.getCode(), msg);
    }

    /** JSON 请求体（@RequestBody @Valid）校验失败 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public BaseResult<?> methodArgumentNotValidHandler(MethodArgumentNotValidException e) {
        String msg = extractMsg(e);
        log.warn("参数校验失败: {}", msg);
        return ResultUtils.error(ResponseStatus.PARAMS_ERROR.getCode(), msg);
    }

    private static String extractMsg(BindException e) {
        return e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .filter(m -> m != null && !m.isBlank())
                .collect(Collectors.joining("；"));
    }
}
