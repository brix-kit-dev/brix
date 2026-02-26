package io.brix.platform.common.exception;

import java.io.Serial;

/**
 * <p>自定义业务异常，强制绑定 {@link PlatformErrorCode}，确保所有异常都能映射到统一响应码。</p>
 * <p>推荐在领域/应用服务层抛出，不要在基础设施层吞掉异常。</p>
 */
public class PlatformException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = -4924589044659651076L;

    private final PlatformErrorCode errorCode;

    public PlatformException(PlatformErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public PlatformException(PlatformErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public PlatformException(PlatformErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public PlatformErrorCode getErrorCode() {
        return errorCode;
    }
}
