package io.brix.platform.gateway.config.resilience.ratelimit;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.annotation.PostConstruct;

/**
 * 限流器配置类
 * <p>
 * P101 任务：网关限流熔断（Resilience4j
 * </p>
 * <p>
 * 基于 Resilience4j RateLimiter 实现滑动窗口 QPS 限流
 * 每个路由可以有独立的限流配置，也可以使用默认配置
 * </p>
 * 
 * <h3>限流算法说明</h3>
 * <p>
 * Resilience4j RateLimiter 采用 AtomicRateLimiter 实现
 * 使用原子操作确保线程安全，适合高并发场景
 * 核心参数
 * <ul>
 *   <li>limitForPeriod - 每个周期允许的请求数</li>
 *   <li>limitRefreshPeriod - 周期刷新时间</li>
 *   <li>timeoutDuration - 等待获取许可的超时时</li>
 * </ul>
 * </p>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * RateLimiter limiter = rateLimitConfig.getRateLimiterForRoute("plugin-engine");
 * // 尝试获取许可
 * if (limiter.acquirePermission()) {
 *     // 执行请求
 * } else {
 *     // 闄愭祦鎷掔粷
 * }
 * }</pre>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 * @since 1.02
 * @see RateLimitProperties
 * @see RateLimitFilter
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitConfig.class);

    /**
     * 限流配置属
     */
    private final RateLimitProperties properties;

    /**
     * 限流器注册表（缓存已创建的限流器实例
     * <p>
     * 技术点：使ConcurrentHashMap 缓存限流器实例，避免重复创建
     * </p>
     */
    private final Map<String, RateLimiter> rateLimiterCache = new ConcurrentHashMap<>();

    /**
     * Resilience4j 限流器注册表
     */
    private RateLimiterRegistry rateLimiterRegistry;

    public RateLimitConfig(RateLimitProperties properties) {
        this.properties = properties;
    }

    /**
     * 初始化限流器注册
     * <p>
     * Bean 初始化后执行，创建默认限流器配置并记录日
     * </p>
     */
    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            logger.info("[shinwa] RateLimit disabled");
            return;
        }

        // 创建默认限流配置
        RateLimitProperties.RateLimitConfig defaultCfg = properties.getDefaultConfig();
        RateLimiterConfig defaultConfig = RateLimiterConfig.custom()
                .limitForPeriod(defaultCfg.getLimitForPeriod())
                .limitRefreshPeriod(defaultCfg.getLimitRefreshPeriod())
                .timeoutDuration(defaultCfg.getTimeoutDuration())
                .build();

        // 创建限流器注册表（使用默认配置）
        this.rateLimiterRegistry = RateLimiterRegistry.of(defaultConfig);

        logger.info("[shinwa] RateLimit Configuration:");
        logger.info("[shinwa]   enabled={}", properties.isEnabled());
        logger.info("[shinwa]   default: limitForPeriod={}, refreshPeriod={}, timeout={}",
                defaultCfg.getLimitForPeriod(),
                defaultCfg.getLimitRefreshPeriod(),
                defaultCfg.getTimeoutDuration());

        // 预创建路由级别限流器
        properties.getRoutes().forEach((routeId, config) -> {
            logger.info("[shinwa]   route[{}]: limitForPeriod={}, refreshPeriod={}, timeout={}",
                    routeId, config.getLimitForPeriod(), 
                    config.getLimitRefreshPeriod(), config.getTimeoutDuration());
            getRateLimiterForRoute(routeId);
        });
    }

    /**
     * 获取指定路由的限流器
     * <p>
     * 优先使用路由级别配置，如果没有则使用默认配置
     * 限流器实例会被缓存，避免重复创建
     * </p>
     * 
     * @param routeId 路由ID，如 "plugin-engine"
     * @return 对应的限流器实例
     */
    public RateLimiter getRateLimiterForRoute(String routeId) {
        if (!properties.isEnabled() || rateLimiterRegistry == null) {
            return null;
        }

        return rateLimiterCache.computeIfAbsent(routeId, id -> {
            RateLimitProperties.RateLimitConfig config = properties.getConfigForRoute(id);
            
            // 创建路由专用的限流配
            RateLimiterConfig rateLimiterConfig = RateLimiterConfig.custom()
                    .limitForPeriod(config.getLimitForPeriod())
                    .limitRefreshPeriod(config.getLimitRefreshPeriod())
                    .timeoutDuration(config.getTimeoutDuration())
                    .build();

            return rateLimiterRegistry.rateLimiter(id, rateLimiterConfig);
        });
    }

    /**
     * 获取默认限流
     * <p>
     * 用于没有路由信息时的限流
     * </p>
     * 
     * @return 默认限流
     */
    public RateLimiter getDefaultRateLimiter() {
        return getRateLimiterForRoute("default");
    }

    /**
     * 检查限流是否启
     * 
     * @return true 表示启用
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * 获取配置属
     * 
     * @return 限流配置属
     */
    public RateLimitProperties getProperties() {
        return properties;
    }
}
