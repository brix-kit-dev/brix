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
package io.brix.platform.starter.resilience;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Circuit Breaker Protection Aspect
 * 
 * <p>v2.1 Phase 4 Circuit Breaker Implementation</p>
 * 
 * <p>Function Description</p>
 * <p>Intercepts methods annotated with @CircuitProtected to implement circuit breaker protection</p>
 * 
 * <p>Circuit Breaker State Machine</p>
 * <pre>
 *     ┌─────────────────────────────────────────────────────────
 *                                                             
 *      CLOSED ──(failure rate exceeds threshold)──> OPEN ──(wait timeout)──> HALF_OPEN 
 *                                                         
 *                                                         
 *        └────────────────(success rate reaches threshold)──────────────────────   
 *                                                             
 *                        (failure rate still high)                          
 *                                                           
 *                                                           
 *                           OPEN ←────────────────────────────
 *     └─────────────────────────────────────────────────────────
 * </pre>
 * 
 * <p>⚠️ Notes</p>
 * <ul>
 *   <li>This is a lightweight implementation, Resilience4j is recommended for production</li>
 *   <li>Circuit breaker state is stored in memory, distributed consistency is needed for cluster environments</li>
 * </ul>
 * 
 * @author Brix Platform Authors Platform Team
 * @since v2.1
 * @see CircuitProtected
 */
