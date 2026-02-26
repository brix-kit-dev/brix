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
 * 并发隔离（Bulkhead）配置类
 * <p>
 * P101 任务：网关限流熔断（Resilience4j
 * </p>
 * <p>
 * 基于 Resilience4j Bulkhead 实现并发数限制
 * 防止下游服务慢响应时耗尽系统资源
 * </p>
 * 
 * <h3>工作原理</h3>
 * <pre>
 * 请求到达 ──检查并发数 ──┬── 未达上限 ──获取许可 ──调用下游 ──释放许可
 *                         
 *                         └── 已达上限 ──等待/拒绝（返503
 * </pre>
 * 
 * <h3>与限流器的区</h3>
 * <ul>
 *   <li>限流器（RateLimiter）：控制单位时间内的请求总量（QPS</li>
 *   <li>隔离舱（Bulkhead）：控制同时进行中的请求数量（并发数</li>
 * </ul>
 * <p>
 * 两者可以配合使用，限流器防止请求过快，隔离舱防止积压过多
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
     * 隔离配置属
     */
    private final BulkheadProperties properties;

    /**
     * 隔离器实例缓
     */
    private final Map<String, Bulkhead> bulkheadCache = new ConcurrentHashMap<>();

    /**
     * Resilience4j 隔离器注册表
     */
    private BulkheadRegistry bulkheadRegistry;

    public BulkheadConfiguration(BulkheadProperties properties) {
        this.properties = properties;
    }

    /**
     * 初始化隔离器注册
     */
    @PostConstruct
    public void init() {
        if (!properties.isEnabled()) {
            logger.info("[shinwa] Bulkhead disabled");
            return;
        }

        // 创建默认隔离配置
        BulkheadProperties.BulkheadConfig defaultCfg = properties.getDefaultConfig();
        BulkheadConfig defaultConfig = BulkheadConfig.custom()
                .maxConcurrentCalls(defaultCfg.getMaxConcurrentCalls())
                .maxWaitDuration(defaultCfg.getMaxWaitDuration())
                .build();

        // 创建隔离器注册表
        this.bulkheadRegistry = BulkheadRegistry.of(defaultConfig);

        logger.info("[shinwa] Bulkhead Configuration:");
        logger.info("[shinwa]   enabled={}", properties.isEnabled());
        logger.info("[shinwa]   default: maxConcurrentCalls={}, maxWaitDuration={}",
                defaultCfg.getMaxConcurrentCalls(),
                defaultCfg.getMaxWaitDuration());

        // 预创建路由级别隔离器
        properties.getRoutes().forEach((routeId, config) -> {
            logger.info("[shinwa]   route[{}]: maxConcurrentCalls={}, maxWaitDuration={}",
                    routeId, config.getMaxConcurrentCalls(), config.getMaxWaitDuration());
            getBulkheadForRoute(routeId);
        });
    }

    /**
     * 获取指定路由的隔离器
     * <p>
     * 优先使用路由级别配置，如果没有则使用默认配置
     * </p>
     * 
     * @param routeId 路由ID
     * @return 对应的隔离器实例
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
            
            // 注册拒绝事件监听
            bulkhead.getEventPublisher()
                    .onCallRejected(this::handleCallRejected);
            
            return bulkhead;
        });
    }

    /**
     * 处理请求被隔离拒绝事
     * 
     * @param event 拒绝事件
     */
    private void handleCallRejected(BulkheadOnCallRejectedEvent event) {
        logger.warn("[shinwa] Bulkhead[{}] rejected call - concurrent limit reached",
                event.getBulkheadName());
    }

    /**
     * 获取默认隔离
     * 
     * @return 默认隔离
     */
    public Bulkhead getDefaultBulkhead() {
        return getBulkheadForRoute("default");
    }

    /**
     * 检查隔离功能是否启
     * 
     * @return true 表示启用
     */
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * 获取配置属
     * 
     * @return 隔离配置属
     */
    public BulkheadProperties getProperties() {
        return properties;
    }
}
