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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Gateway Health Probe Configuration
 * <p>
 * configuration Spring Boot Actuator healthendpoint，supportK8s liveness/readiness probe
 * MVP Red Line Requirements
 * <ul>
 *   <li>Gateway: /actuator/health + liveness/readiness</li>
 *   <li>readinessprobeneeddepend on Engine healthstatus</li>
 * </ul>
 * </p>
 * 
 * <h3>Endpoint Description</h3>
 * <ul>
 *   <li>/actuator/health - comprehensivehealthstatus</li>
 *   <li>/actuator/health/liveness - Liveness probe (checks application only)</li>
 *   <li>/actuator/health/readiness - Readiness probe (checks dependent services)</li>
 * </ul>
 *
 * @author Brix Platform Authors
 * @version 1.0.0
 * @since MVP v1.0
 */
@Configuration
@EnableConfigurationProperties(HealthProperties.class)
public class GatewayHealthConfig {

    private static final Logger logger = LoggerFactory.getLogger(GatewayHealthConfig.class);

    private final HealthProperties healthProperties;

    public GatewayHealthConfig(HealthProperties healthProperties) {
        this.healthProperties = healthProperties;
    }

    @PostConstruct
    public void init() {
        logger.info("[brix] Gateway Health Probe Configuration:");
        logger.info("  - Health check enabled: {}", healthProperties.isEnabled());
        logger.info("  - Engine check enabled: {}", healthProperties.isEngineCheckEnabled());
        if (healthProperties.isEngineCheckEnabled()) {
            logger.info("  - Engine URL: {}", healthProperties.getEngineUrl());
            logger.info("  - Engine health path: {}", healthProperties.getEngineHealthPath());
            logger.info("  - Engine timeout: {}ms", healthProperties.getEngineTimeoutMs());
        }
        logger.info("  - Redis check enabled: {}", healthProperties.isRedisCheckEnabled());
        logger.info("  - Cache TTL: {}s", healthProperties.getCacheTtlSeconds());
        logger.info("  - Show details: {}", healthProperties.isShowDetails());
        logger.info("[brix] Available health endpoints:");
        logger.info("  - /actuator/health          - Comprehensive health status");
        logger.info("  - /actuator/health/liveness - Liveness probe (application only)");
        logger.info("  - /actuator/health/readiness - Readiness probe (with dependencies)");
        logger.info("  - /healthz                  - Lightweight health check (alias)");
    }
}
