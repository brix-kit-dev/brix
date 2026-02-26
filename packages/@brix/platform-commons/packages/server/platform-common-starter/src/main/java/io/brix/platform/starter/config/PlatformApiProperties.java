package io.brix.platform.starter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 平台 API 配置属
 * 
 * <p>统一管理 API 版本、路径前缀、注册端点等配置
 * 解决 API 版本路径不统一的问题</p>
 * 
 * <p>设计目的</p>
 * <ul>
 *   <li>解决问题4：API 版本前缀不统一v1 vs 无版本）</li>
 *   <li>配置API 版本路径</li>
 *   <li>统一注册端点路径</li>
 * </ul>
 * 
 * <p>配置示例</p>
 * <pre>
 * shinwa:
 *   api:
 *     version: ""                    # 无版本前缀（默认）
 *     # version: "v1"                # 使用 /v1 前缀
 *     include-version-in-routes: false
 *     registration-endpoint: /api/plugin-engine/register
 *     heartbeat-endpoint: /api/plugin-engine/cache/plugins/{name}/heartbeat
 * </pre>
 * 
 * <p>版本前缀规则</p>
 * <ul>
 *   <li>空字符串：不添加版本前缀（推荐）</li>
 *   <li>"v1"：添/v1 前缀</li>
 *   <li>"v2"：添/v2 前缀</li>
 * </ul>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 * @see ServiceProperties
 */
@ConfigurationProperties(prefix = "shinwa.api")
public class PlatformApiProperties {
    
    /**
     * API 版本前缀
     * 
     * <p>用于API 路径前添加版本号</p>
     * <p>空字符串表示不添加版本前缀（推荐）</p>
     * <p>例如v1" 会使路径变为 /v1/api/xxx</p>
     * 
     * <p>默认值：空字符串（无版本前缀</p>
     */
    private String version = "";
    
    /**
     * 是否在注册路由时包含版本前缀
     * 
     * <p>true 时，注册的路由会包含版本前缀</p>
     * <p>false 时，路由不包含版本前缀</p>
     * 
     * <p>默认值：false</p>
     */
    private boolean includeVersionInRoutes = false;
    
    /**
     * Plugin Engine 注册端点路径
     * 
     * <p>服务向基座注册时使用API 端点</p>
     * <p>必须Plugin Engine 的注册接口路径一</p>
     * 
     * <p>默认值：/api/plugin-engine/register</p>
     */
    private String registrationEndpoint = "/api/plugin-engine/register";
    
    /**
     * Plugin Engine 心跳端点路径
     * 
     * <p>服务向基座发送心跳时使用API 端点</p>
     * <p>{name} 会被替换为服务名</p>
     * 
     * <p>默认值：/api/plugin-engine/cache/plugins/{name}/heartbeat</p>
     */
    private String heartbeatEndpoint = "/api/plugin-engine/cache/plugins/{name}/heartbeat";
    
    /**
     * 网关路由前缀
     * 
     * <p>网关路由到服务时使用的前缀</p>
     * <p>用于生成服务的路由规</p>
     * 
     * <p>默认值：/api</p>
     */
    private String gatewayRoutePrefix = "/api";
    
    // ===== 构建方法 =====
    
    /**
     * 构建带版本的路径
     * 
     * <p>如果配置了版本前缀，则在路径前添加版本</p>
     * 
     * @param basePath 基础路径，必须以 / 开
     * @return 带版本的完整路径
     */
    public String buildVersionedPath(String basePath) {
        // 如果未配置版本前缀，直接返回原路径
        if (version == null || version.isEmpty()) {
            return basePath;
        }
        
        // 确保基础路径/ 开
        String normalizedPath = basePath.startsWith("/") ? basePath : "/" + basePath;
        
        // 确保版本前缀格式正确
        String normalizedVersion = version.startsWith("/") ? version : "/" + version;
        
        return normalizedVersion + normalizedPath;
    }
    
    /**
     * 获取实际的注册端点路
     * 
     * <p>根据配置决定是否包含版本前缀</p>
     * 
     * @return 注册端点路径
     */
    public String getActualRegistrationEndpoint() {
        if (includeVersionInRoutes) {
            return buildVersionedPath(registrationEndpoint);
        }
        return registrationEndpoint;
    }
    
    /**
     * 获取实际的心跳端点路
     * 
     * @param serviceName 服务名称，用于替{name} 占位
     * @return 心跳端点路径
     */
    public String getActualHeartbeatEndpoint(String serviceName) {
        String endpoint = includeVersionInRoutes 
            ? buildVersionedPath(heartbeatEndpoint) 
            : heartbeatEndpoint;
        return endpoint.replace("{name}", serviceName);
    }
    
    // ===== Getters and Setters =====
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public boolean isIncludeVersionInRoutes() {
        return includeVersionInRoutes;
    }
    
    public void setIncludeVersionInRoutes(boolean includeVersionInRoutes) {
        this.includeVersionInRoutes = includeVersionInRoutes;
    }
    
    public String getRegistrationEndpoint() {
        return registrationEndpoint;
    }
    
    public void setRegistrationEndpoint(String registrationEndpoint) {
        this.registrationEndpoint = registrationEndpoint;
    }
    
    public String getHeartbeatEndpoint() {
        return heartbeatEndpoint;
    }
    
    public void setHeartbeatEndpoint(String heartbeatEndpoint) {
        this.heartbeatEndpoint = heartbeatEndpoint;
    }
    
    public String getGatewayRoutePrefix() {
        return gatewayRoutePrefix;
    }
    
    public void setGatewayRoutePrefix(String gatewayRoutePrefix) {
        this.gatewayRoutePrefix = gatewayRoutePrefix;
    }
}
