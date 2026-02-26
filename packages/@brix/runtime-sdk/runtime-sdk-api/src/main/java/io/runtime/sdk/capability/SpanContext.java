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

/**
 * Span 上下文
 * 
 * <p>封装分布式追踪的 Span 信息，用于跨服务传递追踪上下文。
 * 基于 W3C Trace Context 标准设计。</p>
 * 
 * <h3>核心字段</h3>
 * <ul>
 *   <li><b>traceId</b>：追踪 ID，贯穿整个请求链路</li>
 *   <li><b>spanId</b>：当前 Span ID</li>
 *   <li><b>parentSpanId</b>：父 Span ID，用于构建调用树</li>
 * </ul>
 * 
 * <h3>使用场景</h3>
 * <ul>
 *   <li>跨服务调用时传递追踪上下文</li>
 *   <li>异步任务关联原始请求</li>
 *   <li>日志关联分析</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see ObservabilityCapability#currentSpan()
 */
public final class SpanContext {

    /**
     * 空上下文单例
     */
    private static final SpanContext EMPTY = new SpanContext(null, null, null, false);

    /**
     * 追踪 ID（32 位十六进制字符串）
     */
    private final String traceId;

    /**
     * 当前 Span ID（16 位十六进制字符串）
     */
    private final String spanId;

    /**
     * 父 Span ID
     */
    private final String parentSpanId;

    /**
     * 是否采样
     */
    private final boolean sampled;

    /**
     * 创建 Span 上下文
     * 
     * @param traceId      追踪 ID
     * @param spanId       当前 Span ID
     * @param parentSpanId 父 Span ID
     * @param sampled      是否采样
     */
    public SpanContext(String traceId, String spanId, String parentSpanId, boolean sampled) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.sampled = sampled;
    }

    /**
     * 获取空上下文
     * 
     * @return 空的 SpanContext 实例
     */
    public static SpanContext empty() {
        return EMPTY;
    }

    /**
     * 创建新的 Span 上下文
     * 
     * @param traceId 追踪 ID
     * @param spanId  Span ID
     * @return SpanContext 实例
     */
    public static SpanContext create(String traceId, String spanId) {
        return new SpanContext(traceId, spanId, null, true);
    }

    /**
     * 检查上下文是否有效
     * 
     * @return 如果 traceId 和 spanId 都不为空返回 true
     */
    public boolean isValid() {
        return traceId != null && !traceId.isBlank() 
            && spanId != null && !spanId.isBlank();
    }

    /**
     * 获取追踪 ID
     * 
     * @return 追踪 ID，可能为 null
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * 获取当前 Span ID
     * 
     * @return Span ID，可能为 null
     */
    public String getSpanId() {
        return spanId;
    }

    /**
     * 获取父 Span ID
     * 
     * @return 父 Span ID，可能为 null
     */
    public String getParentSpanId() {
        return parentSpanId;
    }

    /**
     * 是否被采样
     * 
     * @return 如果被采样返回 true
     */
    public boolean isSampled() {
        return sampled;
    }

    /**
     * 转换为 W3C traceparent 格式
     * 
     * <p>格式：{version}-{traceId}-{spanId}-{flags}</p>
     * 
     * @return traceparent 字符串，如果上下文无效返回 null
     */
    public String toTraceParent() {
        if (!isValid()) {
            return null;
        }
        String flags = sampled ? "01" : "00";
        return String.format("00-%s-%s-%s", traceId, spanId, flags);
    }

    /**
     * 从 W3C traceparent 格式解析
     * 
     * @param traceParent traceparent 字符串
     * @return 解析后的 SpanContext，解析失败返回空上下文
     */
    public static SpanContext fromTraceParent(String traceParent) {
        if (traceParent == null || traceParent.isBlank()) {
            return empty();
        }
        
        String[] parts = traceParent.split("-");
        if (parts.length < 4) {
            return empty();
        }
        
        try {
            String traceId = parts[1];
            String spanId = parts[2];
            boolean sampled = "01".equals(parts[3]);
            return new SpanContext(traceId, spanId, null, sampled);
        } catch (Exception e) {
            return empty();
        }
    }

    @Override
    public String toString() {
        if (!isValid()) {
            return "SpanContext[empty]";
        }
        return String.format("SpanContext[traceId=%s, spanId=%s, sampled=%s]", 
                traceId, spanId, sampled);
    }
}
