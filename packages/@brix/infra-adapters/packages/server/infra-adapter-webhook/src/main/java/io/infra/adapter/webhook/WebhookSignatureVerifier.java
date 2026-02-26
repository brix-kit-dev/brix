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
 * Webhook 签名验证器
 * 
 * <p>提供 HMAC-SHA256 签名生成和验证功能，用于确保 Webhook 请求的安全性。</p>
 * 
 * <h2>签名算法</h2>
 * <pre>
 * signature = HMAC-SHA256(secret, timestamp + "." + payload)
 * </pre>
 * 
 * <h2>安全特性</h2>
 * <ul>
 *   <li>HMAC-SHA256 签名算法</li>
 *   <li>时间戳防重放攻击（默认 5 分钟有效期）</li>
 *   <li>常量时间比较防止时序攻击</li>
 * </ul>
 * 
 * <h2>请求头格式</h2>
 * <pre>
 * X-Webhook-Signature: t=1234567890,v1=abc123...
 * </pre>
 * 
 * <h2>使用示例</h2>
 * <pre>{@code
 * WebhookSignatureVerifier verifier = new WebhookSignatureVerifier("your-secret-key");
 * 
 * // 生成签名
 * String signature = verifier.sign(payload, timestamp);
 * 
 * // 验证签名
 * boolean valid = verifier.verify(payload, signature, timestamp);
 * }</pre>
 * 
 * @author Brix Team
 * @since 3.0.0
 */
public final class WebhookSignatureVerifier {
    
    /**
     * 签名算法名称
     */
    private static final String ALGORITHM = "HmacSHA256";
    
    /**
     * 签名头名称
     */
    public static final String SIGNATURE_HEADER = "X-Webhook-Signature";
    
    /**
     * 时间戳头名称
     */
    public static final String TIMESTAMP_HEADER = "X-Webhook-Timestamp";
    
    /**
     * 签名版本前缀
     */
    private static final String SIGNATURE_VERSION = "v1";
    
    /**
     * 默认时间戳有效期（5 分钟）
     */
    private static final long DEFAULT_TOLERANCE_SECONDS = 300;
    
    /**
     * 签名密钥
     */
    private final String secret;
    
    /**
     * 时间戳容差（秒）
     */
    private final long toleranceSeconds;
    
    /**
     * 创建签名验证器
     *
     * @param secret 签名密钥（不能为空）
     * @throws NullPointerException 如果 secret 为空
     */
    public WebhookSignatureVerifier(String secret) {
        this(secret, DEFAULT_TOLERANCE_SECONDS);
    }
    
    /**
     * 创建签名验证器
     *
     * @param secret 签名密钥（不能为空）
     * @param toleranceSeconds 时间戳容差（秒）
     * @throws NullPointerException 如果 secret 为空
     */
    public WebhookSignatureVerifier(String secret, long toleranceSeconds) {
        this.secret = Objects.requireNonNull(secret, "签名密钥不能为空");
        this.toleranceSeconds = toleranceSeconds > 0 ? toleranceSeconds : DEFAULT_TOLERANCE_SECONDS;
    }
    
    /**
     * 生成 Webhook 签名
     * 
     * <p>签名格式：t={timestamp},v1={signature}</p>
     *
     * @param payload 请求体内容
     * @param timestamp Unix 时间戳（秒）
     * @return 签名字符串
     */
    public String sign(String payload, long timestamp) {
        String signatureData = timestamp + "." + payload;
        String signature = computeHmacSha256(signatureData);
        return String.format("t=%d,%s=%s", timestamp, SIGNATURE_VERSION, signature);
    }
    
    /**
     * 使用当前时间戳生成签名
     *
     * @param payload 请求体内容
     * @return 签名字符串
     */
    public String sign(String payload) {
        return sign(payload, Instant.now().getEpochSecond());
    }
    
    /**
     * 验证 Webhook 签名
     * 
     * <p>验证步骤：</p>
     * <ol>
     *   <li>解析签名头，提取时间戳和签名</li>
     *   <li>验证时间戳是否在有效期内</li>
     *   <li>重新计算签名并比较</li>
     * </ol>
     *
     * @param payload 请求体内容
     * @param signatureHeader 签名头内容
     * @return 是否验证通过
     */
    public boolean verify(String payload, String signatureHeader) {
        if (payload == null || signatureHeader == null) {
            return false;
        }
        
        try {
            // 解析签名头
            SignatureComponents components = parseSignatureHeader(signatureHeader);
            if (components == null) {
                return false;
            }
            
            // 验证时间戳
            long currentTime = Instant.now().getEpochSecond();
            if (Math.abs(currentTime - components.timestamp) > toleranceSeconds) {
                return false;
            }
            
            // 计算预期签名
            String signatureData = components.timestamp + "." + payload;
            String expectedSignature = computeHmacSha256(signatureData);
            
            // 常量时间比较，防止时序攻击
            return constantTimeEquals(expectedSignature, components.signature);
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 验证签名（带指定时间戳）
     *
     * @param payload 请求体内容
     * @param signature 签名值
     * @param timestamp Unix 时间戳（秒）
     * @return 是否验证通过
     */
    public boolean verify(String payload, String signature, long timestamp) {
        if (payload == null || signature == null) {
            return false;
        }
        
        // 验证时间戳
        long currentTime = Instant.now().getEpochSecond();
        if (Math.abs(currentTime - timestamp) > toleranceSeconds) {
            return false;
        }
        
        // 计算预期签名
        String signatureData = timestamp + "." + payload;
        String expectedSignature = computeHmacSha256(signatureData);
        
        // 常量时间比较
        return constantTimeEquals(expectedSignature, signature);
    }
    
    /**
     * 计算 HMAC-SHA256 签名
     *
     * @param data 待签名数据
     * @return Base64 编码的签名
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
            throw new RuntimeException("计算 HMAC-SHA256 签名失败", e);
        }
    }
    
    /**
     * 解析签名头
     * 
     * <p>签名头格式：t={timestamp},v1={signature}</p>
     *
     * @param header 签名头内容
     * @return 签名组件，解析失败返回 null
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
     * 常量时间字符串比较
     * 
     * <p>防止时序攻击，无论比较结果如何，执行时间都相同</p>
     *
     * @param a 字符串 A
     * @param b 字符串 B
     * @return 是否相等
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
     * 签名组件内部类
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
     * 获取签名头名称
     *
     * @return 签名头名称
     */
    public static String getSignatureHeaderName() {
        return SIGNATURE_HEADER;
    }
    
    /**
     * 获取时间戳头名称
     *
     * @return 时间戳头名称
     */
    public static String getTimestampHeaderName() {
        return TIMESTAMP_HEADER;
    }
}
