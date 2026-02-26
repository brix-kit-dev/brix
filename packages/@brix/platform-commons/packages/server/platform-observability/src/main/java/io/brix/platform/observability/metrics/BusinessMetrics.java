package io.brix.platform.observability.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 业务指标收集
 * 
 * <p>v2.1 阶段4 可观测性增强</p>
 * 
 * <p>功能说明</p>
 * <p>提供统一的业务指标收集接口，支持以下指标类型</p>
 * <ul>
 *   <li><b>Counter</b>：计数器，用于记录事件发生次</li>
 *   <li><b>Timer</b>：计时器，用于记录操作耗时</li>
 *   <li><b>Gauge</b>：仪表盘，用于记录瞬时</li>
 * </ul>
 * 
 * <p>指标命名规范</p>
 * <pre>
 * shinwa.{domain}.{metric_name}
 * 
 * 示例
 * - shinwa.file.upload.count    文件上传次数
 * - shinwa.file.upload.duration 文件上传耗时
 * - shinwa.case.active.count    活跃案件
 * - shinwa.outbox.pending.count 待发送事件数
 * </pre>
 * 
 * <p>使用示例</p>
 * <pre>{@code
 * @Autowired
 * private BusinessMetrics metrics;
 * 
 * public void uploadFile(File file) {
 *     Timer.Sample sample = metrics.startTimer();
 *     try {
 *         // 上传逻辑
 *         metrics.incrementCounter("file.upload.success", "type", file.getType());
 *     } catch (Exception e) {
 *         metrics.incrementCounter("file.upload.failure", "type", file.getType());
 *         throw e;
 *     } finally {
 *         metrics.stopTimer(sample, "file.upload.duration", "type", file.getType());
 *     }
 * }
 * }</pre>
 * 
 * @author Brix Platform Authors Platform Team
 * @since v2.1
 */
@Component
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnProperty(
    prefix = "observability.metrics",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class BusinessMetrics {
    
    private static final Logger log = LoggerFactory.getLogger(BusinessMetrics.class);
    
    /** 指标前缀 */
    private static final String METRIC_PREFIX = "shinwa.";
    
    /** Micrometer 注册*/
    private final MeterRegistry meterRegistry;
    
    /** 计数器缓存 */
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    
    /** 计时器缓存 */
    private final Map<String, Timer> timers = new ConcurrentHashMap<>();
    
    /**
     * 构造函数
     */
    public BusinessMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        log.info("[BusinessMetrics] 业务指标收集器已初始");
    }
    
    // ==================== Counter 操作 ====================
    
    /**
     * 增加计数
     * 
     * @param name 指标名称（不含前缀
     * @param tags 标签（键值对
     */
    public void incrementCounter(String name, String... tags) {
        String fullName = METRIC_PREFIX + name;
        String key = buildKey(fullName, tags);
        
        Counter counter = counters.computeIfAbsent(key, k -> 
            Counter.builder(fullName)
                .tags(tags)
                .description("业务计数 " + name)
                .register(meterRegistry)
        );
        
        counter.increment();
    }
    
    /**
     * 增加计数器指定
     * 
     * @param name 指标名称
     * @param amount 增加
     * @param tags 标签
     */
    public void incrementCounter(String name, double amount, String... tags) {
        String fullName = METRIC_PREFIX + name;
        String key = buildKey(fullName, tags);
        
        Counter counter = counters.computeIfAbsent(key, k -> 
            Counter.builder(fullName)
                .tags(tags)
                .description("业务计数 " + name)
                .register(meterRegistry)
        );
        
        counter.increment(amount);
    }
    
    // ==================== Timer 操作 ====================
    
    /**
     * 开始计
     * 
     * @return 计时采样
     */
    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }
    
    /**
     * 停止计时并记
     * 
     * @param sample 计时采样
     * @param name 指标名称
     * @param tags 标签
     * @return 耗时（毫秒）
     */
    public long stopTimer(Timer.Sample sample, String name, String... tags) {
        String fullName = METRIC_PREFIX + name;
        String key = buildKey(fullName, tags);
        
        Timer timer = timers.computeIfAbsent(key, k -> 
            Timer.builder(fullName)
                .tags(tags)
                .description("业务计时 " + name)
                .register(meterRegistry)
        );
        
        return sample.stop(timer);
    }
    
    /**
     * 记录耗时
     * 
     * @param name 指标名称
     * @param durationMs 耗时（毫秒）
     * @param tags 标签
     */
    public void recordDuration(String name, long durationMs, String... tags) {
        String fullName = METRIC_PREFIX + name;
        String key = buildKey(fullName, tags);
        
        Timer timer = timers.computeIfAbsent(key, k -> 
            Timer.builder(fullName)
                .tags(tags)
                .description("业务计时 " + name)
                .register(meterRegistry)
        );
        
        timer.record(durationMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * 执行操作并记录耗时
     * 
     * @param name 指标名称
     * @param operation 操作
     * @param tags 标签
     * @param <T> 返回类型
     * @return 操作结果
     */
    public <T> T timed(String name, Supplier<T> operation, String... tags) {
        Timer.Sample sample = startTimer();
        try {
            return operation.get();
        } finally {
            stopTimer(sample, name, tags);
        }
    }
    
    /**
     * 执行操作并记录耗时（无返回值）
     * 
     * @param name 指标名称
     * @param operation 操作
     * @param tags 标签
     */
    public void timed(String name, Runnable operation, String... tags) {
        Timer.Sample sample = startTimer();
        try {
            operation.run();
        } finally {
            stopTimer(sample, name, tags);
        }
    }
    
    // ==================== Gauge 操作 ====================
    
    /**
     * 注册仪表
     * 
     * @param name 指标名称
     * @param valueSupplier 值提供
     * @param tags 标签
     */
    public void registerGauge(String name, Supplier<Number> valueSupplier, String... tags) {
        String fullName = METRIC_PREFIX + name;
        
        Gauge.builder(fullName, valueSupplier)
            .tags(tags)
            .description("业务仪表 " + name)
            .register(meterRegistry);
        
        log.debug("[BusinessMetrics] 注册 Gauge: {}", fullName);
    }
    
    // ==================== 辅助方法 ====================
    
    /**
     * 构建缓存 key
     */
    private String buildKey(String name, String... tags) {
        if (tags == null || tags.length == 0) {
            return name;
        }
        StringBuilder sb = new StringBuilder(name);
        for (String tag : tags) {
            sb.append(":").append(tag);
        }
        return sb.toString();
    }
    
    /**
     * 获取 MeterRegistry（用于高级用法）
     */
    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }
}
