package io.brix.platform.gateway.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * CORS 跨域配置属
 * <p>
 * application.yml 中读CORS 配置，支持运行时配置而无需修改代码
 * 支持从环境变{@code GATEWAY_ALLOWED_ORIGINS} 读取逗号分隔的域名白名单
 * </p>
 * 
 * <h3>配置示例</h3>
 * <pre>
 * # 方式一：YAML 列表配置
 * gateway:
 *   cors:
 *     allowed-origin-patterns:
 *       - "https://www.your-domain.com"
 *       - "https://*.your-domain.com"
 * 
 * # 方式二：环境变量配置（逗号分隔
 * GATEWAY_ALLOWED_ORIGINS=https://www.example.com,https://*.example.com
 * 
 * # 方式三：YAML 中引用环境变
 * gateway:
 *   cors:
 *     allowed-origin-patterns: ${GATEWAY_ALLOWED_ORIGINS:*}
 * </pre>
 * 
 * <h3>安全建议</h3>
 * <ul>
 *   <li>生产环境必须配置具体的域名白名单，禁止使"*"</li>
 *   <li>启用 warn-on-wildcard 可在启动时检测不安全配置</li>
 *   <li>设置 block-wildcard-in-production=true 可阻止生产环境使用通配</li>
 *   <li>根据实际需求限allowed-methods allowed-headers</li>
 * </ul>
 *
 * @author Brix Platform Authors
 * @version 1.0.2
 * @see CorsConfig CORS 过滤器配置类
 */
@ConfigurationProperties(prefix = "gateway.cors")
public class CorsProperties {
    
    /**
     * 允许的来源模式列
     * <p>
     * 支持通配符模式，例如
     * <ul>
     *   <li>"https://www.example.com" - 精确匹配</li>
     *   <li>"https://*.example.com" - 匹配所有子域名</li>
     *   <li>"*" - 允许所有来源（⚠️ 仅限开发环境）</li>
     * </ul>
     * </p>
     * <p>
     * 支持两种配置方式
     * <ol>
     *   <li>YAML 列表格式</li>
     *   <li>逗号分隔的字符串（用于环境变量，GATEWAY_ALLOWED_ORIGINS</li>
     * </ol>
     * </p>
     */
    private List<String> allowedOriginPatterns = List.of("*");
    
    /**
     * 原始的允许来源配置字符串
     * <p>
     * 内部使用，用于处理从环境变量传入的逗号分隔格式
     * </p>
     */
    private String allowedOriginPatternsRaw;
    
    /**
     * 允许HTTP 方法列表
     * <p>
     * 常用值：GET, POST, PUT, DELETE, PATCH, OPTIONS, HEAD
     * </p>
     */
    private List<String> allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS");
    
    /**
     * 允许的请求头列表
     * <p>
     * 使用 "*" 允许所有请求头，或指定具体的请求头名称
     * 常见请求头：Content-Type, Authorization, X-Requested-With
     * </p>
     */
    private List<String> allowedHeaders = List.of("*");
    
    /**
     * 暴露给客户端的响应头列表
     * <p>
     * 默认情况下，浏览器只能访问以下响应头
     * Cache-Control, Content-Language, Content-Type, Expires, Last-Modified, Pragma
     * 如需访问其他响应头，需要在此配置
     * </p>
     */
    private List<String> exposedHeaders = List.of();
    
    /**
     * 是否允许携带凭证（Cookie、Authorization 等）
     * <p>
     * 设置true 时，allowedOriginPatterns 不能使用 "*"（浏览器安全限制）
     * </p>
     */
    private boolean allowCredentials = true;
    
    /**
     * 预检请求（OPTIONS）的缓存时间，单位为
     * <p>
     * 在此时间内，浏览器不会重复发送预检请求
     * 建议值：3600小时
     * </p>
     */
    private long maxAge = 3600L;
    
    /**
     * 是否在启动时对通配符配置发出警
     * <p>
     * 启用后，如果 allowedOriginPatterns 包含 "*"，将在启动日志中输出安全警告
     * 生产环境建议保持启用
     * </p>
     */
    private boolean warnOnWildcard = true;
    
    /**
     * 是否在生产环境阻止通配符配
     * <p>
     * 启用后，如果当前环境为生产环境且 allowedOriginPatterns 包含 "*"
     * 将抛出异常阻止应用启动
     * </p>
     */
    private boolean blockWildcardInProduction = false;

