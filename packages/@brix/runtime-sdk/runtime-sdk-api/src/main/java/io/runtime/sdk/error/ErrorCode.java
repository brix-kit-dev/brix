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

    /**
     * Returns what kind of failure this is, which the runtime answers with its protocol's status (standard-link plan
     * L7.3). A code that does not say is {@link ErrorCategory#INTERNAL}: declare the category of every code a caller
     * can cause.
     *
     * @return the category
     * @since 3.3.0
     */
    default ErrorCategory category() {
        return ErrorCategory.INTERNAL;
    }
}