@Aspect
@Component
@Order(3)
@ConditionalOnProperty(
    prefix = "brix.resilience",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class CircuitProtectedAspect {
    
    private static final Logger log = LoggerFactory.getLogger(CircuitProtectedAspect.class);
    
    /** Circuit breaker state storage */
    private final Map<String, CircuitBreakerState> circuitBreakers = new ConcurrentHashMap<>();
    
    /** Configuration properties */
    private final ResilienceProperties properties;
    
    /**
     * Constructor
     */
    public CircuitProtectedAspect(ResilienceProperties properties) {
        this.properties = properties;
        log.info("[CircuitProtectedAspect] Circuit breaker protection aspect enabled");
    }
    
    /**
     * Intercept methods annotated with @CircuitProtected
     */
    @Around("@annotation(circuitProtected)")
    public Object protect(ProceedingJoinPoint joinPoint, CircuitProtected circuitProtected) throws Throwable {
        String name = circuitProtected.name();
        CircuitBreakerState state = circuitBreakers.computeIfAbsent(name, 
            k -> new CircuitBreakerState(properties.getCircuitBreakerConfig(k)));
        
        // Check circuit breaker state
        if (state.isOpen()) {
            // Check if can enter half-open state
            if (state.shouldAttemptReset()) {
                log.debug("[CircuitBreaker] {} entering half-open state", name);
                state.transitionToHalfOpen();
            } else {
                log.debug("[CircuitBreaker] {} is open, falling back directly", name);
                return invokeFallback(joinPoint, circuitProtected, 
                    new CircuitBreakerOpenException("CircuitBreaker " + name + " is in open state"));
            }
        }
        
        // Execute target method
        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            
            // Record success
            state.recordSuccess(duration);
            log.debug("[CircuitBreaker] {} call succeeded, duration={}ms", name, duration);
            
            return result;
        } catch (Throwable t) {
            long duration = System.currentTimeMillis() - startTime;
            
            // Check if should record as failure
            if (shouldRecordFailure(t, circuitProtected)) {
                state.recordFailure();
                log.debug("[CircuitBreaker] {} call failed, current failure rate: {}", name, state.getFailureRate());
                
                // Check if should open circuit breaker
                if (state.shouldOpen()) {
                    log.warn("[CircuitBreaker] {} triggered circuit break, failureRate={}%", name, state.getFailureRate());
                    state.transitionToOpen();
                }
            }
            
            // Try to invoke fallback method
            if (!circuitProtected.fallbackMethod().isEmpty()) {
                return invokeFallback(joinPoint, circuitProtected, t);
            }
            
            throw t;
        }
    }
    
    /**
     * Determine if should record as failure
     */
    private boolean shouldRecordFailure(Throwable t, CircuitProtected circuitProtected) {
        // Check ignored exceptions
        for (Class<? extends Throwable> ignored : circuitProtected.ignoreExceptions()) {
            if (ignored.isInstance(t)) {
                return false;
            }
        }
        
        // Check exceptions to record
        for (Class<? extends Throwable> recorded : circuitProtected.recordFailureFor()) {
            if (recorded.isInstance(t)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Invoke fallback method
     */
    private Object invokeFallback(ProceedingJoinPoint joinPoint, 
                                   CircuitProtected circuitProtected, 
                                   Throwable cause) throws Throwable {
        String fallbackMethodName = circuitProtected.fallbackMethod();
        if (fallbackMethodName.isEmpty()) {
            throw cause;
        }
        
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Class<?> targetClass = joinPoint.getTarget().getClass();
        Object[] args = joinPoint.getArgs();
        
        // Find fallback method (try version with Throwable parameter)
        Method fallbackMethod = findFallbackMethod(targetClass, fallbackMethodName, 
            signature.getParameterTypes(), true);
        
        if (fallbackMethod == null) {
            // Try version without Throwable parameter
            fallbackMethod = findFallbackMethod(targetClass, fallbackMethodName, 
                signature.getParameterTypes(), false);
        }
        
        if (fallbackMethod == null) {
            log.error("[CircuitBreaker] Fallback method not found: {}.{}", targetClass.getSimpleName(), fallbackMethodName);
            throw cause;
        }
        
        // Invoke fallback method
        try {
            fallbackMethod.setAccessible(true);
            if (fallbackMethod.getParameterCount() == args.length + 1) {
                // With Throwable parameter
                Object[] fallbackArgs = Arrays.copyOf(args, args.length + 1);
                fallbackArgs[args.length] = cause;
                return fallbackMethod.invoke(joinPoint.getTarget(), fallbackArgs);
            } else {
                return fallbackMethod.invoke(joinPoint.getTarget(), args);
            }
        } catch (Exception e) {
            log.error("[CircuitBreaker] Fallback method execution failed", e);
            throw cause;
        }
    }
    
    /**
     * Find fallback method
     */
    private Method findFallbackMethod(Class<?> targetClass, String methodName, 
                                       Class<?>[] paramTypes, boolean withThrowable) {
        try {
            if (withThrowable) {
                Class<?>[] newParamTypes = Arrays.copyOf(paramTypes, paramTypes.length + 1);
                newParamTypes[paramTypes.length] = Throwable.class;
                return targetClass.getDeclaredMethod(methodName, newParamTypes);
            } else {
                return targetClass.getDeclaredMethod(methodName, paramTypes);
            }
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
    
    /**
     * Get circuit breaker states (for monitoring)
     */
    public Map<String, CircuitBreakerState> getCircuitBreakers() {
        return circuitBreakers;
    }
    
    /**
     * Circuit breaker state
     */
    public static class CircuitBreakerState {
        
        private final ResilienceProperties.CircuitBreakerConfig config;
        private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicInteger halfOpenSuccessCount = new AtomicInteger(0);
        private volatile Instant lastStateChange = Instant.now();
        private volatile Instant openedAt;
        
        public CircuitBreakerState(ResilienceProperties.CircuitBreakerConfig config) {
            this.config = config;
        }
        
        public boolean isOpen() {
            return state.get() == State.OPEN;
        }
        
        public boolean isClosed() {
            return state.get() == State.CLOSED;
        }
        
        public boolean isHalfOpen() {
            return state.get() == State.HALF_OPEN;
        }
        
        public boolean shouldAttemptReset() {
            if (openedAt == null) {
                return false;
            }
            return Duration.between(openedAt, Instant.now()).toMillis() >= 
                config.getWaitDurationOpenMillis();
        }
        
        public boolean shouldOpen() {
            int total = successCount.get() + failureCount.get();
            if (total < config.getMinimumCalls()) {
                return false;
            }
            return getFailureRate() >= config.getFailureRateThreshold();
        }
        
        public int getFailureRate() {
            int total = successCount.get() + failureCount.get();
            if (total == 0) {
                return 0;
            }
            return (failureCount.get() * 100) / total;
        }
        
        public void recordSuccess(long durationMs) {
            if (state.get() == State.HALF_OPEN) {
                int count = halfOpenSuccessCount.incrementAndGet();
                if (count >= config.getPermittedCallsHalfOpen()) {
                    transitionToClosed();
                }
            } else {
                successCount.incrementAndGet();
                // Sliding window: keep within window size
                int total = successCount.get() + failureCount.get();
                if (total > config.getSlidingWindowSize()) {
                    successCount.updateAndGet(v -> Math.max(0, v - 1));
                }
            }
        }
        
        public void recordFailure() {
            if (state.get() == State.HALF_OPEN) {
                // Failure in half-open state, return to open state
                transitionToOpen();
            } else {
                failureCount.incrementAndGet();
                // Sliding window
                int total = successCount.get() + failureCount.get();
                if (total > config.getSlidingWindowSize()) {
                    failureCount.updateAndGet(v -> Math.max(0, v - 1));
                }
            }
        }
        
        public void transitionToOpen() {
            state.set(State.OPEN);
            openedAt = Instant.now();
            lastStateChange = openedAt;
            log.info("[CircuitBreakerState] -> OPEN");
        }
        
        public void transitionToHalfOpen() {
            state.set(State.HALF_OPEN);
            halfOpenSuccessCount.set(0);
            lastStateChange = Instant.now();
            log.info("[CircuitBreakerState] -> HALF_OPEN");
        }
        
        public void transitionToClosed() {
            state.set(State.CLOSED);
            successCount.set(0);
            failureCount.set(0);
            halfOpenSuccessCount.set(0);
            openedAt = null;
            lastStateChange = Instant.now();
            log.info("[CircuitBreakerState] -> CLOSED");
        }
        
        public State getState() {
            return state.get();
        }
        
        public enum State {
            CLOSED, OPEN, HALF_OPEN
        }
    }
    
    /**
     * Circuit breaker open exception
     */
    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }
}
