package io.runtime.sdk.capability;

import java.util.OptionalLong;

import io.runtime.sdk.annotation.Since;

/**
 * Time-based one-time password capability contract.
 *
 * <p>Implementations generate RFC 6238 compatible secrets, build Google
 * Authenticator compatible otpauth URIs, and validate six-digit TOTP codes.
 * The contract is intentionally free of Spring, persistence, or QR-code
 * dependencies so hosts can replace the implementation without affecting
 * platform-admin services.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@Since("3.2.0")
public interface TotpCapability {

    /**
     * Generates a Base32 encoded shared secret suitable for Google Authenticator.
     *
     * @return Base32 secret without padding
     */
    String generateSecret();

    /**
     * Builds an otpauth URI accepted by Google Authenticator compatible apps.
     *
     * @param accountName account label, usually the login email
     * @param secret Base32 encoded shared secret
     * @return otpauth URI containing issuer, account label, and secret
     */
    String buildOtpauthUri(String accountName, String secret);

    /**
     * Validates a user supplied six-digit TOTP code.
     *
     * @param secret Base32 encoded shared secret
     * @param code user supplied six-digit code
     * @return {@code true} when the code is valid within the configured window
     */
    boolean validateCode(String secret, String code);

    /**
     * Returns the RFC 6238 time step a user supplied code matches within the configured
     * window. Verifiers use the step to reject a second use of the same code (RFC 6238
     * §5.2): a code is accepted only when its step is later than the last accepted step.
     *
     * <p>Implementations that cannot report the step keep this default and fail closed.</p>
     *
     * @param secret Base32 encoded shared secret
     * @param code user supplied code
     * @return the matched time step, or empty when the code is invalid
     * @since 3.2.0
     */
    default OptionalLong matchTimeStep(String secret, String code) {
        return OptionalLong.empty();
    }
}