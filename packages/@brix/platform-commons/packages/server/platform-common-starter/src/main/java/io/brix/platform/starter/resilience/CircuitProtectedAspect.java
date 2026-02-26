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
 * 熔断保护切面
 * 
 * <p>v2.1 阶段4 熔断降级实现</p>
 * 
 * <p>功能说明</p>
 * <p>拦截标注@CircuitProtected 注解的方法，实现熔断保护</p>
 * 
 * <p>熔断状态机</p>
 * <pre>
 *     ┌─────────────────────────────────────────────────────────
 *                                                             
 *      CLOSED ──(失败率超阈──> OPEN ──(等待超时)──> HALF_OPEN 
 *                                                         
 *                                                         
 *        └────────────────(成功率达──────────────────────   
 *                                                             
 *                        (失败率仍                          
 *                                                           
 *                                                           
 *                           OPEN ←────────────────────────────
 *     └─────────────────────────────────────────────────────────
 * </pre>
 * 
 * <p>⚠️ 注意事项</p>
 * <ul>
 *   <li>此实现是轻量级版本，生产环境建议使用 Resilience4j </li>
 *   <li>熔断状态存储在内存中，集群环境需要考虑分布式一致</li>
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
    prefix = "shinwa.resilience",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class CircuitProtectedAspect {
    
    private static final Logger log = LoggerFactory.getLogger(CircuitProtectedAspect.class);
    
    /** 熔断器状态存*/
    private final Map<String, CircuitBreakerState> circuitBreakers = new ConcurrentHashMap<>();
    
    /** 配置属*/
    private final ResilienceProperties properties;
    
    /**
     * 构造函数
     */
    public CircuitProtectedAspect(ResilienceProperties properties) {
        this.properties = properties;
        log.info("[CircuitProtectedAspect] 熔断保护切面已启");
    }
    
    /**
     * 拦截 @CircuitProtected 注解的方
     */
    @Around("@annotation(circuitProtected)")
    public Object protect(ProceedingJoinPoint joinPoint, CircuitProtected circuitProtected) throws Throwable {
        String name = circuitProtected.name();
        CircuitBreakerState state = circuitBreakers.computeIfAbsent(name, 
            k -> new CircuitBreakerState(properties.getCircuitBreakerConfig(k)));
        
        // 检查熔断状
        if (state.isOpen()) {
            // 检查是否可以进入半开状
            if (state.shouldAttemptReset()) {
                log.debug("[熔断] {} 进入半开状", name);
                state.transitionToHalfOpen();
            } else {
                log.debug("[鐔旀柇] {} 鐔旀柇涓紝鐩存帴闄嶇骇", name);
                return invokeFallback(joinPoint, circuitProtected, 
                    new CircuitBreakerOpenException("熔断器 " + name + " 处于打开状态"));
            }
        }
        
        // 执行目标方法
        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            
            // 记录成功
            state.recordSuccess(duration);
            log.debug("[熔断] {} 调用成功, duration={}ms", name, duration);
            
            return result;
        } catch (Throwable t) {
            long duration = System.currentTimeMillis() - startTime;
            
            // 检查是否需要记录为失败
            if (shouldRecordFailure(t, circuitProtected)) {
                state.recordFailure();
                log.debug("[熔断] {} 调用失败, 当前失败{}", name, state.getFailureRate());
                
                // 检查是否需要熔
                if (state.shouldOpen()) {
                    log.warn("[鐔旀柇] {} 瑙﹀彂鐔旀柇, failureRate={}%", name, state.getFailureRate());
                    state.transitionToOpen();
                }
            }
            
            // 尝试调用降级方法
            if (!circuitProtected.fallbackMethod().isEmpty()) {
                return invokeFallback(joinPoint, circuitProtected, t);
            }
            
            throw t;
        }
    }
    
    /**
     * 判断是否应记录为失败
     */
    private boolean shouldRecordFailure(Throwable t, CircuitProtected circuitProtected) {
        // 检查忽略的异常
        for (Class<? extends Throwable> ignored : circuitProtected.ignoreExceptions()) {
            if (ignored.isInstance(t)) {
                return false;
            }
        }
        
        // 检查需要记录的异常
        for (Class<? extends Throwable> recorded : circuitProtected.recordFailureFor()) {
            if (recorded.isInstance(t)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * 调用降级方法
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
        
        // 查找降级方法（尝试带 Throwable 参数的版本）
        Method fallbackMethod = findFallbackMethod(targetClass, fallbackMethodName, 
            signature.getParameterTypes(), true);
        
        if (fallbackMethod == null) {
            // 尝试不带 Throwable 参数的版
            fallbackMethod = findFallbackMethod(targetClass, fallbackMethodName, 
                signature.getParameterTypes(), false);
        }
        
        if (fallbackMethod == null) {
            log.error("[熔断] 降级方法未找 {}.{}", targetClass.getSimpleName(), fallbackMethodName);
            throw cause;
        }
        
        // 调用降级方法
        try {
            fallbackMethod.setAccessible(true);
            if (fallbackMethod.getParameterCount() == args.length + 1) {
                // Throwable 参数
                Object[] fallbackArgs = Arrays.copyOf(args, args.length + 1);
                fallbackArgs[args.length] = cause;
                return fallbackMethod.invoke(joinPoint.getTarget(), fallbackArgs);
            } else {
                return fallbackMethod.invoke(joinPoint.getTarget(), args);
            }
        } catch (Exception e) {
            log.error("[熔断] 降级方法执行失败", e);
            throw cause;
        }
    }
    
    /**
     * 查找降级方法
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
     * 获取熔断器状态（用于监控
     */
    public Map<String, CircuitBreakerState> getCircuitBreakers() {
        return circuitBreakers;
    }
    
    /**
     * 熔断器状
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
                // 滑动窗口：保持在窗口大小
                int total = successCount.get() + failureCount.get();
                if (total > config.getSlidingWindowSize()) {
                    successCount.updateAndGet(v -> Math.max(0, v - 1));
                }
            }
        }
        
        public void recordFailure() {
            if (state.get() == State.HALF_OPEN) {
                // 半开状态下失败，回到打开状
                transitionToOpen();
            } else {
                failureCount.incrementAndGet();
                // 滑动窗口
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
            log.info("[熔断状态] -> OPEN");
        }
        
        public void transitionToHalfOpen() {
            state.set(State.HALF_OPEN);
            halfOpenSuccessCount.set(0);
            lastStateChange = Instant.now();
            log.info("[熔断状态] -> HALF_OPEN");
        }
        
        public void transitionToClosed() {
            state.set(State.CLOSED);
            successCount.set(0);
            failureCount.set(0);
            halfOpenSuccessCount.set(0);
            openedAt = null;
            lastStateChange = Instant.now();
            log.info("[熔断状态] -> CLOSED");
        }
        
        public State getState() {
            return state.get();
        }
        
        public enum State {
            CLOSED, OPEN, HALF_OPEN
        }
    }
    
    /**
     * 熔断器打开异常
     */
    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }
}
