package io.brix.platform.starter.registration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import io.brix.platform.starter.config.PlatformApiProperties;
import io.brix.platform.starter.config.ServiceProperties;
import io.brix.platform.starter.header.PlatformHeaders;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * v2.1 服务注册服务
 * 
 * <p>负责在服务启动时向基座 Plugin Engine 注册服务信息</p>
 * 
 * <p>注册流程</p>
 * <ol>
 *   <li>应用启动完成后触发注</li>
 *   <li>调用 RouteScanner 扫描所有路</li>
 *   <li>构建符合 Plugin Engine 格式的注册请</li>
 *   <li>通过 HTTP 向基座发送注册请</li>
 *   <li>失败时按配置重试</li>
 * </ol>
 * 
 * <p>注册端点（与 Plugin Engine 兼容）：</p>
 * <ul>
 *   <li>注册：POST {baseUrl}/api/plugin-engine/register</li>
 * </ul>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 */
@Service
public class ServiceRegistrationService implements ApplicationListener<ApplicationReadyEvent> {
    
    private static final Logger log = LoggerFactory.getLogger(ServiceRegistrationService.class);
    
    /** 服务配置 */
    private final ServiceProperties serviceProperties;
    
    /** API 配置 */
    private final PlatformApiProperties apiProperties;
    
    /** 路由扫描*/
    private final RouteScanner routeScanner;
    
    /** 插件 Manifest 扫描*/
    private final PluginManifestScanner manifestScanner;
    
    /** HTTP 客户*/
    private final WebClient webClient;
    
    /** 环境配置 */
    private final Environment environment;
    
    /** JSON 序列化器 */
    private final ObjectMapper objectMapper;
    
    /** 服务实例 ID */
    private final String instanceId;
    
    /** 注册状*/
    private volatile boolean registered = false;
    
    /** 
     * 注册端点路径 - Plugin Engine 兼容 
     * 参 PluginRegistrationController 使用 /api/plugin-engine/register
     */
    private static final String REGISTRY_PATH = "/api/plugin-engine/register";
    
    public ServiceRegistrationService(ServiceProperties serviceProperties,
                                      PlatformApiProperties apiProperties,
                                      RouteScanner routeScanner,
                                      PluginManifestScanner manifestScanner,
                                      Environment environment,
                                      ObjectMapper objectMapper) {
        this.serviceProperties = serviceProperties;
        this.apiProperties = apiProperties;
        this.routeScanner = routeScanner;
        this.manifestScanner = manifestScanner;
        this.environment = environment;
        this.objectMapper = objectMapper;
        
        // 鏋勫缓 WebClient
        this.webClient = WebClient.builder()
            .baseUrl(serviceProperties.getBaseUrl())
            .build();
        
        // 生成实例 ID
        this.instanceId = generateInstanceId();
        
        log.info("[ServiceRegistration] 初始化完成，实例ID: {}", instanceId);
    }
    
    /**
     * 应用启动完成后自动注
     * 
     * @param event 应用就绪事件
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // 检查是否启用注
        if (!serviceProperties.isRegistrationEnabled()) {
            log.info("[ServiceRegistration] 服务注册已禁");
            return;
        }
        
        log.info("[ServiceRegistration] 应用启动完成，开始向基座注册...");
        
        // 异步注册，不阻塞启动过程
        register()
            .subscribe(
                success -> {
                    if (success) {
                        registered = true;
                        log.info("[ServiceRegistration] 服务注册成功");
                    } else {
                        log.warn("[ServiceRegistration] 服务注册失败，但应用将继续运");
                    }
                },
                error -> log.error("[ServiceRegistration] 服务注册异常: {}", error.getMessage())
            );
    }
    
    /**
     * 应用关闭时注销
     * 
     * @param event 上下文关闭事
     */
    @EventListener(ContextClosedEvent.class)
    public void onApplicationShutdown(ContextClosedEvent event) {
        if (!registered) {
            return;
        }
        
        log.info("[ServiceRegistration] 应用关闭，开始注销服务...");
        
        // 同步注销，确保在应用关闭前完
        try {
            deregister().block(Duration.ofSeconds(5));
            log.info("[ServiceRegistration] 服务注销成功");
        } catch (Exception e) {
            log.warn("[ServiceRegistration] 服务注销失败: {}", e.getMessage());
        }
    }
    
