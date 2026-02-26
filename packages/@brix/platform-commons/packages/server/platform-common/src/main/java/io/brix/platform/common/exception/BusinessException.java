package io.brix.platform.common.exception;

import java.io.Serial;

/**
 * 业务异常基类
 * 
 * <p>用于表示业务规则校验失败或业务逻辑错误
 * 
 * @author Brix Platform Authors Platform Team
 * @since 1.0.0
 */
public class BusinessException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final String code;

    public BusinessException(String message) {
        super(message);
        this.code = "BUSINESS_ERROR";
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
