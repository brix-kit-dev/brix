package io.brix.platform.starter.registration;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * v2.1 服务注册请求 DTO
 * 
 * <p>服务启动时向基座发送的注册信息</p>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 */
record ServiceRegistrationRequest(
    /**
     * 服务名称
     * 
     * <p>例如：shinwa-service-user</p>
     */
    String serviceName,
    
    /**
     * 服务实例 ID
     * 
     * <p>唯一标识一个服务实例，用于区分同一服务的多个实</p>
     */
    String instanceId,
    
    /**
     * 服务地址
     * 
     * <p>服务对外暴露HTTP 地址，例如：http://localhost:9010</p>
     */
    String serviceUrl,
    
    /**
     * 服务版本
     * 
     * <p>例如.1.0-SNAPSHOT</p>
     */
    String version,
    
    /**
     * 服务描述
     */
    String description,
    
    /**
     * 路由清单
     * 
     * <p>服务暴露的所有 REST 端点</p>
     */
    List<RouteInfo> routes,
    
    /**
     * 组装的插件列
     * 
     * <p>服务中组装了哪些插件 JAR</p>
     */
    List<PluginInfo> plugins,
    
    /**
     * 服务元数
     * 
     * <p>其他自定义信息，例如：环境、负责人</p>
     */
    Map<String, Object> metadata,
    
    /**
     * 注册时间
     */
    Instant registrationTime
) {}

/**
 * v2.1 插件信息 DTO
 * 
 * <p>描述服务中组装的插件</p>
 */
record PluginInfo(
    /**
     * 插件 ID
     * 
     * <p>Maven artifactId，例如：plugin-user-core</p>
     */
    String pluginId,
    
    /**
     * 插件名称
     * 
     * <p>人类可读名称，例如：用户管理插件</p>
     */
    String name,
    
    /**
     * 插件版本
     * 
     * <p>例如.1.0-SNAPSHOT</p>
     */
    String version,
    
    /**
     * 插件类型
     * 
     * <p>例如：CORE, API, EVENT</p>
     */
    String type,
    
    /**
     * 插件描述
     */
    String description
) {}

/**
 * v2.1 心跳请求 DTO
 * 
 * <p>服务定时向基座发送的心跳信息</p>
 */
record HeartbeatRequest(
    /**
     * 服务名称
     */
    String serviceName,
    
    /**
     * 服务实例 ID
     */
    String instanceId,
    
    /**
     * 服务状
     */
    ServiceStatus status,
    
    /**
     * 当前时间
     */
    Instant timestamp,
    
    /**
     * 健康指标
     */
    HealthMetrics healthMetrics
) {}

/**
 * v2.1 服务状态枚
 */
enum ServiceStatus {
    /** 启动*/
    STARTING,
    
    /** 运行*/
    RUNNING,
    
    /** 降级运行 */
    DEGRADED,
    
    /** 停止*/
    STOPPING,
    
    /** 已停*/
    STOPPED
}

/**
 * v2.1 健康指标 DTO
 */
record HealthMetrics(
    /**
     * CPU 使用率（百分比）
     */
    double cpuUsage,
    
    /**
     * 内存使用率（百分比）
     */
    double memoryUsage,
    
    /**
     * 活跃线程
     */
    int activeThreads,
    
    /**
     * 每秒请求
     */
    double requestsPerSecond,
    
    /**
     * 平均响应时间（毫秒）
     */
    double avgResponseTimeMs,
    
    /**
     * 错误率（百分比）
     */
    double errorRate
) {}
