package io.runtime.sdk.error;

import java.util.Map;
import java.util.Objects;

/**
 * Base unchecked exception for stable Runtime Shell capability failures.
 *
 * <p>The exception carries only a stable error code and approved safe
 * parameters. Trace identifiers are attached by Runtime error boundaries, not
 * by domain or capability code.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public abstract class BrixException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, String> safeParameters;

    /**
     * Creates a stable Brix exception.
     *
     * @param errorCode stable error code
     * @param safeParameters non-sensitive parameters safe for external envelopes
     * @param cause internal cause retained for logs and diagnostics
     */
    protected BrixException(
            ErrorCode errorCode,
            Map<String, String> safeParameters,
            Throwable cause) {
        super(Objects.requireNonNull(errorCode, "errorCode is required").wireCode(), cause);
        this.errorCode = errorCode;
        this.safeParameters = Map.copyOf(safeParameters == null ? Map.of() : safeParameters);
    }

    /**
     * Returns the stable error code.
     *
     * @return stable error code
     */
    public final ErrorCode errorCode() {
        return errorCode;
    }

    /**
     * Returns non-sensitive parameters safe for external reporting.
     *
     * @return immutable safe parameter map
     */
    public final Map<String, String> safeParameters() {
        return safeParameters;
    }
}