    // ========== Getters and Setters ==========
    
    /**
     * 获取允许的来源模式列
     * <p>
     * 如果配置了逗号分隔的字符串（如环境变量），会自动解析为列表
     * </p>
     * 
     * @return 解析后的允许来源列表
     */
    public List<String> getAllowedOriginPatterns() {
        // 如果存在原始字符串配置，解析为列
        if (StringUtils.hasText(allowedOriginPatternsRaw)) {
            return parseCommaSeparatedOrigins(allowedOriginPatternsRaw);
        }
        return allowedOriginPatterns;
    }

    /**
     * 设置允许的来源模式列
     * 
     * @param allowedOriginPatterns 来源模式列表
     */
    public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
        // 检查是否为单元素列表且包含逗号（从环境变量解析的情况）
        if (allowedOriginPatterns != null && allowedOriginPatterns.size() == 1) {
            String singleValue = allowedOriginPatterns.get(0);
            if (singleValue != null && singleValue.contains(",")) {
                // 这是逗号分隔的环境变量
                this.allowedOriginPatterns = parseCommaSeparatedOrigins(singleValue);
                return;
            }
        }
        this.allowedOriginPatterns = allowedOriginPatterns;
    }
    
    /**
     * 设置原始的来源配置字符串
     * <p>
     * 用于直接从环境变量接收逗号分隔的
     * </p>
     * 
     * @param allowedOriginPatternsRaw 逗号分隔的来源字符串
     */
    public void setAllowedOriginPatternsRaw(String allowedOriginPatternsRaw) {
        this.allowedOriginPatternsRaw = allowedOriginPatternsRaw;
    }
    
    /**
     * 解析逗号分隔的来源字符串
     * 
     * @param origins 逗号分隔的来源字符串
     * @return 解析后的来源列表
     */
    private List<String> parseCommaSeparatedOrigins(String origins) {
        if (!StringUtils.hasText(origins)) {
            return List.of("*");
        }
        
        List<String> result = new ArrayList<>();
        String[] parts = origins.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (StringUtils.hasText(trimmed)) {
                result.add(trimmed);
            }
        }
        
        return result.isEmpty() ? List.of("*") : result;
    }

    public List<String> getAllowedMethods() {
        return allowedMethods;
    }

    public void setAllowedMethods(List<String> allowedMethods) {
        this.allowedMethods = allowedMethods;
    }

    public List<String> getAllowedHeaders() {
        return allowedHeaders;
    }

    public void setAllowedHeaders(List<String> allowedHeaders) {
        this.allowedHeaders = allowedHeaders;
    }

    public List<String> getExposedHeaders() {
        return exposedHeaders;
    }

    public void setExposedHeaders(List<String> exposedHeaders) {
        this.exposedHeaders = exposedHeaders;
    }

    public boolean isAllowCredentials() {
        return allowCredentials;
    }

    public void setAllowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
    }

    public long getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(long maxAge) {
        this.maxAge = maxAge;
    }

    public boolean isWarnOnWildcard() {
        return warnOnWildcard;
    }

    public void setWarnOnWildcard(boolean warnOnWildcard) {
        this.warnOnWildcard = warnOnWildcard;
    }

    public boolean isBlockWildcardInProduction() {
        return blockWildcardInProduction;
    }

    public void setBlockWildcardInProduction(boolean blockWildcardInProduction) {
        this.blockWildcardInProduction = blockWildcardInProduction;
    }

    /**
     * 检查是否包含通配符配
     * 
     * @return 如果 allowedOriginPatterns 包含 "*" 则返回 true
     */
    public boolean hasWildcardOrigin() {
        return allowedOriginPatterns != null && allowedOriginPatterns.contains("*");
    }

    @Override
    public String toString() {
        return "CorsProperties{" +
                "allowedOriginPatterns=" + allowedOriginPatterns +
                ", allowedMethods=" + allowedMethods +
                ", allowedHeaders=" + allowedHeaders +
                ", exposedHeaders=" + exposedHeaders +
                ", allowCredentials=" + allowCredentials +
                ", maxAge=" + maxAge +
                ", warnOnWildcard=" + warnOnWildcard +
                ", blockWildcardInProduction=" + blockWildcardInProduction +
                '}';
    }
}
