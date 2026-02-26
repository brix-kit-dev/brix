package io.brix.platform.gateway.health;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

/**
 * 插件引擎健康指示
 * <p>
 * 检Plugin Engine 服务的健康状态
 * MVP 红线要求：Gateway 就绪探针需依赖 Engine 健康状态
 * </p>
 * 
 * <h3>实现特性：</h3>
 * <ul>
 *   <li>响应式实现，支持 WebFlux 环境</li>
 *   <li>超时保护，避免阻塞探针响</li>
 *   <li>结果缓存，避免频繁调</li>
 *   <li>优雅降级，超时或错误时返DOWN</li>
 * </ul>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 * @since MVP v1.0
 */
@Component("pluginEngine")
@ConditionalOnProperty(prefix = "gateway.health", name = "engine-check-enabled", havingValue = "true", matchIfMissing = true)
public class PluginEngineHealthIndicator implements ReactiveHealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(PluginEngineHealthIndicator.class);
    private static final String INDICATOR_NAME = "pluginEngine";

    private final HealthProperties healthProperties;
    private final WebClient webClient;

    /**
     * 健康状态缓
     * 用于避免频繁调用 Engine 健康端点
     */
    private final Map<String, CachedHealth> healthCache = new ConcurrentHashMap<>();

    public PluginEngineHealthIndicator(HealthProperties healthProperties, WebClient.Builder webClientBuilder) {
        this.healthProperties = healthProperties;
        this.webClient = webClientBuilder
                .baseUrl(Objects.requireNonNull(healthProperties.getEngineUrl()))
                .build();
        logger.info("[shinwa] PluginEngineHealthIndicator initialized - engineUrl: {}, timeout: {}ms",
                healthProperties.getEngineUrl(), healthProperties.getEngineTimeoutMs());
    }

    @Override
    public Mono<Health> health() {
        // 检查缓存是否有
        CachedHealth cached = healthCache.get(INDICATOR_NAME);
        if (cached != null && !cached.isExpired(healthProperties.getCacheTtlSeconds())) {
            return Mono.just(cached.getHealth());
        }

        return checkEngineHealth()
                .doOnNext(health -> {
                    // 更新缓存
                    healthCache.put(INDICATOR_NAME, new CachedHealth(health, Instant.now()));
                    if (health.getStatus().equals(org.springframework.boot.actuate.health.Status.DOWN)) {
                        logger.warn("[shinwa] Plugin Engine health check failed: {}", health.getDetails());
                    }
                })
                .doOnError(error -> {
                    logger.error("[shinwa] Plugin Engine health check error: {}", error.getMessage());
                });
    }

    /**
     * 执行 Engine 健康检
     */
    private Mono<Health> checkEngineHealth() {
        String healthUrl = healthProperties.getEngineHealthPath();
        Duration timeout = Duration.ofMillis(healthProperties.getEngineTimeoutMs());

        return webClient.get()
                .uri(Objects.requireNonNull(healthUrl))
                .retrieve()
                .bodyToMono(EngineHealthResponse.class)
                .timeout(timeout)
                .map(response -> {
                    if ("UP".equalsIgnoreCase(response.status())) {
                        return Health.up()
                                .withDetail("engineUrl", healthProperties.getEngineUrl())
                                .withDetail("engineStatus", response.status())
                                .withDetail("responseTime", "< " + healthProperties.getEngineTimeoutMs() + "ms")
                                .build();
                    } else {
                        return Health.down()
                                .withDetail("engineUrl", healthProperties.getEngineUrl())
                                .withDetail("engineStatus", response.status())
                                .withDetail("reason", "Engine reported non-UP status")
                                .build();
                    }
                })
                .onErrorResume(ex -> {
                    logger.warn("[shinwa] Engine health check failed: {}", ex.getMessage());
                    return Mono.just(Health.down()
                            .withDetail("engineUrl", healthProperties.getEngineUrl())
                            .withDetail("error", ex.getClass().getSimpleName())
                            .withDetail("message", ex.getMessage())
                            .build());
                });
    }

    /**
     * Engine 健康响应
     */
    private record EngineHealthResponse(String status) {}

    /**
     * 缓存的健康状
     */
    private static class CachedHealth {
        private final Health health;
        private final Instant timestamp;

        CachedHealth(Health health, Instant timestamp) {
            this.health = health;
            this.timestamp = timestamp;
        }

        Health getHealth() {
            return health;
        }

        boolean isExpired(int ttlSeconds) {
            return Instant.now().isAfter(timestamp.plusSeconds(ttlSeconds));
        }
    }
}