    /**
     * 注册服务
     * 
     * <p>使用Plugin Engine 兼容API 格式</p>
     * <p>使用 PlatformHeaders 常量统一 Header 定义（解决问</p>
     * 
     * @return 注册结果
     */
    public Mono<Boolean> register() {
        // 1. 扫描路由
        List<RouteInfo> routes = routeScanner.scanRoutes();
        log.info("[ServiceRegistration] 扫描{} 个路", routes.size());
        
        // 2. 构建 Plugin Engine 兼容的注册请
        Map<String, Object> request = buildPluginEngineRequest(routes);
        
        // 3. 发送注册请求（使用 PlatformHeaders 常量
        WebClient.RequestBodySpec requestSpec = webClient.post()
            .uri(REGISTRY_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .header(PlatformHeaders.TENANT_ID, "default");  // Plugin Engine 需要租户ID
        
        // 添加 API Key 认证头（如果配置了）- 使用 PlatformHeaders 常量
        if (StringUtils.hasText(serviceProperties.getApiKey()) 
            && StringUtils.hasText(serviceProperties.getApiSecret())) {
            requestSpec = (WebClient.RequestBodySpec) requestSpec
                .header(PlatformHeaders.API_KEY, serviceProperties.getApiKey())
                .header(PlatformHeaders.API_SECRET, serviceProperties.getApiSecret());
            log.debug("[ServiceRegistration] 添加 API Key 认证");
        }
        
        return requestSpec
            .bodyValue(request)
            .exchangeToMono(response -> {
                if (response.statusCode().is2xxSuccessful()) {
                    log.info("[ServiceRegistration] 注册成功");
                    return Mono.just(true);
                } else {
                    return response.bodyToMono(String.class)
                        .doOnNext(body -> log.error("[ServiceRegistration] 注册失败 - 状态码: {}, 响应: {}", 
                            response.statusCode().value(), body))
                        .thenReturn(false);
                }
            })
            .retryWhen(Retry.backoff(
                serviceProperties.getRegistrationRetryCount(),
                serviceProperties.getRegistrationRetryInterval()
            ).doBeforeRetry(signal -> 
                log.warn("[ServiceRegistration] 注册失败，重试第 {} ", signal.totalRetries() + 1)
            ))
            .onErrorResume(error -> {
                log.error("[ServiceRegistration] 注册失败: {}", error.getMessage());
                return Mono.just(false);
            });
    }
    
    /**
     * 注销服务
     * 
     * <p>Plugin Engine 不支持显式注销，依靠心跳超时自动清</p>
     * 
     * @return 注销结果（总是返回 true
     */
    public Mono<Boolean> deregister() {
        log.info("[ServiceRegistration] 服务将通过心跳超时自动注销");
        return Mono.just(true);
    }
    
    /**
     * 构建 Plugin Engine 兼容的注册请
     * 
     * <p>格式参考 Plugin Engine PluginRegistration 模型</p>
     * <pre>
     * {
     *   "name": "服务名称",
     *   "version": "版本,
     *   "displayName": "展示名称",
     *   "serviceUrl": "服务地址",
     *   "apis": { "basePath": "/api/xxx", "endpoints": [...] },
     *   "events": null,
     *   "ui": null
     * }
     * </pre>
     * 
     * @param routes 路由清单
     * @return Plugin Engine 兼容的注册请
     */
    private Map<String, Object> buildPluginEngineRequest(List<RouteInfo> routes) {
        Map<String, Object> request = new HashMap<>();
        
        // 基本信息
        request.put("name", serviceProperties.getName());
        request.put("version", getServiceVersion());
        // v2.1.2 修复: 优先从插manifest 获取 displayName
        request.put("displayName", getPluginDisplayName());
        request.put("serviceUrl", getServiceUrl());
        
        // API 契约 - 使用 Plugin Engine 期望的格
        // basePath 是必填项，endpoints 是可选的
        String basePath = serviceProperties.getApiBasePath();
        if (!StringUtils.hasText(basePath)) {
            // 如果没有配置 apiBasePath，使用服务名构建默认路径
            // 例如: shinwa-service-case -> /api/case
            String serviceName = serviceProperties.getName();
            if (serviceName != null && serviceName.startsWith("shinwa-service-")) {
                basePath = "/api/" + serviceName.substring("shinwa-service-".length());
            } else {
                basePath = "/api/" + (serviceName != null ? serviceName : "unknown");
            }
            log.info("[ServiceRegistration] 使用默认 API basePath: {}", basePath);
        }
        
        Map<String, Object> apis = new HashMap<>();
        apis.put("basePath", basePath);
        
        // 将路由转换为 endpoints 格式
        List<Map<String, Object>> endpoints = new ArrayList<>();
        for (RouteInfo route : routes) {
            // 每个 HTTP 方法生成一endpoint
            for (String method : route.methods()) {
                Map<String, Object> endpoint = new HashMap<>();
                endpoint.put("path", route.path());
                endpoint.put("method", method);
                endpoint.put("summary", route.description());
                endpoint.put("tags", route.tags());
                endpoints.add(endpoint);
            }
        }
        apis.put("endpoints", endpoints);
        request.put("apis", apis);
        
        // 事件契约暂不使用
        request.put("events", null);
        
        // UI 契约 - 从插manifest 聚合
        Map<String, Object> aggregatedUi = manifestScanner.aggregateUiContracts();
        request.put("ui", aggregatedUi);
        
        if (aggregatedUi != null) {
            log.info("[ServiceRegistration] UI 契约已从插件 manifest 聚合");
        }
        
        return request;
    }
    
    /**
     * 构建注册请求（保留用于兼容）
     * 
     * @param routes 路由清单
     * @return 注册请求
     * @deprecated 使用 {@link #buildPluginEngineRequest(List)} 替代
     */
    @Deprecated
    private ServiceRegistrationRequest buildRegistrationRequest(List<RouteInfo> routes) {
        return new ServiceRegistrationRequest(
            serviceProperties.getName(),
            instanceId,
            getServiceUrl(),
            getServiceVersion(),
            getServiceDescription(),
            routes,
            scanPlugins(),
            buildMetadata(),
            Instant.now()
        );
    }
    
    /**
     * 生成服务实例 ID
     * 
     * <p>格式：{serviceName}-{hostname}-{port}-{uuid}</p>
     */
    private String generateInstanceId() {
        String hostname = getHostname();
        String port = environment.getProperty("server.port", "8080");
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        
        return String.format("%s-%s-%s-%s", 
            serviceProperties.getName(), hostname, port, uuid);
    }
    
    /**
     * 获取主机
     */
    private String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
    
    /**
     * 获取服务 URL
     */
    private String getServiceUrl() {
        String port = environment.getProperty("server.port", "8080");
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        
        // 优先使用配置的地址
        String configuredUrl = environment.getProperty("shinwa.service.url");
        if (configuredUrl != null && !configuredUrl.isEmpty()) {
            return configuredUrl;
        }
        
        // 否则使用本机地址
        String host = getHostAddress();
        return String.format("http://%s:%s%s", host, port, contextPath);
    }
    
    /**
     * 获取主机 IP 地址
     */
    private String getHostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }
    
