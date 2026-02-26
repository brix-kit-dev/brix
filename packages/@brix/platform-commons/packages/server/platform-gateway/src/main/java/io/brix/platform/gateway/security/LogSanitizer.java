package io.brix.platform.gateway.security;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日志脱敏服务
 * <p>
 * 提供统一的日志脱敏能力，用于对敏感信息进行掩码处理
 * 支持请求头脱敏和文本内容脱敏两种模式
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@Component
public class LogSanitizer {

    private final LogSanitizationProperties properties;

    public LogSanitizer(LogSanitizationProperties properties) {
        this.properties = properties;
    }

    /**
     * 对请求头值进行脱
     *
     * @param headerName  请求头名
     * @param headerValue 请求头
     * @return 脱敏后的
     */
    public String sanitizeHeader(String headerName, String headerValue) {
        if (!properties.isEnabled() || headerValue == null) {
            return headerValue;
        }

        if (properties.isSensitiveHeader(headerName)) {
            return maskValue(headerValue);
        }

        return headerValue;
    }

    /**
     * 对文本内容进行脱
     * 使用配置的正则表达式模式匹配并脱敏敏感内
     *
     * @param text 原始文本
     * @return 脱敏后的文本
     */
    public String sanitizeText(String text) {
        if (!properties.isEnabled() || text == null) {
            return text;
        }

        String result = text;
        List<Pattern> patterns = properties.getCompiledPatterns();

        for (Pattern pattern : patterns) {
            result = sanitizeWithPattern(result, pattern);
        }

        return result;
    }

    /**
     * 使用正则表达式模式进行脱
     */
    private String sanitizeWithPattern(String text, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        while (matcher.find()) {
            // 添加匹配前的文本
            result.append(text, lastEnd, matcher.start());
            // 对匹配的内容进行脱敏
            String matched = matcher.group();
            result.append(maskValue(matched));
            lastEnd = matcher.end();
        }

        // 添加剩余文本
        result.append(text.substring(lastEnd));
        return result.toString();
    }

    /**
     * 对值进行掩码处
     *
     * @param value 原始
     * @return 脱敏后的
     */
    public String maskValue(String value) {
        if (value == null) {
            return null;
        }

        int length = value.length();
        String maskChar = properties.getMaskChar();
        int visibleChars = properties.getVisibleChars();
        int fullMaskThreshold = properties.getFullMaskThreshold();

        // 短值完全掩
        if (length <= fullMaskThreshold) {
            return maskChar.repeat(Math.min(8, length));
        }

        // 保留首尾可见字符
        int actualVisible = Math.min(visibleChars, length / 4);
        String prefix = value.substring(0, actualVisible);
        String suffix = value.substring(length - actualVisible);
        int maskLength = Math.min(8, length - 2 * actualVisible);

        return prefix + maskChar.repeat(maskLength) + suffix;
    }

    /**
     * 创建用于日志输出的安全请求头摘要
     *
     * @param headerName  请求头名
     * @param headerValue 请求头
     * @return 格式化的头信息（已脱敏）
     */
    public String formatHeader(String headerName, String headerValue) {
        String sanitizedValue = sanitizeHeader(headerName, headerValue);
        return headerName + ": " + sanitizedValue;
    }

    /**
     * 检查是否启用了脱敏
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * Authorization 头进行特殊处
     * 保留认证类型（Bearer/Basic），脱敏凭证部分
     *
     * @param authValue Authorization 头的
     * @return 脱敏后的
     */
    public String sanitizeAuthorizationHeader(String authValue) {
        if (authValue == null) {
            return null;
        }

        // 处理 Bearer token
        if (authValue.toLowerCase().startsWith("bearer ")) {
            String token = authValue.substring(7);
            return "Bearer " + maskValue(token);
        }

        // 处理 Basic auth
        if (authValue.toLowerCase().startsWith("basic ")) {
            return "Basic " + maskChar(8);
        }

        // 其他类型直接脱敏
        return maskValue(authValue);
    }

    private String maskChar(int count) {
        return properties.getMaskChar().repeat(count);
    }
}
