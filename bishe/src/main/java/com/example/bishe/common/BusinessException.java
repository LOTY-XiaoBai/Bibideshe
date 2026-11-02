package com.example.bishe.common;

/**
 * 业务异常, 用于在 Service 层抛出可预期的错误(如账号已存在、密码错误等)。
 * 默认 HTTP/业务码为 400; 鉴权类可传 401/403, 资源不存在可传 404。
 * 可选携带 data, 供前端展示结构化信息(如永久封禁弹窗)。
 */
public class BusinessException extends RuntimeException {

    private final int code;
    private final Object data;

    public BusinessException(String message) {
        this(400, message, null);
    }

    public BusinessException(int code, String message) {
        this(code, message, null);
    }

    public BusinessException(int code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public int getCode() {
        return code;
    }

    public Object getData() {
        return data;
    }
}
