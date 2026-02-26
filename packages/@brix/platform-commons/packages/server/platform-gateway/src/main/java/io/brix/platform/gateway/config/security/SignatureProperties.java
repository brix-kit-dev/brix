package io.brix.platform.gateway.config.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 请求签名配置属
 * 
 * <p>P105 任务：请求签+ IP 白名
 * 
 * <p>配置 HMAC-SHA256 请求签名校验的相关参
 * 
 * <p>配置示例
 * <pre>
 * gateway:
 *   signature:
 *     enabled: true
 *     secret-key: your-secret-key
 *     timestamp-tolerance-seconds: 300
 *     protected-paths:
 *       - /open-api/**
 * </pre>
 *
 * @author Brix Platform Authors Platform
 * @version 1.0.0
 * @since 2025-12-13
 */
@Component
@ConfigurationProperties(prefix = "gateway.signature")
public class SignatureProperties {

    /**
     * 是否启用签名校验
     */
    private boolean enabled = true;

    /**
     * 签名密钥（HMAC-SHA256
     * 生产环境必须通过环境变量配置
     */
    private String secretKey = "shinwa-default-signature-key-change-in-production";

    /**
     * 时间戳容忍时间（秒）
     * 超过此时间的请求视为重放攻击
     */
    private int timestampToleranceSeconds = 300;

    /**
     * 需要签名校验的路径
     */
    private List<String> protectedPaths = new ArrayList<>(List.of("/open-api/**"));

    /**
     * 签名请求头名
     */
    private String signatureHeader = "X-Signature";

    /**
     * 时间戳请求头名称
     */
    private String timestampHeader = "X-Timestamp";

    /**
     * Nonce 请求头名
     */
    private String nonceHeader = "X-Nonce";

    /**
     * 签名算法
     */
    private String algorithm = "HmacSHA256";

    // ==================== Getters and Setters ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public int getTimestampToleranceSeconds() {
        return timestampToleranceSeconds;
    }

    public void setTimestampToleranceSeconds(int timestampToleranceSeconds) {
        this.timestampToleranceSeconds = timestampToleranceSeconds;
    }

    public List<String> getProtectedPaths() {
        return protectedPaths;
    }

    public void setProtectedPaths(List<String> protectedPaths) {
        this.protectedPaths = protectedPaths;
    }

    public String getSignatureHeader() {
        return signatureHeader;
    }

    public void setSignatureHeader(String signatureHeader) {
        this.signatureHeader = signatureHeader;
    }

    public String getTimestampHeader() {
        return timestampHeader;
    }

    public void setTimestampHeader(String timestampHeader) {
        this.timestampHeader = timestampHeader;
    }

    public String getNonceHeader() {
        return nonceHeader;
    }

    public void setNonceHeader(String nonceHeader) {
        this.nonceHeader = nonceHeader;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }
}
