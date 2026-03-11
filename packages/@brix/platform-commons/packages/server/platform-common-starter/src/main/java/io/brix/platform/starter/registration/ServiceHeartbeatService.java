/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
 * v2.1 Service Heartbeat Service
 * 
 * <p>Responsible for periodically sending heartbeats to the shell Plugin Engine to maintain service online status</p>
 * 
 * <p>Heartbeat endpoint (compatible with Plugin Engine):</p>
 * <ul>
 *   <li>POST {baseUrl}/api/plugin-engine/cache/plugins/{name}/heartbeat</li>
 * </ul>
 * 
 * <p>Reference: plugin-common-starter PluginRegistrationClient.heartbeat()</p>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 */
@Service
@EnableScheduling
public class ServiceHeartbeatService {
    
    private static final Logger log = LoggerFactory.getLogger(ServiceHeartbeatService.class);
    
    /** Service configuration */
    private final ServiceProperties serviceProperties;
    
    /** Service registration service (for getting instance ID) */
    private final ServiceRegistrationService registrationService;
    
    /** HTTP client */
    private final WebClient webClient;
    
    /** Health endpoint (optional, for getting health status) */
    private final HealthEndpoint healthEndpoint;
    
    /** 
     * Heartbeat endpoint path template - Plugin Engine compatible 
     * Format: /api/plugin-engine/cache/plugins/{name}/heartbeat
     */
    private static final String HEARTBEAT_PATH_TEMPLATE = "/api/plugin-engine/cache/plugins/%s/heartbeat";
    
    /** Consecutive failure count */
    private final AtomicLong consecutiveFailures = new AtomicLong(0);
    
    /** Max consecutive failures, re-register after exceeding */
    private static final int MAX_CONSECUTIVE_FAILURES = 3;
    
    /** Request counter (for calculating RPS) */
    private final AtomicLong requestCount = new AtomicLong(0);
    private volatile long lastRequestCountSnapshot = 0;
    private volatile long lastSnapshotTime = System.currentTimeMillis();
    
    /** Accumulated response time (for calculating average response time) */
    private final AtomicLong totalResponseTimeMs = new AtomicLong(0);
    private volatile long lastTotalResponseTimeSnapshot = 0;
    
    /** Error counter */
    private final AtomicLong errorCount = new AtomicLong(0);
    private volatile long lastErrorCountSnapshot = 0;
    
    public ServiceHeartbeatService(ServiceProperties serviceProperties,
                                   ServiceRegistrationService registrationService,
                                   HealthEndpoint healthEndpoint) {
        this.serviceProperties = serviceProperties;
        this.registrationService = registrationService;
        this.healthEndpoint = healthEndpoint;
        
        // Build WebClient
        this.webClient = WebClient.builder()
            .baseUrl(serviceProperties.getBaseUrl())
            .build();
        
        log.info("[ServiceHeartbeat] Initialization complete, heartbeat interval: {}ms", 
            serviceProperties.getHeartbeatInterval().toMillis());
    }
    
    /**
     * Periodically send heartbeat
     * 
     * <p>Heartbeat interval is configured via brix.service.heartbeat-interval</p>
     * <p>Default: 30 seconds</p>
     */
    @Scheduled(fixedDelayString = "${brix.service.heartbeat-interval:30000}")
    public void sendHeartbeat() {
        // Check if registered
        if (!registrationService.isRegistered()) {
            log.debug("[ServiceHeartbeat] Service not registered, skipping heartbeat");
            return;
        }
        
        // Check if registration is enabled
        if (!serviceProperties.isRegistrationEnabled()) {
            return;
        }
        
        // Send heartbeat (Plugin Engine format - only needs service name, no request body)
        sendPluginEngineHeartbeat()
            .subscribe(
                success -> {
                    if (success) {
                        consecutiveFailures.set(0);
                        log.debug("[ServiceHeartbeat] Heartbeat sent successfully");
                    } else {
                        handleHeartbeatFailure();
                    }
                },
                error -> {
                    log.warn("[ServiceHeartbeat] Heartbeat sending exception: {}", error.getMessage());
                    handleHeartbeatFailure();
                }
            );
    }
    
    /**
     * Handle heartbeat failure
     */
    private void handleHeartbeatFailure() {
        long failures = consecutiveFailures.incrementAndGet();
        log.warn("[ServiceHeartbeat] Heartbeat failed, consecutive failures: {}", failures);
        
        // Consecutive failures exceed threshold, try to re-register
        if (failures >= MAX_CONSECUTIVE_FAILURES) {
            log.info("[ServiceHeartbeat] Consecutive failures exceeded threshold, attempting re-registration");
            registrationService.register()
                .subscribe(
                    success -> {
                        if (success) {
                            consecutiveFailures.set(0);
                            log.info("[ServiceHeartbeat] Re-registration successful");
                        }
                    },
                    error -> log.error("[ServiceHeartbeat] Re-registration failed: {}", error.getMessage())
                );
        }
    }
    
