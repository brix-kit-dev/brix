/*
 * Copyright 2026 Runtime SDK Authors
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
package io.runtime.sdk.capability;

import java.util.Map;

/**
 * 可观测性能力契约
 * 
 * <p>提供日志、指标、追踪的统一抽象接口，是云原生可观测性的核心能力。
 * 模块通过此接口记录运行时信息，无需感知底层实现（OpenTelemetry/Prometheus/Jaeger）。</p>
 * 
 * <h3>核心职责</h3>
 * <ul>
 *   <li><b>日志（Logging）</b>：结构化日志记录</li>
 *   <li><b>指标（Metrics）</b>：业务和技术指标采集</li>
 *   <li><b>追踪（Tracing）</b>：分布式链路追踪</li>
 * </ul>
 * 
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>低侵入性</b>：简洁的 API，不影响业务代码</li>
 *   <li><b>高性能</b>：异步采集，不阻塞主流程</li>
 *   <li><b>标准兼容</b>：基于 OpenTelemetry 标准</li>
 * </ul>
 * 
 * <h3>日志级别</h3>
 * <p>使用 {@link LogLevel} 枚举，从低到高：TRACE &lt; DEBUG &lt; INFO &lt; WARN &lt; ERROR</p>
 * 
 * <h3>指标类型</h3>
 * <ul>
 *   <li><b>Counter</b>：累加计数器，如请求总数</li>
 *   <li><b>Gauge</b>：瞬时值，如当前连接数</li>
 *   <li><b>Histogram</b>：分布统计，如响应时间</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Inject
 * private ObservabilityCapability observability;
 * 
 * public void processOrder(Order order) {
 *     // 记录日志
 *     observability.log(LogLevel.INFO, "Processing order: {}", order.getId());
 *     
 *     // 记录指标
 *     observability.recordMetric("orders.processed", 1, 
 *         Map.of("type", order.getType(), "region", order.getRegion()));
 *     
 *     // 添加追踪属性
 *     observability.addSpanAttribute("order.id", order.getId());
 *     observability.addSpanAttribute("order.amount", String.valueOf(order.getAmount()));
 * }
 * }</pre>
 * 
 * <h3>实现说明</h3>
 * <ul>
 *   <li>Full Product Host：OpenTelemetry + Prometheus + Jaeger</li>
 *   <li>Embedded Host：SLF4J 日志 + 基础指标</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public interface ObservabilityCapability {

    /**
     * 记录日志
     * 
     * <p>支持 SLF4J 风格的占位符 {}，自动处理参数替换</p>
     * 
     * @param level   日志级别
     * @param message 日志消息模板
     * @param args    消息参数，最后一个参数如果是 Throwable 会作为异常记录
     */
    void log(LogLevel level, String message, Object... args);

    /**
     * 记录指标
     * 
     * <p>指标命名规范：{模块}.{类别}.{名称}，如 "booking.api.requests"</p>
     * 
     * @param name  指标名称，使用点分隔的命名空间
     * @param value 指标值
     * @param tags  标签，用于多维度聚合
     */
    void recordMetric(String name, double value, Map<String, String> tags);

    /**
     * 获取当前 Span 上下文
     * 
     * <p>用于跨服务传递追踪信息</p>
     * 
     * @return 当前 Span 上下文，如果不在追踪上下文中返回空上下文
     */
    SpanContext currentSpan();

    /**
     * 添加 Span 属性
     * 
     * <p>为当前追踪 Span 添加业务属性，用于追踪分析</p>
     * 
     * @param key   属性键
     * @param value 属性值
     */
    void addSpanAttribute(String key, String value);

    /**
     * 记录 INFO 级别日志（便捷方法）
     * 
     * @param message 日志消息
     * @param args    消息参数
     */
    default void info(String message, Object... args) {
        log(LogLevel.INFO, message, args);
    }

    /**
     * 记录 WARN 级别日志（便捷方法）
     * 
     * @param message 日志消息
     * @param args    消息参数
     */
    default void warn(String message, Object... args) {
        log(LogLevel.WARN, message, args);
    }

    /**
     * 记录 ERROR 级别日志（便捷方法）
     * 
     * @param message 日志消息
     * @param args    消息参数
     */
    default void error(String message, Object... args) {
        log(LogLevel.ERROR, message, args);
    }

    /**
     * 记录 DEBUG 级别日志（便捷方法）
     * 
     * @param message 日志消息
     * @param args    消息参数
     */
    default void debug(String message, Object... args) {
        log(LogLevel.DEBUG, message, args);
    }

    /**
     * 记录无标签指标（便捷方法）
     * 
     * @param name  指标名称
     * @param value 指标值
     */
    default void recordMetric(String name, double value) {
        recordMetric(name, value, Map.of());
    }

    /**
     * 增加计数器（便捷方法）
     * 
     * @param name 计数器名称
     * @param tags 标签
     */
    default void incrementCounter(String name, Map<String, String> tags) {
        recordMetric(name, 1.0, tags);
    }
}
