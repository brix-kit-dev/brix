package io.brix.platform.common.exception;

import java.io.Serial;

/**
 * 租户未找到异
 * 
 * <p>当租户上下文未初始化或租户不存在时抛出
 * 
 * @author Brix Platform Authors Platform Team
 * @since 1.0.0
 */
public class TenantNotFoundException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public TenantNotFoundException(String message) {
        super(message);
    }

    public TenantNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
