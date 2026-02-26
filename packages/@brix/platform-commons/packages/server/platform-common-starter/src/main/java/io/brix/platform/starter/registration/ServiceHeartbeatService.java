package io.brix.platform.starter.registration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import io.brix.platform.starter.config.ServiceProperties;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * v2.1 服务心跳服务
 * 
 * <p>负责定时向基座 Plugin Engine 发送心跳，维持服务在线状</p>
 * 
 * <p>心跳端点（与 Plugin Engine 兼容）：</p>
 * <ul>
 *   <li>POST {baseUrl}/api/plugin-engine/cache/plugins/{name}/heartbeat</li>
 * </ul>
 * 
 * <p>参考：plugin-common-starter PluginRegistrationClient.heartbeat()</p>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 */
@Service
@EnableScheduling
public class ServiceHeartbeatService {
    
    private static final Logger log = LoggerFactory.getLogger(ServiceHeartbeatService.class);
    
    /** 服务配置 */
    private final ServiceProperties serviceProperties;
    
    /** 服务注册服务（获取实ID*/
    private final ServiceRegistrationService registrationService;
    
    /** HTTP 客户*/
    private final WebClient webClient;
    
    /** 健康端点（可选，用于获取健康状态） */
    private final HealthEndpoint healthEndpoint;
    
    /** 
     * 心跳端点路径模板 - Plugin Engine 兼容 
     * 格式: /api/plugin-engine/cache/plugins/{name}/heartbeat
     */
    private static final String HEARTBEAT_PATH_TEMPLATE = "/api/plugin-engine/cache/plugins/%s/heartbeat";
    
    /** 连续失败次数 */
    private final AtomicLong consecutiveFailures = new AtomicLong(0);
    
    /** 最大连续失败次数，超过后尝试重新注*/
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    
    /** 请求计数器（用于计算 RPS*/
    private final AtomicLong requestCount = new AtomicLong(0);
    private volatile long lastRequestCountSnapshot = 0;
    private volatile long lastSnapshotTime = System.currentTimeMillis();
    
    /** 响应时间累计（用于计算平均响应时间） */
    private final AtomicLong totalResponseTimeMs = new AtomicLong(0);
    private volatile long lastTotalResponseTimeSnapshot = 0;
    
    /** 错误计数*/
    private final AtomicLong errorCount = new AtomicLong(0);
    private volatile long lastErrorCountSnapshot = 0;
    
    public ServiceHeartbeatService(ServiceProperties serviceProperties,
                                   ServiceRegistrationService registrationService,
                                   HealthEndpoint healthEndpoint) {
        this.serviceProperties = serviceProperties;
        this.registrationService = registrationService;
        this.healthEndpoint = healthEndpoint;
        
        // 鏋勫缓 WebClient
        this.webClient = WebClient.builder()
            .baseUrl(serviceProperties.getBaseUrl())
            .build();
        
        log.info("[ServiceHeartbeat] 初始化完成，心跳间隔: {}ms", 
            serviceProperties.getHeartbeatInterval().toMillis());
    }
    
    /**
     * 定时发送心
     * 
     * <p>心跳间隔shinwa.service.heartbeat-interval 配置</p>
     * <p>默认 30 </p>
     */
    @Scheduled(fixedDelayString = "${shinwa.service.heartbeat-interval:30000}")
    public void sendHeartbeat() {
        // 检查是否已注册
        if (!registrationService.isRegistered()) {
            log.debug("[ServiceHeartbeat] 服务未注册，跳过心跳");
            return;
        }
        
        // 检查是否启用注
        if (!serviceProperties.isRegistrationEnabled()) {
            return;
        }
        
        // 发送心跳（Plugin Engine 格式 - 只需要服务名称，无需请求体）
        sendPluginEngineHeartbeat()
            .subscribe(
                success -> {
                    if (success) {
                        consecutiveFailures.set(0);
                        log.debug("[ServiceHeartbeat] 心跳发送成");
                    } else {
                        handleHeartbeatFailure();
                    }
                },
                error -> {
                    log.warn("[ServiceHeartbeat] 心跳发送异 {}", error.getMessage());
                    handleHeartbeatFailure();
                }
            );
    }
    
    /**
     * 处理心跳失败
     */
    private void handleHeartbeatFailure() {
        long failures = consecutiveFailures.incrementAndGet();
        log.warn("[ServiceHeartbeat] 心跳失败，连续失败次 {}", failures);
        
        // 连续失败超过阈值，尝试重新注册
        if (failures >= MAX_CONSECUTIVE_FAILURES) {
            log.info("[ServiceHeartbeat] 连续失败次数超过阈值，尝试重新注册");
            registrationService.register()
                .subscribe(
                    success -> {
                        if (success) {
                            consecutiveFailures.set(0);
                            log.info("[ServiceHeartbeat] 重新注册成功");
                        }
                    },
                    error -> log.error("[ServiceHeartbeat] 重新注册失败: {}", error.getMessage())
                );
        }
    }
    
