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
 * Span Context
 * 
 * <p>Encapsulates Span information for distributed tracing, used to propagate
 * tracing context across services. Designed based on W3C Trace Context standard.</p>
 * 
 * <h3>Core Fields</h3>
 * <ul>
 *   <li><b>traceId</b>: Trace ID, spans the entire request chain</li>
 *   <li><b>spanId</b>: Current Span ID</li>
 *   <li><b>parentSpanId</b>: Parent Span ID, used to build call tree</li>
 * </ul>
 * 
 * <h3>Use Cases</h3>
 * <ul>
 *   <li>Propagate tracing context in cross-service calls</li>
 *   <li>Associate async tasks with original requests</li>
 *   <li>Log correlation analysis</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see ObservabilityCapability#currentSpan()
 */
public final class SpanContext {

    /**
     * Empty context singleton
     */
    private static final SpanContext EMPTY = new SpanContext(null, null, null, false);

    /**
     * Trace ID (32-character hex string)
     */
    private final String traceId;

    /**
     * Current Span ID (16-character hex string)
     */
    private final String spanId;

    /**
     * Parent Span ID
     */
    private final String parentSpanId;

    /**
     * Whether sampled
     */
    private final boolean sampled;

    /**
     * Create Span context
     * 
     * @param traceId      trace ID
     * @param spanId       current Span ID
     * @param parentSpanId parent Span ID
     * @param sampled      whether sampled
     */
    public SpanContext(String traceId, String spanId, String parentSpanId, boolean sampled) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.parentSpanId = parentSpanId;
        this.sampled = sampled;
    }

    /**
     * Get empty context
     * 
     * @return empty SpanContext instance
     */
    public static SpanContext empty() {
        return EMPTY;
    }

    /**
     * Create new Span context
     * 
     * @param traceId trace ID
     * @param spanId  Span ID
     * @return SpanContext instance
     */
    public static SpanContext create(String traceId, String spanId) {
        return new SpanContext(traceId, spanId, null, true);
    }

    /**
     * Check if context is valid
     * 
     * @return true if both traceId and spanId are non-empty
     */
    public boolean isValid() {
        return traceId != null && !traceId.isBlank() 
            && spanId != null && !spanId.isBlank();
    }

    /**
     * Get trace ID
     * 
     * @return trace ID, may be null
     */
    public String getTraceId() {
        return traceId;
    }

    /**
     * Get current Span ID
     * 
     * @return Span ID, may be null
     */
    public String getSpanId() {
        return spanId;
    }

    /**
     * Get parent Span ID
     * 
     * @return parent Span ID, may be null
     */
    public String getParentSpanId() {
        return parentSpanId;
    }

    /**
     * Whether sampled
     * 
     * @return true if sampled
     */
    public boolean isSampled() {
        return sampled;
    }

    /**
     * Convert to W3C traceparent format
     * 
     * <p>Format: {version}-{traceId}-{spanId}-{flags}</p>
     * 
     * @return traceparent string, null if context is invalid
     */
    public String toTraceParent() {
        if (!isValid()) {
            return null;
        }
        String flags = sampled ? "01" : "00";
        return String.format("00-%s-%s-%s", traceId, spanId, flags);
    }

    /**
     * Parse from W3C traceparent format
     * 
     * @param traceParent traceparent string
     * @return parsed SpanContext, empty context on parse failure
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
