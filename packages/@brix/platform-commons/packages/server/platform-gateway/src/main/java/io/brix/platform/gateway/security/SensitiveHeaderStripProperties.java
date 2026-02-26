package io.brix.platform.gateway.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;

import jakarta.annotation.PostConstruct;

/**
 * 敏感请求头剥离配
 * <p>
 * 用于配置网关需要剥离的敏感请求头，防止客户端伪造身份信息
 * </p>
 * <p>
 * 配置示例（application.yml）：
 * <pre>
 * gateway:
 *   security:
 *     header-strip:
 *       enabled: true
 *       headers:
 *         - x-user-id
 *         - x-tenant-id
 *         - x-role
 *         - x-roles
 *         - x-permissions
 *       log-stripped: true
 * </pre>
 * </p>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 */
@ConfigurationProperties(prefix = "gateway.security.header-strip")
public class SensitiveHeaderStripProperties {

    private static final Logger logger = LoggerFactory.getLogger(SensitiveHeaderStripProperties.class);

    /**
     * 默认需要剥离的敏感头（MVP 红线要求
     */
    private static final List<String> DEFAULT_SENSITIVE_HEADERS = List.of(
            "x-user-id",
            "x-tenant-id",
            "x-role",
            "x-roles",
            "x-permissions",
            "x-user-name",
            "x-internal-call"
    );

    /**
     * 是否启用敏感头剥
     */
    private boolean enabled = true;

    /**
     * 需要剥离的请求头列表（不区分大小写
     */
    private List<String> headers = new ArrayList<>(DEFAULT_SENSITIVE_HEADERS);

    /**
     * 是否记录剥离操作日志
     */
    private boolean logStripped = true;

    /**
     * 是否在日志中显示被剥离的原始值（生产环境建议 false
     */
    private boolean logStrippedValue = false;

    /**
     * 排除剥离的路径（内部服务间调用可能需要保留这些头
     */
    private List<String> excludePaths = new ArrayList<>();

    @PostConstruct
    public void init() {
        if (!enabled) {
            logger.warn("[shinwa] 鈿狅笍 Sensitive header stripping is DISABLED. " +
                    "This may allow header spoofing attacks!");
            return;
        }

        // 将所有头转换为小写以便不区分大小写匹
        headers = headers.stream()
                .map(String::toLowerCase)
                .distinct()
                .toList();

        logger.info("[shinwa] Sensitive header stripping enabled for {} header(s): {}",
                headers.size(), headers);
    }

    /**
     * 检查指定的头是否应该被剥离
     *
     * @param headerName 请求头名
     * @return true 如果应该剥离
     */
    public boolean shouldStrip(String headerName) {
        if (!enabled || headerName == null) {
            return false;
        }
        return headers.contains(headerName.toLowerCase());
    }

    /**
     * 获取所有需要剥离的头名称集合（小写
     *
     * @return 头名称集
     */
    public Set<String> getHeadersAsSet() {
        return Set.copyOf(headers);
    }

    // Getters and Setters
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getHeaders() {
        return headers;
    }

    public void setHeaders(List<String> headers) {
        this.headers = headers;
    }

    public boolean isLogStripped() {
        return logStripped;
    }

    public void setLogStripped(boolean logStripped) {
        this.logStripped = logStripped;
    }

    public boolean isLogStrippedValue() {
        return logStrippedValue;
    }

    public void setLogStrippedValue(boolean logStrippedValue) {
        this.logStrippedValue = logStrippedValue;
    }

    public List<String> getExcludePaths() {
        return excludePaths;
    }

    public void setExcludePaths(List<String> excludePaths) {
        this.excludePaths = excludePaths;
    }
}