    /**
     * 发Plugin Engine 格式的心跳请
     * 
     * <p>绔偣: POST /api/plugin-engine/cache/plugins/{name}/heartbeat</p>
     * <p>参 plugin-common-starter PluginRegistrationClient.heartbeat()</p>
     * 
     * @return 发送结
     */
    private Mono<Boolean> sendPluginEngineHeartbeat() {
        String heartbeatPath = String.format(HEARTBEAT_PATH_TEMPLATE, serviceProperties.getName());
        
        WebClient.RequestBodySpec requestSpec = webClient.post()
            .uri(heartbeatPath)
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Tenant-Id", "default");  // Plugin Engine 需要租户ID
        
        // 添加 API Key 认证头（如果配置了）
        if (StringUtils.hasText(serviceProperties.getApiKey()) 
            && StringUtils.hasText(serviceProperties.getApiSecret())) {
            requestSpec = (WebClient.RequestBodySpec) requestSpec
                .header("X-API-Key", serviceProperties.getApiKey())
                .header("X-API-Secret", serviceProperties.getApiSecret());
        }
        
        return requestSpec
            .retrieve()
            .toBodilessEntity()
            .map(response -> {
                if (response.getStatusCode().is2xxSuccessful()) {
                    return true;
                } else if (response.getStatusCode().value() == 404) {
                    // 插件不存在，需要重新注
                    log.warn("[ServiceHeartbeat] 服务不存在于 Plugin Engine，需要重新注");
                    return false;
                }
                return false;
            })
            .onErrorResume(error -> {
                log.debug("[ServiceHeartbeat] 心跳请求失败: {}", error.getMessage());
                return Mono.just(false);
            });
    }
    
    /**
     * 发送心跳请求（保留用于兼容
     * 
     * @param request 心跳请求
     * @return 发送结
     * @deprecated 使用 {@link #sendPluginEngineHeartbeat()} 替代
     */
    @Deprecated
    private Mono<Boolean> sendHeartbeatRequest(HeartbeatRequest request) {
        return sendPluginEngineHeartbeat();
    }
    
    /**
     * 构建心跳请求（保留用于兼容）
     * 
     * @return 心跳请求
     * @deprecated Plugin Engine 心跳不需要请求体
     */
    @Deprecated
    private HeartbeatRequest buildHeartbeatRequest() {
        return new HeartbeatRequest(
            serviceProperties.getName(),
            registrationService.getInstanceId(),
            determineServiceStatus(),
            Instant.now(),
            collectHealthMetrics()
        );
    }
    
    /**
     * 判断服务状
     * 
     * @return 服务状
     */
    private ServiceStatus determineServiceStatus() {
        if (healthEndpoint != null) {
            try {
                Status status = healthEndpoint.health().getStatus();
                if (Status.UP.equals(status)) {
                    return ServiceStatus.RUNNING;
                } else if (Status.DOWN.equals(status)) {
                    return ServiceStatus.DEGRADED;
                }
            } catch (Exception e) {
                log.debug("[ServiceHeartbeat] 获取健康状态失 {}", e.getMessage());
            }
        }
        
        return ServiceStatus.RUNNING;
    }
    
    /**
     * 收集健康指标
     * 
     * @return 健康指标
     */
    private HealthMetrics collectHealthMetrics() {
        OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        
        // CPU 使用
        double cpuUsage = osMXBean.getSystemLoadAverage();
        if (cpuUsage < 0) {
            cpuUsage = 0;  // 某些系统可能不支
        }
        
        // 内存使用
        long usedMemory = memoryMXBean.getHeapMemoryUsage().getUsed();
        long maxMemory = memoryMXBean.getHeapMemoryUsage().getMax();
        double memoryUsage = maxMemory > 0 ? (double) usedMemory / maxMemory * 100 : 0;
        
        // 活跃线程
        int activeThreads = threadMXBean.getThreadCount();
        
        // 计算 RPS（每秒请求数
        long currentTime = System.currentTimeMillis();
        long elapsedMs = currentTime - lastSnapshotTime;
        long currentRequestCount = requestCount.get();
        double rps = elapsedMs > 0 
            ? (currentRequestCount - lastRequestCountSnapshot) * 1000.0 / elapsedMs 
            : 0;
        
        // 计算平均响应时间
        long currentTotalResponseTime = totalResponseTimeMs.get();
        long requestsDelta = currentRequestCount - lastRequestCountSnapshot;
        double avgResponseTimeMs = requestsDelta > 0 
            ? (currentTotalResponseTime - lastTotalResponseTimeSnapshot) / (double) requestsDelta 
            : 0;
        
        // 计算错误
        long currentErrorCount = errorCount.get();
        double errorRate = requestsDelta > 0 
            ? (currentErrorCount - lastErrorCountSnapshot) * 100.0 / requestsDelta 
            : 0;
        
        // 更新快照
        lastSnapshotTime = currentTime;
        lastRequestCountSnapshot = currentRequestCount;
        lastTotalResponseTimeSnapshot = currentTotalResponseTime;
        lastErrorCountSnapshot = currentErrorCount;
        
        return new HealthMetrics(
            cpuUsage,
            memoryUsage,
            activeThreads,
            rps,
            avgResponseTimeMs,
            errorRate
        );
    }
    
    /**
     * 记录请求（供外部调用
     * 
     * @param responseTimeMs 响应时间（毫秒）
     * @param isError 是否错误
     */
    public void recordRequest(long responseTimeMs, boolean isError) {
        requestCount.incrementAndGet();
        totalResponseTimeMs.addAndGet(responseTimeMs);
        if (isError) {
            errorCount.incrementAndGet();
        }
    }
    
    /**
     * 获取连续失败次数
     * 
     * @return 连续失败次数
     */
    public long getConsecutiveFailures() {
        return consecutiveFailures.get();
    }
}