    /**
     * 获取服务版本
     */
    private String getServiceVersion() {
        // Maven 构建信息获取
        String version = environment.getProperty("info.app.version");
        if (version != null) {
            return version;
        }
        
        // 从包信息获取
        Package pkg = getClass().getPackage();
        if (pkg != null && pkg.getImplementationVersion() != null) {
            return pkg.getImplementationVersion();
        }
        
        return "0.1.0-SNAPSHOT";
    }
    
    /**
     * 获取服务描述
     */
    private String getServiceDescription() {
        return environment.getProperty("info.app.description", 
            "Shinwa Service: " + serviceProperties.getName());
    }
    
    /**
     * 获取插件显示名称
     * 
     * <p>v2.1.2 修复：优先从插件 manifest 获取 displayName
     * 这样父菜单就会显示中文名称（用户管理"）而不Shinwa Service: xxx"</p>
     * 
     * <p>获取优先级：</p>
     * <ol>
     *   <li>插件 manifest 中的 displayName</li>
     *   <li>环境配置 info.app.description</li>
     *   <li>默认"Shinwa Service: {name}"</li>
     * </ol>
     * 
     * @return 插件显示名称
     */
    private String getPluginDisplayName() {
        // 1. 优先从插manifest 获取 displayName
        List<PluginManifest> manifests = manifestScanner.scanManifests();
        if (!manifests.isEmpty()) {
            // 如果有多manifest，尝试找到与服务名匹配的
            String serviceName = serviceProperties.getName();
            for (PluginManifest manifest : manifests) {
                // 检manifest 名称是否与服务名相关
                // 例如: service=shinwa-service-user, plugin=plugin-user
                String manifestName = manifest.getName();
                if (manifestName != null && manifest.getDisplayName() != null) {
                    // 服务名包含插件名的情 shinwa-service-user 包含 user
                    // 或者插件名对应服务: plugin-user -> service-user
                    String pluginCore = manifestName.replace("plugin-", "");
                    if (serviceName != null && 
                        (serviceName.contains(pluginCore) || serviceName.endsWith("-" + pluginCore))) {
                        log.debug("[ServiceRegistration] 使用匹配插件 {} displayName: {}", 
                            manifestName, manifest.getDisplayName());
                        return manifest.getDisplayName();
                    }
                }
            }
            
            // 如果没有精确匹配，使用第一个有 displayName manifest
            for (PluginManifest manifest : manifests) {
                if (manifest.getDisplayName() != null && !manifest.getDisplayName().isEmpty()) {
                    log.debug("[ServiceRegistration] 使用插件 {} displayName: {}", 
                        manifest.getName(), manifest.getDisplayName());
                    return manifest.getDisplayName();
                }
            }
        }
        
        // 2. 回退到配置文
        String configuredName = environment.getProperty("info.app.description");
        if (configuredName != null && !configuredName.isEmpty() 
            && !configuredName.startsWith("Shinwa Service:")) {
            return configuredName;
        }
        
        // 3. 最后回退到默认
        return "Shinwa Service: " + serviceProperties.getName();
    }

    /**
     * 扫描组装的插
     * 
     * <p>通过扫描 classpath 中的 plugin-xxx-core.jar 来识别插</p>
     */
    private List<PluginInfo> scanPlugins() {
        // TODO: 实现插件扫描逻辑
        // 可以通过扫描 META-INF/plugin.properties 或特定的 marker 接口来识别插
        return Collections.emptyList();
    }
    
    /**
     * 构建服务元数
     */
    private Map<String, Object> buildMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        
        // 运行环境
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length > 0) {
            metadata.put("profiles", Arrays.asList(profiles));
        }
        
        // Java 版本
        metadata.put("javaVersion", System.getProperty("java.version"));
        
        // Spring Boot 版本
        String bootVersion = environment.getProperty("spring.boot.version");
        if (bootVersion != null) {
            metadata.put("springBootVersion", bootVersion);
        }
        
        // 启动时间
        metadata.put("startTime", Instant.now().toString());
        
        return metadata;
    }
    
    /**
     * 获取实例 ID
     * 
     * @return 实例 ID
     */
    public String getInstanceId() {
        return instanceId;
    }
    
    /**
     * 检查是否已注册
     * 
     * @return 是否已注
     */
    public boolean isRegistered() {
        return registered;
    }
}
