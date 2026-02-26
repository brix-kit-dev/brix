package io.brix.platform.gateway.config.security;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * IP 白名单配置属
 * 
 * <p>P105 任务：请求签+ IP 白名
 * 
 * <p>配置 IP 白名单校验的相关参数
 * 
 * <p>配置示例
 * <pre>
 * gateway:
 *   ip-whitelist:
 *     enabled: true
 *     allowed-ips:
 *       - 127.0.0.1
 *       - 192.168.1.0/24
 *       - 10.0.0.0/8
 *     protected-paths:
 *       - /open-api/**
 * </pre>
 * 
 * <p>支持IP 格式
 * <ul>
 *   <li>单个 IP92.168.1.100</li>
 *   <li>CIDR 格式92.168.1.0/24</li>
 *   <li>IP 范围92.168.1.1-192.168.1.255</li>
 * </ul>
 *
 * @author Brix Platform Authors Platform
 * @version 1.0.0
 * @since 2025-12-13
 */
@Component
@ConfigurationProperties(prefix = "gateway.ip-whitelist")
public class IpWhitelistProperties {

    /**
     * 是否启用 IP 白名
     */
    private boolean enabled = true;

    /**
     * 允许IP 地址列表
     * 支持单个 IP、CIDR、IP 范围
     */
    private List<String> allowedIps = new ArrayList<>(List.of(
            "127.0.0.1",
            "0:0:0:0:0:0:0:1",  // IPv6 localhost
            "192.168.0.0/16",
            "10.0.0.0/8",
            "172.16.0.0/12"
    ));

    /**
     * 需IP 白名单校验的路径
     */
    private List<String> protectedPaths = new ArrayList<>(List.of("/open-api/**"));

    /**
     * 是否信任 X-Forwarded-For 请求
     * 在使用反向代理时设为 true
     */
    private boolean trustXForwardedFor = true;

    /**
     * 刷新间隔（秒
     * 配置变更后生效的延迟时间
     */
    private int refreshIntervalSeconds = 30;

    // ==================== Getters and Setters ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getAllowedIps() {
        return allowedIps;
    }

    public void setAllowedIps(List<String> allowedIps) {
        this.allowedIps = allowedIps;
    }

    public List<String> getProtectedPaths() {
        return protectedPaths;
    }

    public void setProtectedPaths(List<String> protectedPaths) {
        this.protectedPaths = protectedPaths;
    }

    public boolean isTrustXForwardedFor() {
        return trustXForwardedFor;
    }

    public void setTrustXForwardedFor(boolean trustXForwardedFor) {
        this.trustXForwardedFor = trustXForwardedFor;
    }

    public int getRefreshIntervalSeconds() {
        return refreshIntervalSeconds;
    }

    public void setRefreshIntervalSeconds(int refreshIntervalSeconds) {
        this.refreshIntervalSeconds = refreshIntervalSeconds;
    }
}
