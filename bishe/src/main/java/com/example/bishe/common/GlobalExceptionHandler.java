package com.example.bishe.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理, 将业务异常统一转换为 Result 结构返回。
 * HTTP 状态码由 {@link ResultHttpStatusAdvice} 根据 Result.code 同步设置。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public org.springframework.http.ResponseEntity<Result<Void>> handleMissingResource() {
        return org.springframework.http.ResponseEntity.status(404).body(Result.error(404, "资源不存在"));
    }

    @ExceptionHandler(BusinessException.class)
    @SuppressWarnings("unchecked")
    public Result<Object> handleBusinessException(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage(), e.getData());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        String errorId = java.util.UUID.randomUUID().toString();
        log.error("未处理异常 errorId={}", errorId, e);
        return Result.error(500, "服务器内部错误，请稍后重试（错误编号：" + errorId + "）");
    }
}
