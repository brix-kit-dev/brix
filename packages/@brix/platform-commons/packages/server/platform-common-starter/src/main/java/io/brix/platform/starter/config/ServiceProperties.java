package io.brix.platform.starter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * v2.1 服务配置属
 * 
 * <p>服务（shinwa-service-xxx）向基座注册所需的配置项</p>
 * 
 * <p>配置示例</p>
 * <pre>
 * shinwa:
 *   service:
 *     name: shinwa-service-user
 *     base-url: http://localhost:8900
 *     heartbeat-interval: 30s
 *     route-scan:
 *       enabled: true
 *       base-packages:
 *         - com.shinwa.plugin.user
 * </pre>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 */
@ConfigurationProperties(prefix = "shinwa.service")
public class ServiceProperties {

    /**
     * 服务名称，用于向基座注册
     * 
     * <p>格式建议：shinwa-service-{domain}</p>
     * <p>例如：shinwa-service-user, shinwa-service-contract</p>
     */
    private String name;

    /**
     * 基座网关地址
     * 
     * <p>服务注册和心跳请求的目标地址</p>
     * <p>例如：http://localhost:8900</p>
     */
    private String baseUrl;

    /**
     * 蹇冭烦闂撮殧
     * 
     * <p>默认 30 秒发送一次心</p>
     */
    private Duration heartbeatInterval = Duration.ofSeconds(30);

    /**
     * 是否启用服务注册
     * 
     * <p>默认启用。设置为 false 可禁用自动注</p>
     */
    private boolean registrationEnabled = true;

    /**
     * 注册重试次数
     * 
     * <p>初始注册失败时的最大重试次</p>
     */
    private int registrationRetryCount = 3;

    /**
     * 注册重试间隔
     * 
     * <p>注册失败后等待多久再重试</p>
     */
    private Duration registrationRetryInterval = Duration.ofSeconds(5);

    /**
     * API Key（网关认证）
     * 
     * <p>用于Plugin Engine 注册时的认证</p>
     * <p>通过环境变量 SHINWA_SERVICE_API_KEY 设置</p>
     */
    private String apiKey;

    /**
     * API Secret（网关认证）
     * 
     * <p>用于Plugin Engine 注册时的认证</p>
     * <p>通过环境变量 SHINWA_SERVICE_API_SECRET 设置</p>
     */
    private String apiSecret;

    /**
     * API 基础路径
     * 
     * <p>服务暴露API 基础路径，用Plugin Engine 网关路由</p>
     * <p>例如api/users, /api/case</p>
     * <p>格式要求：必须以 / 开</p>
     */
    private String apiBasePath;

    /**
     * 路由扫描配置
     */
    private RouteScan routeScan = new RouteScan();

    // ===== Getters and Setters =====

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public Duration getHeartbeatInterval() {
        return heartbeatInterval;
    }

    public void setHeartbeatInterval(Duration heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    public boolean isRegistrationEnabled() {
        return registrationEnabled;
    }

    public void setRegistrationEnabled(boolean registrationEnabled) {
        this.registrationEnabled = registrationEnabled;
    }

    public int getRegistrationRetryCount() {
        return registrationRetryCount;
    }

    public void setRegistrationRetryCount(int registrationRetryCount) {
        this.registrationRetryCount = registrationRetryCount;
    }

    public Duration getRegistrationRetryInterval() {
        return registrationRetryInterval;
    }

    public void setRegistrationRetryInterval(Duration registrationRetryInterval) {
        this.registrationRetryInterval = registrationRetryInterval;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }

    public String getApiBasePath() {
        return apiBasePath;
    }

    public void setApiBasePath(String apiBasePath) {
        this.apiBasePath = apiBasePath;
    }

    public RouteScan getRouteScan() {
        return routeScan;
    }

    public void setRouteScan(RouteScan routeScan) {
        this.routeScan = routeScan;
    }

    /**
     * 路由扫描配置
     * 
     * <p>控制如何扫描服务中组装的插件暴露REST 端点</p>
     */
    public static class RouteScan {

        /**
         * 是否启用路由扫描
         * 
         * <p>默认启用。设置为 false 可禁用自动路由扫</p>
         */
        private boolean enabled = true;

        /**
         * 扫描的基础
         * 
         * <p>指定要扫描的包路径，通常是组装的插件</p>
         * <p>例如：com.shinwa.plugin.user, com.shinwa.plugin.contract</p>
         * <p>如果不指定，默认扫描 com.shinwa.plugin</p>
         */
        private Set<String> basePackages = new HashSet<>();

        /**
         * 排除的路径模
         * 
         * <p>匹配这些模式的路由不会被注册</p>
         * <p>例如actuator/**, /internal/**</p>
         */
        private Set<String> excludePatterns = new HashSet<>();

        /**
         * 是否包含 actuator 端点
         * 
         * <p>默认不包含，因为 actuator 端点不需要路由注</p>
         */
        private boolean includeActuator = false;

        // ===== Getters and Setters =====

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Set<String> getBasePackages() {
            return basePackages;
        }

        public void setBasePackages(Set<String> basePackages) {
            this.basePackages = basePackages;
        }

        public Set<String> getExcludePatterns() {
            return excludePatterns;
        }

        public void setExcludePatterns(Set<String> excludePatterns) {
            this.excludePatterns = excludePatterns;
        }

        public boolean isIncludeActuator() {
            return includeActuator;
        }

        public void setIncludeActuator(boolean includeActuator) {
            this.includeActuator = includeActuator;
        }
    }
}
