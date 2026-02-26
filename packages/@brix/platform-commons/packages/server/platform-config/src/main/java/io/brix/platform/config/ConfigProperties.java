package io.brix.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 配置中心属性
 *
 * @since 3.0.0
 */
@ConfigurationProperties(prefix = "platform.config")
public class ConfigProperties {

    /**
     * 是否启用配置中心
     */
    private boolean enabled = true;

    /**
     * 配置刷新间隔（毫秒）
     */
    private long refreshInterval = 30000;

    /**
     * 是否启用配置加密
     */
    private boolean encryptEnabled = false;

    /**
     * 加密密钥（仅在 encryptEnabled=true 时有效）
     */
    private String encryptKey;

    // Getters and Setters

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getRefreshInterval() {
        return refreshInterval;
    }

    public void setRefreshInterval(long refreshInterval) {
        this.refreshInterval = refreshInterval;
    }

    public boolean isEncryptEnabled() {
        return encryptEnabled;
    }

    public void setEncryptEnabled(boolean encryptEnabled) {
        this.encryptEnabled = encryptEnabled;
    }

    public String getEncryptKey() {
        return encryptKey;
    }

    public void setEncryptKey(String encryptKey) {
        this.encryptKey = encryptKey;
    }
}
