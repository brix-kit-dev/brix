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

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.slf4j.MDC;

import io.runtime.sdk.event.IntegrationEvent;

/**
 * Fills the tenant and trace id of an integration event the direct bus sends (Kafka design §13 K2).
 */
final class IntegrationEventMetadataSupport {

    private IntegrationEventMetadataSupport() {
    }

    static void enrich(
            IntegrationEvent event,
            Supplier<Optional<String>> tenantIdProvider) {
        Objects.requireNonNull(event, "event cannot be null");
        ensureTenantId(event, tenantIdProvider != null ? tenantIdProvider : Optional::empty);
        ensureTraceId(event);
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
