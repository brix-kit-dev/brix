package io.runtime.sdk.capability;

import io.runtime.sdk.annotation.Since;

/**
 * Capability contract for encrypting server-side authentication secrets.
 *
 * <p>Callers pass plaintext secrets only in memory. Implementations return an
 * opaque encrypted value suitable for persistence and later decryption by the
 * same capability. The contract does not prescribe key storage, algorithm, or
 * KMS integration.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@Since("3.2.0")
public interface SecretEncryptionCapability {

    /**
     * Encrypts a plaintext secret for storage.
     *
     * @param plaintextSecret secret value to encrypt
     * @return opaque encrypted value
     */
    String encryptSecret(String plaintextSecret);

    /**
     * Decrypts a previously encrypted secret.
     *
     * @param encryptedSecret opaque encrypted value returned by {@link #encryptSecret(String)}
     * @return plaintext secret
     */
    String decryptSecret(String encryptedSecret);
}