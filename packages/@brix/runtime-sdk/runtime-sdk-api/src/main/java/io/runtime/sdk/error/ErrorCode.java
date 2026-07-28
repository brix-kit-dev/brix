package io.runtime.sdk.error;

/**
 * Stable machine-readable error code exposed across Runtime Shell boundaries.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public interface ErrorCode {

    /**
     * Returns the stable wire code for external error envelopes.
     *
     * @return a stable wire code such as {@code notification.request.invalid}
     */
    String wireCode();
}
