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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import jakarta.annotation.PostConstruct;

/**
 * Circuit Breaker Configurationclass
 * <p>
 * P101 task：Gatewayrate limitcircuit breaker（Resilience4j
 * </p>
 * <p>
 * based on Resilience4j CircuitBreaker implementationcircuit breakerprotect
 * supportbased onfailedrateandslowcallrateofcircuit breakerstrategy
 * </p>
 * 
 * <h3>circuit breakererState transitionflow</h3>
 * <pre>
 *                      ┌──────────────────
 *                          CLOSED       ◄─── normalstatus
 *                       (requestnormalvia)    
 *                      └────────┬─────────
 *                               failedslowcallrateexceedthreshold
 *                               
 *                      ┌──────────────────
 *                           OPEN        ◄─── circuit breakerstatus
 *                        (rejectedallhasplease   
 *                      └────────┬─────────
 *                               waittimeend
 *                               
 *                      ┌──────────────────
 *                         HALF_OPEN     ◄─── half-openstatus
 *                       (allowpartrequest)    
 *                      └────────┬─────────
 *                               
 *             ┌─────────────────┼─────────────────
 *             probesuccessful                         probefailed
 *                                               
 *        backto CLOSED                          backto OPEN
 * </pre>
 * 
 * <h3>eventlisten</h3>
 * <p>
 * configurationclasswillautomaticregisterState transitionEvent listener，oncircuit breakererstatuschangetimerecordlog
 * conveniencefor operationsmonitorandaskissuearrangesearch
 * </p>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 * @since 1.02
 * @see CircuitBreakerProperties
 * @see CircuitBreakerFilter
 */
