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
package io.infra.adapter.kafka.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.runtime.sdk.event.IntegrationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Micrometer metrics helper for Kafka consumer-side observability.
 *
 * <p>Provides counter-based metrics for three key consumer lifecycle events:
 * successful consumption, retry attempts, and dead-letter queue (DLQ) routing.
 * These metrics fulfil <strong>Architecture Red Line 3</strong>
 * (all cross-plugin communication must be observable) and the
 * <strong>R3.7 Retry/DLQ Observability</strong> requirement from the blueprint.</p>
 *
 * <h3>Registered Counters</h3>
 * <table border="1">
 *   <tr><th>Metric Name</th><th>Tags</th><th>Description</th></tr>
 *   <tr>
 *     <td>{@code brix.event.consumed.total}</td>
 *     <td>eventType, sourceModule, tenantId</td>
 *     <td>Total events successfully consumed</td>
 *   </tr>
 *   <tr>
 *     <td>{@code brix.event.retry.count}</td>
 *     <td>eventType, attempt</td>
 *     <td>Consumer retry attempts</td>
 *   </tr>
 *   <tr>
 *     <td>{@code brix.event.dlq.count}</td>
 *     <td>eventType, sourceModule, errorType</td>
 *     <td>Events routed to the dead-letter queue</td>
 *   </tr>
 * </table>
 *
 * <h3>Architecture Alignment</h3>
 * <p>This class resides in <em>Layer 2C — Infra Adapter</em> and depends only on
 * {@code runtime-sdk-api} interfaces ({@link IntegrationEvent}) and Micrometer.
 * Plugin-level consumers inject this bean to record metrics without coupling to
 * any specific monitoring backend.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>This class is thread-safe. Micrometer counters are inherently safe for
 * concurrent increments.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 * @see io.runtime.sdk.event.IntegrationEvent
 */
public class KafkaConsumerMetrics {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerMetrics.class);

    /**
     * Counter name for successfully consumed events.
     */
    static final String METRIC_CONSUMED_TOTAL = "brix.event.consumed.total";

    /**
     * Counter name for consumer retry attempts.
     */
    static final String METRIC_RETRY_COUNT = "brix.event.retry.count";

    /**
     * Counter name for events routed to the dead-letter queue.
     */
    static final String METRIC_DLQ_COUNT = "brix.event.dlq.count";

    private final MeterRegistry meterRegistry;

    /**
     * Creates a new {@code KafkaConsumerMetrics} instance.
     *
     * @param meterRegistry the Micrometer meter registry for counter registration;
     *                      must not be {@code null}
     */
    public KafkaConsumerMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
    }

    /**
     * Records a successfully consumed integration event.
     *
     * <p>Increments the {@value #METRIC_CONSUMED_TOTAL} counter with tags
     * identifying the event type, source module, and tenant.</p>
     *
     * @param event the integration event that was successfully consumed;
     *              must not be {@code null}
     */
    public void recordConsumeSuccess(IntegrationEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        meterRegistry.counter(METRIC_CONSUMED_TOTAL,
                "eventType", event.getEventType(),
                "sourceModule", safeTag(event.getSourceModule()),
                "tenantId", safeTag(event.getTenantId())
        ).increment();
    }

    /**
     * Records a consumer retry attempt for an integration event.
     *
     * <p>Increments the {@value #METRIC_RETRY_COUNT} counter with the event type
     * and current retry attempt number. This counter helps operators detect
     * recurring consumer failures before events hit the DLQ.</p>
     *
     * @param event   the integration event being retried; must not be {@code null}
     * @param attempt the current retry attempt number (1-based)
     */
    public void recordConsumeRetry(IntegrationEvent event, int attempt) {
        Objects.requireNonNull(event, "event must not be null");
        meterRegistry.counter(METRIC_RETRY_COUNT,
                "eventType", event.getEventType(),
                "attempt", String.valueOf(attempt)
        ).increment();
    }

    /**
     * Records an event routed to the dead-letter queue (DLQ).
     *
     * <p>Increments the {@value #METRIC_DLQ_COUNT} counter and emits an
     * {@code ERROR}-level log entry. DLQ events indicate consumer-side failures
     * that exceeded the retry policy and require manual investigation.</p>
     *
     * @param event the integration event sent to the DLQ; must not be {@code null}
     * @param cause the exception that caused the DLQ routing; must not be {@code null}
     */
    public void recordConsumeDLQ(IntegrationEvent event, Throwable cause) {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(cause, "cause must not be null");
        meterRegistry.counter(METRIC_DLQ_COUNT,
                "eventType", event.getEventType(),
                "sourceModule", safeTag(event.getSourceModule()),
                "errorType", cause.getClass().getSimpleName()
        ).increment();
        log.error("Event sent to DLQ: eventId={}, type={}, cause={}",
                event.getEventId(), event.getEventType(), cause.getMessage());
    }

    /**
     * Returns a safe tag value, replacing {@code null} with {@code "unknown"}.
     *
     * <p>Micrometer does not accept {@code null} tag values. This helper prevents
     * {@link NullPointerException} when event metadata fields are absent.</p>
     *
     * @param value the raw tag value
     * @return the value itself, or {@code "unknown"} if {@code null}
     */
    private static String safeTag(String value) {
        return value != null ? value : "unknown";
    }
}