    /**
     * Send heartbeat request in Plugin Engine format
     * 
     * <p>Endpoint: POST /api/plugin-engine/cache/plugins/{name}/heartbeat</p>
     * <p>Reference: plugin-common-starter PluginRegistrationClient.heartbeat()</p>
     * 
     * @return Send result
     */
    private Mono<Boolean> sendPluginEngineHeartbeat() {
        String heartbeatPath = String.format(HEARTBEAT_PATH_TEMPLATE, serviceProperties.getName());
        
        WebClient.RequestBodySpec requestSpec = webClient.post()
            .uri(heartbeatPath)
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Tenant-Id", "default");  // Plugin Engine requires tenant ID
        
        // Add API Key authentication header (if configured)
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
                    // Plugin does not exist, need to re-register
                    log.warn("[ServiceHeartbeat] Service does not exist in Plugin Engine, need to re-register");
                    return false;
                }
                return false;
            })
            .onErrorResume(error -> {
                log.debug("[ServiceHeartbeat] Heartbeat request failed: {}", error.getMessage());
                return Mono.just(false);
            });
    }
    
    /**
     * Send heartbeat request (kept for backward compatibility)
     * 
     * @param request Heartbeat request
     * @return Send result
     * @deprecated Use {@link #sendPluginEngineHeartbeat()} instead
     */
    @Deprecated
    private Mono<Boolean> sendHeartbeatRequest(HeartbeatRequest request) {
        return sendPluginEngineHeartbeat();
    }
    
    /**
     * Build heartbeat request (kept for backward compatibility)
     * 
     * @return Heartbeat request
     * @deprecated Plugin Engine heartbeat does not require request body
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
     * Determine service status
     * 
     * @return Service status
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
                log.debug("[ServiceHeartbeat] Failed to get health status: {}", e.getMessage());
            }
        }
        
        return ServiceStatus.RUNNING;
    }
    
    /**
     * Collect health metrics
     * 
     * @return Health metrics
     */
    private HealthMetrics collectHealthMetrics() {
        OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        
        // CPU usage
        double cpuUsage = osMXBean.getSystemLoadAverage();
        if (cpuUsage < 0) {
            cpuUsage = 0;  // Some systems may not support this
        }
        
        // Memory usage
        long usedMemory = memoryMXBean.getHeapMemoryUsage().getUsed();
        long maxMemory = memoryMXBean.getHeapMemoryUsage().getMax();
        double memoryUsage = maxMemory > 0 ? (double) usedMemory / maxMemory * 100 : 0;
        
        // Active threads
        int activeThreads = threadMXBean.getThreadCount();
        
        // Calculate RPS (Requests Per Second)
        long currentTime = System.currentTimeMillis();
        long elapsedMs = currentTime - lastSnapshotTime;
        long currentRequestCount = requestCount.get();
        double rps = elapsedMs > 0 
            ? (currentRequestCount - lastRequestCountSnapshot) * 1000.0 / elapsedMs 
            : 0;
        
        // Calculate average response time
        long currentTotalResponseTime = totalResponseTimeMs.get();
        long requestsDelta = currentRequestCount - lastRequestCountSnapshot;
        double avgResponseTimeMs = requestsDelta > 0 
            ? (currentTotalResponseTime - lastTotalResponseTimeSnapshot) / (double) requestsDelta 
            : 0;
        
        // Calculate error rate
        long currentErrorCount = errorCount.get();
        double errorRate = requestsDelta > 0 
            ? (currentErrorCount - lastErrorCountSnapshot) * 100.0 / requestsDelta 
            : 0;
        
        // Update snapshots
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
     * Record request (for external invocation)
     * 
     * @param responseTimeMs Response time in milliseconds
     * @param isError Whether it's an error
     */
    public void recordRequest(long responseTimeMs, boolean isError) {
        requestCount.incrementAndGet();
        totalResponseTimeMs.addAndGet(responseTimeMs);
        if (isError) {
            errorCount.incrementAndGet();
        }
    }
    
    /**
     * Get consecutive failure count
     * 
     * @return Consecutive failure count
     */
    public long getConsecutiveFailures() {
        return consecutiveFailures.get();
    }
}
