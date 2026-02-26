package io.brix.platform.gateway.health;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Gateway 健康探针配置
 * <p>
 * 配置 Spring Boot Actuator 健康端点，支K8s liveness/readiness 探针
 * MVP 红线要求
 * <ul>
 *   <li>Gateway: /actuator/health + liveness/readiness</li>
 *   <li>就绪探针需依赖 Engine 健康状</li>
 * </ul>
 * </p>
 * 
 * <h3>端点说明</h3>
 * <ul>
 *   <li>/actuator/health - 综合健康状</li>
 *   <li>/actuator/health/liveness - 存活探针（仅检查应用本身）</li>
 *   <li>/actuator/health/readiness - 就绪探针（检查依赖服务）</li>
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
        logger.info("[shinwa] Gateway Health Probe Configuration:");
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
        logger.info("[shinwa] Available health endpoints:");
        logger.info("  - /actuator/health          - Comprehensive health status");
        logger.info("  - /actuator/health/liveness - Liveness probe (application only)");
        logger.info("  - /actuator/health/readiness - Readiness probe (with dependencies)");
        logger.info("  - /healthz                  - Lightweight health check (alias)");
    }
}
