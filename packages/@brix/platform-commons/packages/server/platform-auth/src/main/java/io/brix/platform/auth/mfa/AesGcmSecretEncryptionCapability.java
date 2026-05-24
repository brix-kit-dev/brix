package io.brix.platform.auth.mfa;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import io.runtime.sdk.capability.SecretEncryptionCapability;

/**
 * AES-GCM implementation of {@link SecretEncryptionCapability}.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public class AesGcmSecretEncryptionCapability implements SecretEncryptionCapability {

    private static final String PREFIX = "v1";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom;

    public AesGcmSecretEncryptionCapability(String configuredKey) {
        this(configuredKey, new SecureRandom());
    }

    AesGcmSecretEncryptionCapability(String configuredKey, SecureRandom secureRandom) {
        this.keySpec = new SecretKeySpec(resolveKey(configuredKey), "AES");
        this.secureRandom = secureRandom;
    }

    @Override
    public String encryptSecret(String plaintextSecret) {
        if (plaintextSecret == null || plaintextSecret.isBlank()) {
            throw new IllegalArgumentException("plaintextSecret is required");
        }
        byte[] iv = new byte[IV_BYTES];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintextSecret.getBytes(StandardCharsets.UTF_8));
            return PREFIX + "."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(iv)
                    + "."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to encrypt secret", e);
        }
    }

    @Override
    public String decryptSecret(String encryptedSecret) {
        if (encryptedSecret == null || encryptedSecret.isBlank()) {
            throw new IllegalArgumentException("encryptedSecret is required");
        }
        String[] parts = encryptedSecret.split("\\.");
        if (parts.length != 3 || !PREFIX.equals(parts[0])) {
            throw new IllegalArgumentException("encryptedSecret format is unsupported");
        }
        byte[] iv = Base64.getUrlDecoder().decode(parts[1]);
        byte[] ciphertext = Base64.getUrlDecoder().decode(parts[2]);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to decrypt secret", e);
        }
    }

    private static byte[] resolveKey(String configuredKey) {
        if (configuredKey == null || configuredKey.isBlank()) {
            throw new IllegalStateException("brix.platform.mfa.encryption-key is required");
        }
        String trimmed = configuredKey.trim();
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(trimmed);
        } catch (IllegalArgumentException ex) {
            decoded = trimmed.getBytes(StandardCharsets.UTF_8);
        }
        if (decoded.length == 16 || decoded.length == 24 || decoded.length == 32) {
            return Arrays.copyOf(decoded, decoded.length);
        }
        throw new IllegalStateException("brix.platform.mfa.encryption-key must decode to 16, 24, or 32 bytes");
    }
}