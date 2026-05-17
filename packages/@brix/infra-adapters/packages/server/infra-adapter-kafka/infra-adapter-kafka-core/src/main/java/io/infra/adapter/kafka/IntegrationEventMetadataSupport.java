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
package io.infra.adapter.kafka;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.MDC;

import io.runtime.sdk.event.IntegrationEvent;

/**
 * Shared integration-event metadata enrichment and Kafka header builder.
 */
public final class IntegrationEventMetadataSupport {

    public static final String HEADER_EVENT_ID = "eventId";
    public static final String HEADER_EVENT_TYPE = "eventType";
    public static final String HEADER_TIMESTAMP = "timestamp";
    public static final String HEADER_SOURCE_MODULE = "sourceModule";
    public static final String HEADER_TENANT_ID = "tenantId";
    public static final String HEADER_SCHEMA_VERSION = "schemaVersion";
    public static final String HEADER_TRACE_ID = "traceId";
    public static final String HEADER_CORRELATION_ID = "correlationId";
    public static final String HEADER_ROUTING_KEY = "routingKey";

    private IntegrationEventMetadataSupport() {
    }

    public static void enrich(
            IntegrationEvent event,
            Supplier<Optional<String>> tenantIdProvider) {
        Objects.requireNonNull(event, "event cannot be null");
        ensureTenantId(event, tenantIdProvider != null ? tenantIdProvider : Optional::empty);
        ensureTraceId(event);
    }

    public static RecordHeaders buildHeaders(IntegrationEvent event) {
        RecordHeaders headers = new RecordHeaders();
        addHeader(headers, HEADER_EVENT_ID, event.getEventId());
        addHeader(headers, HEADER_EVENT_TYPE, event.getEventType());
        addHeader(headers, HEADER_TIMESTAMP, String.valueOf(event.getTimestamp().toEpochMilli()));
        addHeader(headers, HEADER_SOURCE_MODULE, event.getSourceModule());
        addHeader(headers, HEADER_TENANT_ID, event.getTenantId());
        addHeader(headers, HEADER_SCHEMA_VERSION, String.valueOf(event.getSchemaVersion()));
        addHeader(headers, HEADER_TRACE_ID, event.getTraceId());
        addHeader(headers, HEADER_CORRELATION_ID, event.getCorrelationId());
        addHeader(headers, HEADER_ROUTING_KEY, event.getRoutingKey());
        return headers;
    }

    public static void addHeader(RecordHeaders headers, String key, String value) {
        if (value != null && !value.isBlank()) {
            headers.add(key, value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void ensureTenantId(
            IntegrationEvent event,
            Supplier<Optional<String>> tenantIdProvider) {
        if (event.getTenantId() != null && !event.getTenantId().isBlank()) {
            return;
        }

        Optional<String> contextTenantId = tenantIdProvider.get();
        if (contextTenantId.isPresent() && !contextTenantId.get().isBlank()) {
            event.setTenantId(contextTenantId.get());
            return;
        }

        throw new IllegalArgumentException(
                "IntegrationEvent.tenantId is required but could not be resolved. "
                + "Either set tenantId explicitly on the event or ensure TenantContext "
                + "is available in the current thread. eventId=" + event.getEventId()
                + ", eventType=" + event.getEventType());
    }

    private static void ensureTraceId(IntegrationEvent event) {
        if (event.getTraceId() != null && !event.getTraceId().isBlank()) {
            return;
        }

        String mdcTraceId = MDC.get("traceId");
        if (mdcTraceId != null && !mdcTraceId.isBlank()) {
            event.setTraceId(mdcTraceId);
        } else {
            event.setTraceId(UUID.randomUUID().toString());
        }
    }
}
