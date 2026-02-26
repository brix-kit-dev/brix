package io.brix.platform.gateway.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

/**
 * API Key 认证配置属
 * <p>
 * 用于配置网关API Key/Secret 认证机制
 * 支持多组 API Key，便于插件、前端、移动端分别使用不同密钥
 * </p>
 * <p>
 * 配置示例（application.yml）：
 * <pre>
 * gateway:
 *   security:
 *     api-key:
 *       enabled: true
 *       header-name: X-API-Key
 *       keys:
 *         - name: frontend-web
 *           key: ${FRONTEND_API_KEY}
 *           secret: ${FRONTEND_API_SECRET}
 *         - name: plugin-engine
 *           key: ${ENGINE_API_KEY}
 *           secret: ${ENGINE_API_SECRET}
 *       exclude-paths:
 *         - /actuator/health
 *         - /actuator/health/**
 * </pre>
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@ConfigurationProperties(prefix = "gateway.security.api-key")
public class ApiKeyAuthProperties {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyAuthProperties.class);

    /**
     * 是否启用 API Key 认证
     */
    private boolean enabled = true;

    /**
     * API Key 请求头名
     */
    private String headerName = "X-API-Key";

    /**
     * API Secret 请求头名
     */
    private String secretHeaderName = "X-API-Secret";

    /**
     * 配置API Key 列表
     */
    private List<ApiKeyEntry> keys = new ArrayList<>();

    /**
     * 排除认证的路径（支持 Ant 风格通配符）
     */
    private List<String> excludePaths = new ArrayList<>();

    /**
     * 是否在生产环境强制启用认
     */
    private boolean enforceInProduction = true;

    /**
     * 认证失败时是否记录详细日志（生产环境建议 false
     */
    private boolean logAuthFailureDetails = false;

    @PostConstruct
    public void validate() {
        if (!enabled) {
            logger.warn("[shinwa] 鈿狅笍 API Key authentication is DISABLED. " +
                    "This is NOT recommended for production environments!");
            return;
        }

        if (keys.isEmpty()) {
            logger.error("[shinwa] API Key authentication is enabled but no keys configured! " +
                    "Please configure at least one API key via environment variables.");
            throw new IllegalStateException(
                    "API Key authentication enabled but no keys configured. " +
                    "Set gateway.security.api-key.keys or disable authentication.");
        }

        // 验证每个 Key 配置
        Set<String> keySet = new java.util.HashSet<>();
        for (ApiKeyEntry entry : keys) {
            if (!StringUtils.hasText(entry.getKey())) {
                throw new IllegalStateException(
                        "API Key entry '" + entry.getName() + "' has empty key value. " +
                        "Ensure environment variable is set.");
            }
            if (!StringUtils.hasText(entry.getSecret())) {
                throw new IllegalStateException(
                        "API Key entry '" + entry.getName() + "' has empty secret value. " +
                        "Ensure environment variable is set.");
            }
            // 检Key 唯一
            if (!keySet.add(entry.getKey())) {
                throw new IllegalStateException(
                        "Duplicate API Key detected for entry: " + entry.getName());
            }
            // 检Key 长度（最小安全长度）
            if (entry.getKey().length() < 16) {
                logger.warn("[shinwa] 鈿狅笍 API Key for '{}' is shorter than recommended 16 characters", 
                        entry.getName());
            }
            if (entry.getSecret().length() < 32) {
                logger.warn("[shinwa] 鈿狅笍 API Secret for '{}' is shorter than recommended 32 characters", 
                        entry.getName());
            }
        }

        logger.info("[shinwa] API Key authentication configured with {} key(s), {} excluded path(s)",
                keys.size(), excludePaths.size());
    }

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public String getSecretHeaderName() {
        return secretHeaderName;
    }

    public void setSecretHeaderName(String secretHeaderName) {
        this.secretHeaderName = secretHeaderName;
    }

    public List<ApiKeyEntry> getKeys() {
        return keys;
    }

    public void setKeys(List<ApiKeyEntry> keys) {
        this.keys = keys;
    }

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths;
    }

    public boolean isEnforceInProduction() {
        return enforceInProduction;
    }

    public void setEnforceInProduction(boolean enforceInProduction) {
        this.enforceInProduction = enforceInProduction;
    }

    public boolean isLogAuthFailureDetails() {
        return logAuthFailureDetails;
    }

    public void setLogAuthFailureDetails(boolean logAuthFailureDetails) {
        this.logAuthFailureDetails = logAuthFailureDetails;
    }

    /**
     * API Key 配置条目
     */
    public static class ApiKeyEntry {
        /**
         * Key 名称标识（用于日志审计）
         */
        private String name;

        /**
         * API Key 
         */
        private String key;

        /**
         * API Secret 
         */
        private String secret;

        /**
         * 允许访问的路径模式（为空则允许所有）
         */
        private List<String> allowedPaths = new ArrayList<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public List<String> getAllowedPaths() {
            return allowedPaths;
        }

        public void setAllowedPaths(List<String> allowedPaths) {
            this.allowedPaths = allowedPaths;
        }
    }
}
