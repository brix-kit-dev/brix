package io.brix.platform.starter.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS 配置属
 * 
 * <p>跨域资源共享配置，用于配置允许的源、方法、头部等</p>
 * 
 * <p>配置示例</p>
 * <pre>
 * shinwa:
 *   cors:
 *     enabled: true
 *     allowed-origins:
 *       - http://localhost:3000
 *       - http://localhost:5173
 *     allowed-methods:
 *       - GET
 *       - POST
 *       - PUT
 *       - DELETE
 *     allowed-headers:
 *       - "*"
 *     allow-credentials: true
 *     max-age: 3600
 * </pre>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 */
@ConfigurationProperties(prefix = "shinwa.cors")
public class CorsProperties {
    
    /**
     * 是否启用 CORS
     * 
     * <p>默认值：true</p>
     */
    private boolean enabled = true;
    
    /**
     * 允许的源
     * 
     * <p>可以是具体的域名或 "*" 表示允许所</p>
     * <p>注意：使credentials 时不能使"*"</p>
     * 
     * <p>默认值：["*"]</p>
     */
    private List<String> allowedOrigins = new ArrayList<>(List.of("*"));
    
    /**
     * 允许的源模式
     * 
     * <p>支持通配符模式，"http://*.example.com"</p>
     */
    private List<String> allowedOriginPatterns = new ArrayList<>();
    
    /**
     * 允许HTTP 方法
     * 
     * <p>默认值：GET, POST, PUT, DELETE, OPTIONS, HEAD, PATCH</p>
     */
    private List<String> allowedMethods = new ArrayList<>(List.of(
        "GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD", "PATCH"
    ));
    
    /**
     * 允许的请求头
     * 
     * <p>"*" 表示允许所</p>
     * 
     * <p>默认值：["*"]</p>
     */
    private List<String> allowedHeaders = new ArrayList<>(List.of("*"));
    
    /**
     * 暴露给客户端的响应头
     * 
     * <p>客户端可以访问这些响应头</p>
     */
    private List<String> exposedHeaders = new ArrayList<>();
    
    /**
     * 是否允许携带凭证
     * 
     * <p>如果true，allowedOrigins 不能"*"</p>
     * 
     * <p>默认值：true</p>
     */
    private boolean allowCredentials = true;
    
    /**
     * 预检请求缓存时间（秒
     * 
     * <p>浏览器缓存预检请求结果的时</p>
     * 
     * <p>默认值：3600小时</p>
     */
    private long maxAge = 3600;
    
    /**
     * CORS 过滤器应用的路径模式
     * 
     * <p>默认值：/**</p>
     */
    private String pathPattern = "/**";
    
    // ===== Getters and Setters =====
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }
    
    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }
    
    public List<String> getAllowedOriginPatterns() {
        return allowedOriginPatterns;
    }
    
    public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
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
    
    public String getPathPattern() {
        return pathPattern;
    }
    
    public void setPathPattern(String pathPattern) {
        this.pathPattern = pathPattern;
    }
}
