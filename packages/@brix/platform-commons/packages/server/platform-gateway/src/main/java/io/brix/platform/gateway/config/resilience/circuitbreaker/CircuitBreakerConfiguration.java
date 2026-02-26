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
 * 熔断器配置类
 * <p>
 * P101 任务：网关限流熔断（Resilience4j
 * </p>
 * <p>
 * 基于 Resilience4j CircuitBreaker 实现熔断保护
 * 支持基于失败率和慢调用率的熔断策略
 * </p>
 * 
 * <h3>熔断器状态转换流</h3>
 * <pre>
 *                      ┌──────────────────
 *                          CLOSED       ◄─── 正常状
 *                       (请求正常通过)    
 *                      └────────┬─────────
 *                               失败慢调用率超过阈
 *                               
 *                      ┌──────────────────
 *                           OPEN        ◄─── 熔断状
 *                        (拒绝所有请   
 *                      └────────┬─────────
 *                               等待时间结束
 *                               
 *                      ┌──────────────────
 *                         HALF_OPEN     ◄─── 半开状
 *                       (允许部分请求)    
 *                      └────────┬─────────
 *                               
 *             ┌─────────────────┼─────────────────
 *             试探成功                         试探失败
 *                                               
 *        回到 CLOSED                          回到 OPEN
 * </pre>
 * 
 * <h3>事件监听</h3>
 * <p>
 * 配置类会自动注册状态转换事件监听器，在熔断器状态变化时记录日志
 * 便于运维监控和问题排查
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
     * 熔断配置属
     */
    private final CircuitBreakerProperties properties;

    /**
     * 熔断器实例缓
     * <p>
     * 技术点：使ConcurrentHashMap 缓存熔断器实
     * Key: 路由ID，Value: 对应的熔断器实例
     * </p>
     */
    private final Map<String, CircuitBreaker> circuitBreakerCache = new ConcurrentHashMap<>();

    /**
     * Resilience4j 熔断器注册表
     */
    private CircuitBreakerRegistry circuitBreakerRegistry;

    public CircuitBreakerConfiguration(CircuitBreakerProperties properties) {
        this.properties = properties;
    }

    /**
     * 初始化熔断器注册
     * <p>
     * Bean 初始化后执行，创建默认熔断配置并记录日志
     * </p>
     */
    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            logger.info("[shinwa] CircuitBreaker disabled");
            return;
        }

        // 创建默认熔断配置
        CircuitBreakerProperties.CircuitBreakerConfig defaultCfg = properties.getDefaultConfig();
        CircuitBreakerConfig defaultConfig = buildCircuitBreakerConfig(defaultCfg);

        // 创建熔断器注册表
        this.circuitBreakerRegistry = CircuitBreakerRegistry.of(defaultConfig);

        logger.info("[shinwa] CircuitBreaker Configuration:");
        logger.info("[shinwa]   enabled={}", properties.isEnabled());
        logger.info("[shinwa]   default: failureRateThreshold={}%, slidingWindow={}/{}, " +
                        "minCalls={}, waitDuration={}",
                defaultCfg.getFailureRateThreshold(),
                defaultCfg.getSlidingWindowType(),
                defaultCfg.getSlidingWindowSize(),
                defaultCfg.getMinimumNumberOfCalls(),
                defaultCfg.getWaitDurationInOpenState());

        // 预创建路由级别熔断器
        properties.getRoutes().forEach((routeId, config) -> {
            logger.info("[shinwa]   route[{}]: failureRateThreshold={}%, slidingWindow={}/{}, " +
                            "minCalls={}, waitDuration={}",
                    routeId, config.getFailureRateThreshold(),
                    config.getSlidingWindowType(), config.getSlidingWindowSize(),
                    config.getMinimumNumberOfCalls(), config.getWaitDurationInOpenState());
            getCircuitBreakerForRoute(routeId);
        });
    }

    /**
     * 构建 Resilience4j 熔断器配
     * 
     * @param cfg 配置属
     * @return Resilience4j 熔断器配
     */
    private CircuitBreakerConfig buildCircuitBreakerConfig(CircuitBreakerProperties.CircuitBreakerConfig cfg) {
        return CircuitBreakerConfig.custom()
                // 失败率阈
                .failureRateThreshold(cfg.getFailureRateThreshold())
                // 慢调用率阈
                .slowCallRateThreshold(cfg.getSlowCallRateThreshold())
                // 慢调用时间阈
                .slowCallDurationThreshold(cfg.getSlowCallDurationThreshold())
                // 滑动窗口类型
                .slidingWindowType(cfg.getSlidingWindowType())
                // 滑动窗口大小
                .slidingWindowSize(cfg.getSlidingWindowSize())
                // 最小调用次
                .minimumNumberOfCalls(cfg.getMinimumNumberOfCalls())
                // 熔断等待时间
                .waitDurationInOpenState(cfg.getWaitDurationInOpenState())
                // 半开状态允许的调用次数
                .permittedNumberOfCallsInHalfOpenState(cfg.getPermittedNumberOfCallsInHalfOpenState())
                // 是否自动转换状
                .automaticTransitionFromOpenToHalfOpenEnabled(cfg.isAutomaticTransitionFromOpenToHalfOpenEnabled())
                .build();
    }

    /**
     * 获取指定路由的熔断器
     * <p>
     * 优先使用路由级别配置，如果没有则使用默认配置
     * 熔断器实例会被缓存，避免重复创建
     * </p>
     * <p>
     * 技术点：首次创建熔断器时会注册状态转换事件监听器
     * 用于在熔断器状态变化时记录日志
     * </p>
     * 
     * @param routeId 路由ID，如 "plugin-engine"
     * @return 对应的熔断器实例
     */
    public CircuitBreaker getCircuitBreakerForRoute(String routeId) {
        if (!properties.isEnabled() || circuitBreakerRegistry == null) {
            return null;
        }

        return circuitBreakerCache.computeIfAbsent(routeId, id -> {
            CircuitBreakerProperties.CircuitBreakerConfig config = properties.getConfigForRoute(id);
            CircuitBreakerConfig cbConfig = buildCircuitBreakerConfig(config);
            
            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(id, cbConfig);
            
            // 注册状态转换事件监听器
            // 技术点：监听熔断器状态变化，便于运维监控
            circuitBreaker.getEventPublisher()
                    .onStateTransition(this::handleStateTransition);
            
            return circuitBreaker;
        });
    }

    /**
     * 处理熔断器状态转换事
     * <p>
     * 技术点：根据状态转换的严重程度使用不同日志级别
     * - OPEN 状态：WARN 级别，表示服务可能存在问
     * - 其他状态：INFO 级别
     * </p>
     * 
     * @param event 状态转换事
     */
    private void handleStateTransition(CircuitBreakerOnStateTransitionEvent event) {
        String cbName = event.getCircuitBreakerName();
        String fromState = event.getStateTransition().getFromState().name();
        String toState = event.getStateTransition().getToState().name();

        // 根据目标状态决定日志级
        if ("OPEN".equals(toState)) {
            logger.warn("[shinwa] CircuitBreaker[{}] state transition: {} -> {} (熔断器已打开，下游服务可能故",
                    cbName, fromState, toState);
        } else if ("CLOSED".equals(toState)) {
            logger.info("[shinwa] CircuitBreaker[{}] state transition: {} -> {} (熔断器已关闭，服务恢复正",
                    cbName, fromState, toState);
        } else {
            logger.info("[shinwa] CircuitBreaker[{}] state transition: {} -> {}",
                    cbName, fromState, toState);
        }
    }

    /**
     * 获取默认熔断
     * 
     * @return 默认熔断
     */
    public CircuitBreaker getDefaultCircuitBreaker() {
        return getCircuitBreakerForRoute("default");
    }

    /**
     * 检查熔断功能是否启
     * 
     * @return true 表示启用
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * 获取配置属
     * 
     * @return 熔断配置属
     */
    public CircuitBreakerProperties getProperties() {
        return properties;
    }
}
