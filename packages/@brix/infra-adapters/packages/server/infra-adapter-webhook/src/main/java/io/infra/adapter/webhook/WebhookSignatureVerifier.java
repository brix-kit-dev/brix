/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.infra.adapter.webhook;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/**
 * Webhook Signature Verifier
 * 
 * <p>Provides HMAC-SHA256 signature generation and verification for ensuring Webhook request security.</p>
 * 
 * <h2>Signature Algorithm</h2>
 * <pre>
 * signature = HMAC-SHA256(secret, timestamp + "." + payload)
 * </pre>
 * 
 * <h2>Security Features</h2>
 * <ul>
 *   <li>HMAC-SHA256 signature algorithm</li>
 *   <li>Timestamp-based replay attack prevention (default 5-minute validity)</li>
 *   <li>Constant-time comparison to prevent timing attacks</li>
 * </ul>
 * 
 * <h2>Request Header Format</h2>
 * <pre>
 * X-Webhook-Signature: t=1234567890,v1=abc123...
 * </pre>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * WebhookSignatureVerifier verifier = new WebhookSignatureVerifier("your-secret-key");
 * 
 * // Generate signature
 * String signature = verifier.sign(payload, timestamp);
 * 
 * // Verify signature
 * boolean valid = verifier.verify(payload, signature, timestamp);
 * }</pre>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
public final class WebhookSignatureVerifier {
    
    /**
     * Signature algorithm name
     */
    private static final String ALGORITHM = "HmacSHA256";
    
    /**
     * Signature header name
     */
    public static final String SIGNATURE_HEADER = "X-Webhook-Signature";
    
    /**
     * Timestamp header name
     */
    public static final String TIMESTAMP_HEADER = "X-Webhook-Timestamp";
    
    /**
     * Signature version prefix
     */
    private static final String SIGNATURE_VERSION = "v1";
    
    /**
     * Default timestamp validity period (5 minutes)
     */
    private static final long DEFAULT_TOLERANCE_SECONDS = 300;
    
    /**
     * Signing secret
     */
    private final String secret;
    
    /**
     * Timestamp tolerance (seconds)
     */
    private final long toleranceSeconds;
    
    /**
     * Creates signature verifier
     *
     * @param secret Signing secret (cannot be null)
     * @throws NullPointerException If secret is null
     */
    public WebhookSignatureVerifier(String secret) {
        this(secret, DEFAULT_TOLERANCE_SECONDS);
    }
    
    /**
     * Creates signature verifier
     *
     * @param secret Signing secret (cannot be null)
     * @param toleranceSeconds Timestamp tolerance (seconds)
     * @throws NullPointerException If secret is null
     */
    public WebhookSignatureVerifier(String secret, long toleranceSeconds) {
        this.secret = Objects.requireNonNull(secret, "Signing secret cannot be null");
        this.toleranceSeconds = toleranceSeconds > 0 ? toleranceSeconds : DEFAULT_TOLERANCE_SECONDS;
    }
    
    /**
     * Generates Webhook signature
     * 
     * <p>Signature format: t={timestamp},v1={signature}</p>
     *
     * @param payload Request body content
     * @param timestamp Unix timestamp (seconds)
     * @return Signature string
     */
    public String sign(String payload, long timestamp) {
        String signatureData = timestamp + "." + payload;
        String signature = computeHmacSha256(signatureData);
        return String.format("t=%d,%s=%s", timestamp, SIGNATURE_VERSION, signature);
    }
    
    /**
     * Generates signature with current timestamp
     *
     * @param payload Request body content
     * @return Signature string
     */
    public String sign(String payload) {
        return sign(payload, Instant.now().getEpochSecond());
    }
    
    /**
     * Verifies Webhook signature
     * 
     * <p>Verification steps:</p>
     * <ol>
     *   <li>Parse signature header, extract timestamp and signature</li>
     *   <li>Verify timestamp is within validity period</li>
     *   <li>Recompute signature and compare</li>
     * </ol>
     *
     * @param payload Request body content
     * @param signatureHeader Signature header content
     * @return Whether verification passed
     */
    public boolean verify(String payload, String signatureHeader) {
        if (payload == null || signatureHeader == null) {
            return false;
        }
        
        try {
            // Parse signature header
            SignatureComponents components = parseSignatureHeader(signatureHeader);
            if (components == null) {
                return false;
            }
            
            // Verify timestamp
            long currentTime = Instant.now().getEpochSecond();
            if (Math.abs(currentTime - components.timestamp) > toleranceSeconds) {
                return false;
            }
            
            // Compute expected signature
            String signatureData = components.timestamp + "." + payload;
            String expectedSignature = computeHmacSha256(signatureData);
            
            // Constant-time comparison to prevent timing attacks
            return constantTimeEquals(expectedSignature, components.signature);
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Verifies signature (with specified timestamp)
     *
     * @param payload Request body content
     * @param signature Signature value
     * @param timestamp Unix timestamp (seconds)
     * @return Whether verification passed
     */
    public boolean verify(String payload, String signature, long timestamp) {
        if (payload == null || signature == null) {
            return false;
        }
        
        // Verify timestamp
        long currentTime = Instant.now().getEpochSecond();
        if (Math.abs(currentTime - timestamp) > toleranceSeconds) {
            return false;
        }
        
        // Compute expected signature
        String signatureData = timestamp + "." + payload;
        String expectedSignature = computeHmacSha256(signatureData);
        
        // Constant-time comparison
        return constantTimeEquals(expectedSignature, signature);
    }
    
    /**
     * Computes HMAC-SHA256 signature
     *
     * @param data Data to sign
     * @return Base64 encoded signature
     */
    private String computeHmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    ALGORITHM
            );
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to compute HMAC-SHA256 signature", e);
        }
    }
    
    /**
     * Parses signature header
     * 
     * <p>Signature header format: t={timestamp},v1={signature}</p>
     *
     * @param header Signature header content
     * @return Signature components, returns null if parsing fails
     */
    private SignatureComponents parseSignatureHeader(String header) {
        if (header == null || header.isEmpty()) {
            return null;
        }
        
        Long timestamp = null;
        String signature = null;
        
        String[] parts = header.split(",");
        for (String part : parts) {
            String[] keyValue = part.split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }
            
            String key = keyValue[0].trim();
            String value = keyValue[1].trim();
            
            if ("t".equals(key)) {
                try {
                    timestamp = Long.parseLong(value);
                } catch (NumberFormatException e) {
                    return null;
                }
            } else if (SIGNATURE_VERSION.equals(key)) {
                signature = value;
            }
        }
        
        if (timestamp == null || signature == null) {
            return null;
        }
        
        return new SignatureComponents(timestamp, signature);
    }
    
    /**
     * Constant-time string comparison
     * 
     * <p>Prevents timing attacks - execution time is the same regardless of comparison result</p>
     *
     * @param a String A
     * @param b String B
     * @return Whether equal
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        
        return MessageDigest.isEqual(aBytes, bBytes);
    }
    
    /**
     * Signature components inner class
     */
    private static final class SignatureComponents {
        final long timestamp;
        final String signature;
        
        SignatureComponents(long timestamp, String signature) {
            this.timestamp = timestamp;
            this.signature = signature;
        }
    }
    
    /**
     * Gets signature header name
     *
     * @return Signature header name
     */
    public static String getSignatureHeaderName() {
        return SIGNATURE_HEADER;
    }
    
    /**
     * Gets timestamp header name
     *
     * @return Timestamp header name
     */
    public static String getTimestampHeaderName() {
        return TIMESTAMP_HEADER;
    }
}
