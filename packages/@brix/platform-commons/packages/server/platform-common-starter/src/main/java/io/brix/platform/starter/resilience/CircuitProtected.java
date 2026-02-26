package io.brix.platform.starter.resilience;

import java.lang.annotation.*;

/**
 * 熔断保护注解
 * 
 * <p>v2.1 阶段4 熔断降级实现</p>
 * 
 * <p>功能说明</p>
 * <p>标记需要熔断保护的方法，当方法调用失败率超过阈值时
 * 触发熔断，直接返回降级结果</p>
 * 
 * <p>使用示例</p>
 * <pre>{@code
 * @CircuitProtected(
 *     name = "fileStorage",
 *     fallbackMethod = "downloadFallback"
 * )
 * public InputStream download(Long fileId) {
 *     return storageAdapter.download(fileId);
 * }
 * 
 * public InputStream downloadFallback(Long fileId, Throwable t) {
 *     log.warn("文件下载降级: fileId={}, error={}", fileId, t.getMessage());
 *     throw new ServiceUnavailableException("文件服务暂不可用，请稍后重试");
 * }
 * }</pre>
 * 
 * <p>熔断策略</p>
 * <ul>
 *   <li><b>失败率阈</b>：连续请求中失败比例超过阈值触发熔</li>
 *   <li><b>慢调用阈</b>：响应时间超过阈值视为慢调用，慢调用比例过高触发熔断</li>
 *   <li><b>半开状</b>：熔断后等待一段时间进入半开状态，允许部分请求尝试</li>
 *   <li><b>恢复</b>：半开状态下请求成功率达标后恢复正常</li>
 * </ul>
 * 
 * <p>⚠️ 注意事项</p>
 * <ul>
 *   <li>fallbackMethod 必须与原方法在同一类中</li>
 *   <li>fallbackMethod 参数必须与原方法相同，最后可Throwable 参数</li>
 *   <li>不同业务建议使用不同name，以便独立熔</li>
 * </ul>
 * 
 * @author Brix Platform Authors Platform Team
 * @since v2.1
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CircuitProtected {
    
    /**
     * 熔断器名
     * 
     * <p>用于标识熔断器实例，同名的方法共享熔断状态</p>
     * <p>建议按服功能命名，如：fileStorage、caseService、notification</p>
     * 
     * @return 熔断器名
     */
    String name();
    
    /**
     * 降级方法
     * 
     * <p>熔断触发或异常时调用的降级方法</p>
     * <p>方法签名要求：与原方法相同的参数，可选最后加 Throwable 参数</p>
     * 
     * @return 降级方法
     */
    String fallbackMethod() default "";
    
    /**
     * 需要记录为失败的异常类
     * 
     * <p>默认所有异常都视为失败</p>
     * 
     * @return 异常类型数组
     */
    Class<? extends Throwable>[] recordFailureFor() default {Exception.class};
    
    /**
     * 不记录为失败的异常类
     * 
     * <p>这些异常不计入失败率统计（如业务异常 IllegalArgumentException）</p>
     * 
     * @return 异常类型数组
     */
    Class<? extends Throwable>[] ignoreExceptions() default {};
}
