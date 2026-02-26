package io.brix.platform.gateway.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.annotation.PostConstruct;

/**
 * 日志脱敏配置
 * <p>
 * 用于配置网关日志中需要脱敏的敏感字段，防止敏感信息泄露到日志中
 * </p>
 * <p>
 * 配置示例（application.yml）：
 * <pre>
 * gateway:
 *   security:
 *     log-sanitization:
 *       enabled: true
 *       sensitive-headers:
 *         - Authorization
 *         - X-API-Key
 *         - X-API-Secret
 *         - Cookie
 *       sensitive-patterns:
 *         - "Bearer\\s+[A-Za-z0-9-_.]+"
 *         - "password\\s*[=:]\\s*\\S+"
 *       mask-char: "*"
 *       visible-chars: 4
 * </pre>
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@ConfigurationProperties(prefix = "gateway.security.log-sanitization")
public class LogSanitizationProperties {

    private static final Logger logger = LoggerFactory.getLogger(LogSanitizationProperties.class);

    /**
     * MVP 红线要求脱敏的默认头
     */
    private static final List<String> DEFAULT_SENSITIVE_HEADERS = List.of(
            "authorization",
            "x-api-key",
            "x-api-secret",
            "cookie",
            "set-cookie",
            "x-auth-token",
            "x-access-token",
            "x-refresh-token",
            "proxy-authorization"
    );

    /**
     * 是否启用日志脱敏
     */
    private boolean enabled = true;

    /**
     * 需要脱敏的请求头列表（不区分大小写
     */
    private List<String> sensitiveHeaders = new ArrayList<>(DEFAULT_SENSITIVE_HEADERS);

    /**
     * 需要脱敏的正则表达式模
     */
    private List<String> sensitivePatterns = new ArrayList<>();

    /**
     * 脱敏使用的掩码字
     */
    private String maskChar = "*";

    /**
     * 保留可见的字符数（前后各保留
     */
    private int visibleChars = 4;

    /**
     * 完全掩盖的最小长度（短于此长度则完全掩盖
     */
    private int fullMaskThreshold = 8;

    /**
     * 编译后的正则表达式模
     */
    private List<Pattern> compiledPatterns;

    @PostConstruct
    public void init() {
        if (!enabled) {
            logger.warn("[shinwa] 鈿狅笍 Log sanitization is DISABLED. " +
                    "Sensitive data may be exposed in logs!");
            return;
        }

        // 将所有头转换为小
        sensitiveHeaders = sensitiveHeaders.stream()
                .map(String::toLowerCase)
                .distinct()
                .collect(Collectors.toList());

        // 添加默认的敏感模
        if (sensitivePatterns.isEmpty()) {
            sensitivePatterns = new ArrayList<>(List.of(
                    // Bearer token
                    "Bearer\\s+[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_.+/=]*",
                    // Basic auth
                    "Basic\\s+[A-Za-z0-9+/=]+",
                    // Generic token patterns
                    "token\\s*[=:]\\s*[\"']?[A-Za-z0-9-_]+[\"']?",
                    // Password patterns
                    "password\\s*[=:]\\s*[\"']?[^\\s\"'&]+[\"']?",
                    // API Key patterns
                    "api[_-]?key\\s*[=:]\\s*[\"']?[A-Za-z0-9-_]+[\"']?",
                    // Secret patterns
                    "secret\\s*[=:]\\s*[\"']?[A-Za-z0-9-_]+[\"']?"
            ));
        }

        // 编译正则表达
        compiledPatterns = sensitivePatterns.stream()
                .map(pattern -> Pattern.compile(pattern, Pattern.CASE_INSENSITIVE))
                .collect(Collectors.toList());

        logger.info("[shinwa] Log sanitization enabled for {} header(s) and {} pattern(s)",
                sensitiveHeaders.size(), compiledPatterns.size());
    }

    /**
     * 检查指定的头是否需要脱
     *
     * @param headerName 请求头名
     * @return true 如果需要脱
     */
    public boolean isSensitiveHeader(String headerName) {
        if (!enabled || headerName == null) {
            return false;
        }
        return sensitiveHeaders.contains(headerName.toLowerCase());
    }

    /**
     * 获取所有敏感头名称集合（小写）
     */
    public Set<String> getSensitiveHeadersAsSet() {
        return Set.copyOf(sensitiveHeaders);
    }

    /**
     * 获取编译后的正则表达式模式列
     */
    public List<Pattern> getCompiledPatterns() {
        return compiledPatterns != null ? compiledPatterns : List.of();
    }

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getSensitiveHeaders() {
        return sensitiveHeaders;
    }

    public void setSensitiveHeaders(List<String> sensitiveHeaders) {
        this.sensitiveHeaders = sensitiveHeaders;
    }

    public List<String> getSensitivePatterns() {
        return sensitivePatterns;
    }

    public void setSensitivePatterns(List<String> sensitivePatterns) {
        this.sensitivePatterns = sensitivePatterns;
    }

    public String getMaskChar() {
        return maskChar;
    }

    public void setMaskChar(String maskChar) {
        this.maskChar = maskChar;
    }

    public int getVisibleChars() {
        return visibleChars;
    }

    public void setVisibleChars(int visibleChars) {
        this.visibleChars = visibleChars;
    }

    public int getFullMaskThreshold() {
        return fullMaskThreshold;
    }

    public void setFullMaskThreshold(int fullMaskThreshold) {
        this.fullMaskThreshold = fullMaskThreshold;
    }
}
