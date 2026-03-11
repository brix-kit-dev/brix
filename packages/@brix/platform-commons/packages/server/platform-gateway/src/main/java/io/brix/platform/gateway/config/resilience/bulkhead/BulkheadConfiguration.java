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
package io.brix.platform.gateway.config.resilience.bulkhead;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.bulkhead.event.BulkheadOnCallRejectedEvent;
import jakarta.annotation.PostConstruct;

/**
 * concurrentisolation（Bulkhead）configurationclass
 * <p>
 * P101 task：Gatewayrate limitcircuit breaker（Resilience4j
 * </p>
 * <p>
 * based on Resilience4j Bulkhead implementationconcurrentcountlimit
 * preventdownstreamserviceslowresponsetimeconsumetrysystemresource
 * </p>
 * 
 * <h3>workworkoriginalmanage</h3>
 * <pre>
 * requesttoreach ──checkconcurrentcount ──┬── not yetreachuplimit ──obtainpermit ──calldownstream ──releasepermit
 *                         
 *                         └── alreadyreachuplimit ──wait/rejected（return503
 * </pre>
 * 
 * <h3>withrate limiterofarea</h3>
 * <ul>
 *   <li>rate limiter（RateLimiter）：controlsinglebittimeinofrequesttotalamount（QPS</li>
 *   <li>isolationcabin（Bulkhead）：controlsimultaneouslyperforminofrequestcountamount（concurrentcount</li>
 * </ul>
 * <p>
 * bothcantoconfigurationcombineuse，rate limiterpreventrequesttoo fast，isolationcabinpreventtoo much backlog
 * </p>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 * @since 1.02
 * @see BulkheadProperties
 */
@Configuration
@EnableConfigurationProperties(BulkheadProperties.class)
public class BulkheadConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(BulkheadConfiguration.class);

    /**
     * Bulkhead configuration properties
     */
    private final BulkheadProperties properties;

    /**
     * Bulkhead instance cache
     */
    private final Map<String, Bulkhead> bulkheadCache = new ConcurrentHashMap<>();

    /**
     * Resilience4j Bulkhead registry
     */
    private BulkheadRegistry bulkheadRegistry;

    public BulkheadConfiguration(BulkheadProperties properties) {
        this.properties = properties;
    }

    /**
     * Initialize Bulkhead registry
     */
    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            logger.info("[brix] Bulkhead disabled");
            return;
        }

        // Create default bulkhead configuration
        BulkheadProperties.BulkheadConfig defaultCfg = properties.getDefaultConfig();
        BulkheadConfig defaultConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(defaultCfg.getMaxConcurrentCalls())
                .maxWaitDuration(defaultCfg.getMaxWaitDuration())
                .build();

        // Create Bulkhead registry
        this.bulkheadRegistry = BulkheadRegistry.of(defaultConfig);

        logger.info("[brix] Bulkhead Configuration:");
        logger.info("[brix]   enabled={}", properties.isEnabled());
        logger.info("[brix]   default: maxConcurrentCalls={}, maxWaitDuration={}",
                defaultCfg.getMaxConcurrentCalls(),
                defaultCfg.getMaxWaitDuration());

        // Pre-create route-level bulkheads
        properties.getRoutes().forEach((routeId, config) -> {
            logger.info("[brix]   route[{}]: maxConcurrentCalls={}, maxWaitDuration={}",
                    routeId, config.getMaxConcurrentCalls(), config.getMaxWaitDuration());
            getBulkheadForRoute(routeId);
        });
    }

    /**
     * Get bulkhead for specified route
     * <p>
     * Prioritizes route-level configuration, falls back to default configuration if not found.
     * </p>
     * 
     * @param routeId route ID
     * @return corresponding bulkhead instance
     */
    public Bulkhead getBulkheadForRoute(String routeId) {
        if (!properties.isEnabled() || bulkheadRegistry == null) {
            return null;
        }

        return bulkheadCache.computeIfAbsent(routeId, id -> {
            BulkheadProperties.BulkheadConfig config = properties.getConfigForRoute(id);
            
            BulkheadConfig bhConfig = BulkheadConfig.custom()
                    .maxConcurrentCalls(config.getMaxConcurrentCalls())
                    .maxWaitDuration(config.getMaxWaitDuration())
                    .build();

            Bulkhead bulkhead = bulkheadRegistry.bulkhead(id, bhConfig);
            
            // registerrejectedeventlisten
            bulkhead.getEventPublisher()
                    .onCallRejected(this::handleCallRejected);
            
            return bulkhead;
        });
    }

    /**
     * processrequestbeisolationrejectedevent
     * 
     * @param event rejectedevent
     */
    private void handleCallRejected(BulkheadOnCallRejectedEvent event) {
        logger.warn("[brix] Bulkhead[{}] rejected call - concurrent limit reached",
                event.getBulkheadName());
    }

    /**
     * obtaindefaultisolation
     * 
     * @return defaultisolation
     */
    public Bulkhead getDefaultBulkhead() {
        return getBulkheadForRoute("default");
    }

    /**
     * checkisolationfunctionalitywhetherstart
     * 
     * @return true representsenable
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * obtainconfigurationproperty
     * 
     * @return isolationconfigurationproperty
     */
    public BulkheadProperties getProperties() {
        return properties;
    }
}
