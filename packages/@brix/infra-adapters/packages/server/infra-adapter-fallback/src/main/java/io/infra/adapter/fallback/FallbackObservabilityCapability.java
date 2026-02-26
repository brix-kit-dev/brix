/*
 * Copyright 2026 Brix Authors
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
package io.infra.adapter.fallback;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.sdk.capability.LogLevel;
import io.runtime.sdk.capability.ObservabilityCapability;
import io.runtime.sdk.capability.SpanContext;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;

/**
 * Fallback 可观测性能力实现
 * 
 * <p>基于 SLF4J 的简单日志实现。指标和追踪仅输出到日志。</p>
 * 
 * <p>生产环境建议使用 infra-adapter-otel 提供的 OpenTelemetry 实现。</p>
 * 
 * @author Brix Team
 * @version 3.0.0
 */
@Capability(
    type = ObservabilityCapability.class,
    name = "fallback-observability",
    description = "基于 SLF4J 的 Fallback 可观测性实现",
    level = CapabilityLevel.EXPERIMENTAL,
    aliases = {"fallbackObservability"}
)
public class FallbackObservabilityCapability implements ObservabilityCapability {

    private static final Logger log = LoggerFactory.getLogger("io.brix.observability");

    @Override
    public void log(LogLevel level, String message, Object... args) {
        switch (level) {
            case DEBUG -> log.debug(message, args);
            case INFO -> log.info(message, args);
            case WARN -> log.warn(message, args);
            case ERROR -> log.error(message, args);
            default -> log.trace(message, args);
        }
    }

    @Override
    public void recordMetric(String name, double value, Map<String, String> tags) {
        log.debug("[Metric] {} = {} tags={}", name, value, tags);
    }

    @Override
    public SpanContext currentSpan() {
        return SpanContext.empty();
    }

    @Override
    public void addSpanAttribute(String key, String value) {
        log.debug("[Span] attribute: {} = {}", key, value);
    }
}