@Configuration
@EnableConfigurationProperties(CircuitBreakerProperties.class)
public class CircuitBreakerConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerConfiguration.class);

    /**
     * Circuit breaker configuration properties
     */
    private final CircuitBreakerProperties properties;

    /**
     * Circuit breaker instance cache
     * <p>
     * Technical note: Uses ConcurrentHashMap to cache circuit breaker instances.
     * Key: Route ID, Value: Corresponding circuit breaker instance
     * </p>
     */
    private final Map<String, CircuitBreaker> circuitBreakerCache = new ConcurrentHashMap<>();

    /**
     * Resilience4j circuit breaker registry
     */
    private CircuitBreakerRegistry circuitBreakerRegistry;

    public CircuitBreakerConfiguration(CircuitBreakerProperties properties) {
        this.properties = properties;
    }

    /**
     * Initialize circuit breaker registry
     * <p>
     * Executed after Bean initialization, creates default circuit breaker configuration and logs it.
     * </p>
     */
    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            logger.info("[brix] CircuitBreaker disabled");
            return;
        }

        // Create default circuit breaker configuration
        CircuitBreakerProperties.CircuitBreakerConfig defaultCfg = properties.getDefaultConfig();
        CircuitBreakerConfig defaultConfig = buildCircuitBreakerConfig(defaultCfg);

        // Create circuit breaker registry
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(defaultConfig);

        logger.info("[brix] CircuitBreaker Configuration:");
        logger.info("[brix]   enabled={}", properties.isEnabled());
        logger.info("[brix]   default: failureRateThreshold={}%, slidingWindow={}/{}, " +
                        "minCalls={}, waitDuration={}",
                defaultCfg.getFailureRateThreshold(),
                defaultCfg.getSlidingWindowType(),
                defaultCfg.getSlidingWindowSize(),
                defaultCfg.getMinimumNumberOfCalls(),
                defaultCfg.getWaitDurationInOpenState());

        // precreateroutelevelcircuit breakerer
        properties.getRoutes().forEach((routeId, config) -> {
            logger.info("[brix]   route[{}]: failureRateThreshold={}%, slidingWindow={}/{}, " +
                            "minCalls={}, waitDuration={}",
                    routeId, config.getFailureRateThreshold(),
                    config.getSlidingWindowType(), config.getSlidingWindowSize(),
                    config.getMinimumNumberOfCalls(), config.getWaitDurationInOpenState());
            getCircuitBreakerForRoute(routeId);
        });
    }

    /**
     * build Resilience4j circuit breakererconfiguration
     * 
     * @param cfg configurationproperty
     * @return Resilience4j circuit breakererconfiguration
     */
    private CircuitBreakerConfig buildCircuitBreakerConfig(CircuitBreakerProperties.CircuitBreakerConfig cfg) {
        return CircuitBreakerConfig.custom()
                // failedratethreshold
                .failureRateThreshold(cfg.getFailureRateThreshold())
                // slowcallratethreshold
                .slowCallRateThreshold(cfg.getSlowCallRateThreshold())
                // slowcalltimethreshold
                .slowCallDurationThreshold(cfg.getSlowCallDurationThreshold())
                // slidingwindowtype
                .slidingWindowType(cfg.getSlidingWindowType())
                // slidingwindowsize
                .slidingWindowSize(cfg.getSlidingWindowSize())
                // minimumcalltimes
                .minimumNumberOfCalls(cfg.getMinimumNumberOfCalls())
                // circuit breakerwaittime
                .waitDurationInOpenState(cfg.getWaitDurationInOpenState())
                // half-openstatusallowofcallcount
                .permittedNumberOfCallsInHalfOpenState(cfg.getPermittedNumberOfCallsInHalfOpenState())
                // whetherautomaticconvertstatus
                .automaticTransitionFromOpenToHalfOpenEnabled(cfg.isAutomaticTransitionFromOpenToHalfOpenEnabled())
                .build();
    }

    /**
     * Get circuit breaker for specified route
     * <p>
     * Prioritizes route-level configuration, falls back to default configuration if not found.
     * Circuit breaker instances are cached to avoid repeated creation.
     * </p>
     * <p>
     * Technical note: When creating a circuit breaker for the first time,
     * a state transition event listener is registered for logging state changes.
     * </p>
     * 
     * @param routeId route ID, e.g. "plugin-engine"
     * @return corresponding circuit breaker instance
     */
    public CircuitBreaker getCircuitBreakerForRoute(String routeId) {
        if (!properties.isEnabled() || circuitBreakerRegistry == null) {
            return null;
        }

        return circuitBreakerCache.computeIfAbsent(routeId, id -> {
            CircuitBreakerProperties.CircuitBreakerConfig config = properties.getConfigForRoute(id);
            CircuitBreakerConfig cbConfig = buildCircuitBreakerConfig(config);
            
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(id, cbConfig);
            
            // registerState transitionEvent listener
            // technical point：listencircuit breakererstatuschange，conveniencefor operationsmonitor
            circuitBreaker.getEventPublisher()
                    .onStateTransition(this::handleStateTransition);
            
            return circuitBreaker;
        });
    }

    /**
     * processcircuit breakererState transitionevent
     * <p>
     * technical point：according toState transitionofstrictre-degreeusenotsameloglevel
     * - OPEN status：WARN level，representsservicecancanstoreonask
     * - otherstatus：INFO level
     * </p>
     * 
     * @param event State transitionevent
     */
    private void handleStateTransition(CircuitBreakerOnStateTransitionEvent event) {
        String cbName = event.getCircuitBreakerName();
        String fromState = event.getStateTransition().getFromState().name();
        String toState = event.getStateTransition().getToState().name();

        // according totargetstatusdetermineloglevel
        if ("OPEN".equals(toState)) {
            logger.warn("[brix] CircuitBreaker[{}] state transition: {} -> {} (circuit breakereralreadyopen，downstreamservicecancanhence",
                    cbName, fromState, toState);
        } else if ("CLOSED".equals(toState)) {
            logger.info("[brix] CircuitBreaker[{}] state transition: {} -> {} (circuit breakereralreadyclosed，servicerecovercorrect",
                    cbName, fromState, toState);
        } else {
            logger.info("[brix] CircuitBreaker[{}] state transition: {} -> {}",
                    cbName, fromState, toState);
        }
    }

    /**
     * obtaindefaultcircuit breaker
     * 
     * @return defaultcircuit breaker
     */
    public CircuitBreaker getDefaultCircuitBreaker() {
        return getCircuitBreakerForRoute("default");
    }

    /**
     * checkcircuit breakerfunctionalitywhetherstart
     * 
     * @return true representsenable
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * obtainconfigurationproperty
     * 
     * @return circuit breakerconfigurationproperty
     */
    public CircuitBreakerProperties getProperties() {
        return properties;
    }
}
