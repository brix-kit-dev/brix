package io.brix.platform.auth.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 配置属
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 */
@ConfigurationProperties(prefix = "platform.security.jwt")
public class JwtProperties {

    /** 是否启用 JWT 验证 */
    private boolean enabled = true;

    /** RS256 公钥路径 */
    private String publicKeyPath = "classpath:keys/public.pem";

    /** JWT 签发*/
    private String issuer = "shinwa-auth-center";

    /** JWT 鍙椾紬 */
    private String audience = "shinwa-platform-api";

    /** 时钟偏差容忍时间（秒*/
    private int clockSkewSeconds = 60;

    // ========== Getters and Setters ==========

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPublicKeyPath() {
        return publicKeyPath;
    }

    public void setPublicKeyPath(String publicKeyPath) {
        this.publicKeyPath = publicKeyPath;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public int getClockSkewSeconds() {
        return clockSkewSeconds;
    }

    public void setClockSkewSeconds(int clockSkewSeconds) {
        this.clockSkewSeconds = clockSkewSeconds;
    }
}
