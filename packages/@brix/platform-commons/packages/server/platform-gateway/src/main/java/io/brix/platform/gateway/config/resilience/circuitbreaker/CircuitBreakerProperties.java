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
package io.brix.platform.gateway.config.resilience.circuitbreaker;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;

/**
 * Circuit Breaker Configuration Propertiesclass
 * <p>
 * P101 task：Gatewayrate limitcircuit breaker（Resilience4j
 * </p>
 * <p>
 * configurationbeforefix：{@code gateway.circuitbreaker}
 * </p>
 * 
 * <h3>circuit breakererthree-statesay</h3>
 * <ul>
 *   <li><b>CLOSED（closed）</b> - normalstatus，requestnormalvia，durationstatisticsfailedrate</li>
 *   <li><b>OPEN（open</b> - circuit breakerstatus，requestdirectlyrejected，returnfallbackresponse</li>
 *   <li><b>HALF_OPEN（half-open</b> - probestatus，allowpartrequestvia，according toresultdeterminestatusconvert</li>
 * </ul>
 * 
 * <h3>configurationexample</h3>
 * <pre>{@code
 * gateway:
 *   circuitbreaker:
 *     enabled: true
 *     default-config:
 *       failure-rate-threshold: 50       # Failure rate threshold（%
 *       slow-call-rate-threshold: 100    # slowcallthreshold（%
 *       slow-call-duration-threshold: PT5S  # slowcalltimethreshold
 *       sliding-window-type: COUNT_BASED # Sliding window type
 *       sliding-window-size: 10          # Sliding window size
 *       minimum-number-of-calls: 5       # minimumcalltimes
 *       wait-duration-in-open-state: PT10S # circuit breakerwaittime
 *       permitted-calls-in-half-open-state: 3 # half-openstatusallowofcall
 * }</pre>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 * @since 1.02
 * @see CircuitBreakerConfiguration
 * @see CircuitBreakerFilter
 */
@ConfigurationProperties(prefix = "gateway.circuitbreaker")
@Validated
public class CircuitBreakerProperties {

    /**
     * whetherenablecircuit breakerfunctionality
     * <p>
     * productionenvironmentrecommendedsettrue，whendownstreamservicefaulttimeautomaticcircuit breakerprotect
     * </p>
     */
    private boolean enabled = true;

    /**
     * defaultcircuit breakerconfiguration
     * <p>
     * whenroutenohassinglealoneconfigurationtimeusethisdefaultconfiguration
     * </p>
     */
    private CircuitBreakerConfig defaultConfig = new CircuitBreakerConfig();

    /**
     * routelevelcircuit breakerconfiguration
     * <p>
     * Key: routeID（like plugin-engine
     * Value: thisrouteofcircuit breakerconfiguration
     * </p>
     */
    private Map<String, CircuitBreakerConfig> routes = new HashMap<>();

    // ========== Getters and Setters ==========

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public CircuitBreakerConfig getDefaultConfig() {
        return defaultConfig;
    }

    public void setDefaultConfig(CircuitBreakerConfig defaultConfig) {
        this.defaultConfig = defaultConfig;
    }

    public Map<String, CircuitBreakerConfig> getRoutes() {
        return routes;
    }

    public void setRoutes(Map<String, CircuitBreakerConfig> routes) {
        this.routes = routes;
    }

    /**
     * Get circuit breaker configuration for specified route
     * 
     * @param routeId route ID
     * @return circuit breaker configuration
     */
    public CircuitBreakerConfig getConfigForRoute(String routeId) {
        return routes.getOrDefault(routeId, defaultConfig);
    }

    /**
     * Single circuit breaker configuration
     * <p>
     * Uses sliding window to track failure rate, triggers circuit break when threshold is reached.
     * </p>
     */
    public static class CircuitBreakerConfig {

        /**
         * Failure rate threshold（Percentage）
         * <p>
         * whenslidingwindowinoffailedrateexceedthisthresholdtime，circuit breakereropen
         * defaultvalue：50%，that ishalfrequestfailedthencircuit breaker
         * </p>
         */
        private float failureRateThreshold = 50f;

        /**
         * Slow call rate threshold（Percentage）
         * <p>
         * whenslowcallproportionexceedthisthresholdtime，circuit breakereropen
         * defaultvalue：100%，representsnotbased onslowcallcircuit
         * </p>
         */
        private float slowCallRateThreshold = 100f;

        /**
         * slowcalltimethreshold
         * <p>
         * exceedthistimeofcallberecognizeisisslowadjust
         * defaultvalue：5
         * </p>
         */
        private Duration slowCallDurationThreshold = Duration.ofSeconds(5);

