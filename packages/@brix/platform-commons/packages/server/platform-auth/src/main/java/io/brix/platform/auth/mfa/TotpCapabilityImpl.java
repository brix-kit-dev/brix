package io.brix.platform.auth.mfa;

import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import io.runtime.sdk.capability.TotpCapability;

/**
 * RFC 6238 TOTP implementation using HMAC-SHA1, six digits, and 30 second steps.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public class TotpCapabilityImpl implements TotpCapability {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final int SECRET_BYTES = 20;

    private final MfaProperties properties;
    private final Clock clock;
    private final SecureRandom secureRandom;

    public TotpCapabilityImpl(MfaProperties properties) {
        this(properties, Clock.systemUTC(), new SecureRandom());
    }

    TotpCapabilityImpl(MfaProperties properties, Clock clock, SecureRandom secureRandom) {
        this.properties = properties;
        this.clock = clock;
        this.secureRandom = secureRandom;
    }

    @Override
    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return base32Encode(bytes);
    }

    @Override
    public String buildOtpauthUri(String accountName, String secret) {
        if (accountName == null || accountName.isBlank()) {
            throw new IllegalArgumentException("accountName is required");
        }
        byte[] decoded = base32Decode(secret);
        if (decoded.length == 0) {
            throw new IllegalArgumentException("secret is required");
        }
        String issuer = trimToDefault(properties.getIssuer(), "Brix");
        String label = encode(issuer + ":" + accountName.trim());
        return "otpauth://totp/" + label
                + "?secret=" + encode(normalizeSecret(secret))
                + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1"
                + "&digits=" + properties.getTotpDigits()
                + "&period=" + properties.getTotpPeriodSeconds();
    }

    @Override
    public boolean validateCode(String secret, String code) {
        if (code == null || !code.matches("\\d{" + properties.getTotpDigits() + "}")) {
            return false;
        }
        Instant now = clock.instant();
        int window = Math.max(0, properties.getTotpWindow());
        for (int offset = -window; offset <= window; offset++) {
            String expected = generateCode(secret, now.plusSeconds((long) offset * periodSeconds()),
                    properties.getTotpDigits());
            if (constantTimeEquals(expected, code)) {
                return true;
            }
        }
        return false;
    }

    String generateCode(String secret, Instant instant, int digits) {
        if (digits < 6 || digits > 8) {
            throw new IllegalArgumentException("digits must be between 6 and 8");
        }
        byte[] key = base32Decode(secret);
        long counter = instant.getEpochSecond() / periodSeconds();
        byte[] counterBytes = ByteBuffer.allocate(Long.BYTES).putLong(counter).array();
        byte[] hmac = hmacSha1(key, counterBytes);
        int offset = hmac[hmac.length - 1] & 0x0f;
        int binary = ((hmac[offset] & 0x7f) << 24)
                | ((hmac[offset + 1] & 0xff) << 16)
                | ((hmac[offset + 2] & 0xff) << 8)
                | (hmac[offset + 3] & 0xff);
        int modulus = (int) Math.pow(10, digits);
        int otp = binary % modulus;
        return String.format(Locale.ROOT, "%0" + digits + "d", otp);
    }

    private int periodSeconds() {
        return Math.max(1, properties.getTotpPeriodSeconds());
    }

    private static byte[] hmacSha1(byte[] key, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(message);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 is unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String expected, String actual) {
        return Arrays.equals(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    private static String base32Encode(byte[] bytes) {
        StringBuilder result = new StringBuilder((bytes.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte value : bytes) {
            buffer = (buffer << 8) | (value & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                result.append(BASE32_ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 0x1f));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            result.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1f));
        }
        return result.toString();
    }

    private static byte[] base32Decode(String secret) {
        String normalized = normalizeSecret(secret);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("secret is required");
        }
        int buffer = 0;
        int bitsLeft = 0;
        byte[] output = new byte[normalized.length() * 5 / 8];
        int index = 0;
        for (int i = 0; i < normalized.length(); i++) {
            int value = BASE32_ALPHABET.indexOf(normalized.charAt(i));
            if (value < 0) {
                throw new IllegalArgumentException("secret is not valid Base32");
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                output[index++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        return index == output.length ? output : Arrays.copyOf(output, index);
    }

    private static String normalizeSecret(String secret) {
        if (secret == null) {
            return "";
        }
        return secret.replace("=", "").replace(" ", "").trim().toUpperCase(Locale.ROOT);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String trimToDefault(String value, String defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value.trim();
    }
}