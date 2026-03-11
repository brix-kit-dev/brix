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
 * pluginenginehealthindicate
 * <p>
 * checkPlugin Engine serviceofhealthstatus
 * MVP Red Line Requirement: Gateway readiness probe must depend on Engine health status
 * </p>
 * 
 * <h3>Implementation Features:</h3>
 * <ul>
 *   <li>Reactive implementation supporting WebFlux environment</li>
 *   <li>timeoutprotect，avoidblockproberesponse</li>
 *   <li>resultcache，avoidfrequentadjust</li>
 *   <li>gracefulfallback，timeoutorerrortimereturnDOWN</li>
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
     * healthstatusslow
     * Used to avoid frequent calls to Engine health endpoint
     */
    private final Map<String, CachedHealth> healthCache = new ConcurrentHashMap<>();

    public PluginEngineHealthIndicator(HealthProperties healthProperties, WebClient.Builder webClientBuilder) {
        this.healthProperties = healthProperties;
        this.webClient = webClientBuilder
                .baseUrl(Objects.requireNonNull(healthProperties.getEngineUrl()))
                .build();
        logger.info("[brix] PluginEngineHealthIndicator initialized - engineUrl: {}, timeout: {}ms",
                healthProperties.getEngineUrl(), healthProperties.getEngineTimeoutMs());
    }

    @Override
    public Mono<Health> health() {
        // checkcachewhetherhas
        CachedHealth cached = healthCache.get(INDICATOR_NAME);
        if (cached != null && !cached.isExpired(healthProperties.getCacheTtlSeconds())) {
            return Mono.just(cached.getHealth());
        }

        return checkEngineHealth()
                .doOnNext(health -> {
                    // Update cache
                    healthCache.put(INDICATOR_NAME, new CachedHealth(health, Instant.now()));
                    if (health.getStatus().equals(org.springframework.boot.actuate.health.Status.DOWN)) {
                        logger.warn("[brix] Plugin Engine health check failed: {}", health.getDetails());
                    }
                })
                .doOnError(error -> {
                    logger.error("[brix] Plugin Engine health check error: {}", error.getMessage());
                });
    }

    /**
     * execute Engine healthcheck
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
                    logger.warn("[brix] Engine health check failed: {}", ex.getMessage());
                    return Mono.just(Health.down()
                            .withDetail("engineUrl", healthProperties.getEngineUrl())
                            .withDetail("error", ex.getClass().getSimpleName())
                            .withDetail("message", ex.getMessage())
                            .build());
                });
    }

    /**
     * Engine health response
     */
    private record EngineHealthResponse(String status) {}

    /**
     * cacheofhealthstatus
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
