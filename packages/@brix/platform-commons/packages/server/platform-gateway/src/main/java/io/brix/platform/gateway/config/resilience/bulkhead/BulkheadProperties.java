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

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * concurrentisolation（Bulkhead）configurationpropertiesclass
 * <p>
 * P101 task：Gatewayrate limitcircuit breaker（Resilience4j
 * </p>
 * <p>
 * configurationbeforefix：{@code gateway.bulkhead}
 * </p>
 * 
 * <h3>Bulkhead isolationcabinpatternsay</h3>
 * <p>
 * isolationcabinpatterninspired by shipsetcount，willshipbodysplit intomultiplealoneestablishcabinroom
 * that isuseone cabinroomwateralsonotwillguidecauseintegership sinkingno
 * onmicroservicein，Bulkhead used forlimitfordownstreamserviceofconcurrentcallcountamount
 * preventcertain downstreamserviceofslowresponseconsumetryallhasthreadresource
 * </p>
 * 
 * <h3>configurationexample</h3>
 * <pre>{@code
 * gateway:
 *   bulkhead:
 *     enabled: true
 *     default-config:
 *       max-concurrent-calls: 25       # maximumconcurrentcount
 *       max-wait-duration: PT0S        # waitobtainpermitofmaximumtime
 *     routes:
 *       plugin-engine:
 *         max-concurrent-calls: 50
 *         max-wait-duration: PT1S
 * }</pre>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 * @since 1.02
 * @see BulkheadConfiguration
 */
@ConfigurationProperties(prefix = "gateway.bulkhead")
@Validated
public class BulkheadProperties {

    /**
     * Whether to enable concurrency isolation
     * <p>
     * Production environments should set to true to protect system from slow responses.
     * </p>
     */
    private boolean enabled = true;

    /**
     * Default bulkhead configuration
     */
    private BulkheadConfig defaultConfig = new BulkheadConfig();

    /**
     * Route-level bulkhead configuration
     */
    private Map<String, BulkheadConfig> routes = new HashMap<>();

    // ========== Getters and Setters ==========

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public BulkheadConfig getDefaultConfig() {
        return defaultConfig;
    }

    public void setDefaultConfig(BulkheadConfig defaultConfig) {
        this.defaultConfig = defaultConfig;
    }

    public Map<String, BulkheadConfig> getRoutes() {
        return routes;
    }

    public void setRoutes(Map<String, BulkheadConfig> routes) {
        this.routes = routes;
    }

    /**
     * obtainspecifyrouteofisolationconfiguration
     * 
     * @param routeId routeID
     * @return isolationconfiguration
     */
    public BulkheadConfig getConfigForRoute(String routeId) {
        return routes.getOrDefault(routeId, defaultConfig);
    }

    /**
     * singleisolationconfiguration
     */
    public static class BulkheadConfig {

        /**
         * maximumconcurrentcallcount
         * <p>
         * simultaneouslyonlyallowspecifycountamountofrequestcalldownstreamservice
         * defaultvalue：25，according todownstreamservicecanforceadjust
         * </p>
         */
        private int maxConcurrentCalls = 25;

        /**
         * obtainpermitofmaximumwaittime
         * <p>
         * whenconcurrentcountreachtouplimittime，newrequestwaitofmaximumtime
         * defaultvalue：PT0Sseconds），representsestablishthat isreject
         * </p>
         */
        private Duration maxWaitDuration = Duration.ZERO;

        // ========== Getters and Setters ==========

        public int getMaxConcurrentCalls() {
            return maxConcurrentCalls;
        }

        public void setMaxConcurrentCalls(int maxConcurrentCalls) {
            this.maxConcurrentCalls = maxConcurrentCalls;
        }

        public Duration getMaxWaitDuration() {
            return maxWaitDuration;
        }

        public void setMaxWaitDuration(Duration maxWaitDuration) {
            this.maxWaitDuration = maxWaitDuration;
        }

        @Override
        public String toString() {
            return "BulkheadConfig{" +
                    "maxConcurrentCalls=" + maxConcurrentCalls +
                    ", maxWaitDuration=" + maxWaitDuration +
                    '}';
        }
    }
}