        /**
         * Sliding window type
         * <p>
         * COUNT_BASED - based oncallcountofslidingwindow
         * TIME_BASED - Time-basedofslidingwindow
         * defaultvalue：COUNT_BASED
         * </p>
         */
        private SlidingWindowType slidingWindowType = SlidingWindowType.COUNT_BASED;

        /**
         * Sliding window size
         * <p>
         * COUNT_BASED pattern：representsstatisticsofcallcount
         * TIME_BASED pattern：representsstatisticsoftimewindow（seconds
         * defaultvalue：10
         * </p>
         */
        private int slidingWindowSize = 10;

        /**
         * Trigger circuit breakcalculateofminimumcalltimes
         * <p>
         * onlyhaswhencallcountreachtothisvalueafterthenstartcalculatefailedrate
         * defaultvalue：5，avoidfewamountrequestthenTrigger circuit break
         * </p>
         */
        private int minimumNumberOfCalls = 5;

        /**
         * circuit breakereropenafterofwaittime
         * <p>
         * circuit breakereropenafter，waitthistimeafterenterhalf-openstatus
         * defaultvalue：10
         * </p>
         */
        private Duration waitDurationInOpenState = Duration.ofSeconds(10);

        /**
         * half-openstatusallowviaofcalltimes
         * <p>
         * used forprobedownstreamservicewhetherrecover
         * defaultvalue：3
         * </p>
         */
        private int permittedNumberOfCallsInHalfOpenState = 3;

        /**
         * whetherautomaticfromhalf-openstatusconvert
         * <p>
         * true - waitenoughcallafterautomaticconvertstatus
         * false - needhandmovetriggerstatusconvert
         * defaultvalue：true
         * </p>
         */
        private boolean automaticTransitionFromOpenToHalfOpenEnabled = true;

        // ========== Getters and Setters ==========

        public float getFailureRateThreshold() {
            return failureRateThreshold;
        }

        public void setFailureRateThreshold(float failureRateThreshold) {
            this.failureRateThreshold = failureRateThreshold;
        }

        public float getSlowCallRateThreshold() {
            return slowCallRateThreshold;
        }

        public void setSlowCallRateThreshold(float slowCallRateThreshold) {
            this.slowCallRateThreshold = slowCallRateThreshold;
        }

        public Duration getSlowCallDurationThreshold() {
            return slowCallDurationThreshold;
        }

        public void setSlowCallDurationThreshold(Duration slowCallDurationThreshold) {
            this.slowCallDurationThreshold = slowCallDurationThreshold;
        }

        public SlidingWindowType getSlidingWindowType() {
            return slidingWindowType;
        }

        public void setSlidingWindowType(SlidingWindowType slidingWindowType) {
            this.slidingWindowType = slidingWindowType;
        }

        public int getSlidingWindowSize() {
            return slidingWindowSize;
        }

        public void setSlidingWindowSize(int slidingWindowSize) {
            this.slidingWindowSize = slidingWindowSize;
        }

        public int getMinimumNumberOfCalls() {
            return minimumNumberOfCalls;
        }

        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
            this.minimumNumberOfCalls = minimumNumberOfCalls;
        }

        public Duration getWaitDurationInOpenState() {
            return waitDurationInOpenState;
        }

        public void setWaitDurationInOpenState(Duration waitDurationInOpenState) {
            this.waitDurationInOpenState = waitDurationInOpenState;
        }

        public int getPermittedNumberOfCallsInHalfOpenState() {
            return permittedNumberOfCallsInHalfOpenState;
        }

        public void setPermittedNumberOfCallsInHalfOpenState(int permittedNumberOfCallsInHalfOpenState) {
            this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
        }

        public boolean isAutomaticTransitionFromOpenToHalfOpenEnabled() {
            return automaticTransitionFromOpenToHalfOpenEnabled;
        }

        public void setAutomaticTransitionFromOpenToHalfOpenEnabled(boolean automaticTransitionFromOpenToHalfOpenEnabled) {
            this.automaticTransitionFromOpenToHalfOpenEnabled = automaticTransitionFromOpenToHalfOpenEnabled;
        }

        @Override
        public String toString() {
            return "CircuitBreakerConfig{" +
                    "failureRateThreshold=" + failureRateThreshold +
                    ", slowCallRateThreshold=" + slowCallRateThreshold +
                    ", slidingWindowType=" + slidingWindowType +
                    ", slidingWindowSize=" + slidingWindowSize +
                    ", minimumNumberOfCalls=" + minimumNumberOfCalls +
                    ", waitDurationInOpenState=" + waitDurationInOpenState +
                    ", permittedCallsInHalfOpen=" + permittedNumberOfCallsInHalfOpenState +
                    '}';
        }
    }
}
