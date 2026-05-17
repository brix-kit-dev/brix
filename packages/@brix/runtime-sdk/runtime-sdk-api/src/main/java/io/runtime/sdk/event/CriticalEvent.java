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
package io.runtime.sdk.event;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an {@link IntegrationEvent} subclass as <em>critical</em>, indicating that
 * it requires guaranteed delivery via the Outbox pattern.
 *
 * <p>When an event class is annotated with {@code @CriticalEvent}, the infrastructure
 * layer's AOP aspect automatically routes calls to
 * {@link io.runtime.sdk.capability.EventBusCapability#publishIntegration(IntegrationEvent)}
 * through the transactional Outbox publisher instead of the default Kafka fire-and-forget
 * path.  This ensures at-least-once delivery with full data-consistency guarantees
 * (business data and event record are committed in the same database transaction).</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * @CriticalEvent
 * public class UserDisabledEvent extends IntegrationEvent {
 *     // ...
 * }
 * }</pre>
 *
 * <h3>Architecture Alignment</h3>
 * <p>This annotation resides in <em>Layer 2A — Contract Layer</em>
 * ({@code runtime-sdk-api}).  The AOP interception that honours it lives in
 * <em>Layer 2C — Infra Adapter</em> ({@code infra-adapter-kafka-outbox}).
 * Plugins only need to annotate their event classes; no direct dependency on
 * the Outbox module is required (R13.4 compliance).</p>
 *
 * <h3>Incremental Adoption</h3>
 * <p>Existing events that are <strong>not</strong> annotated remain unaffected and
 * continue to use the default publish path.  Teams can adopt {@code @CriticalEvent}
 * incrementally by annotating business-critical events one at a time.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 * @see IntegrationEvent
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CriticalEvent {

    /**
     * Whether this event must go through the Outbox pattern for guaranteed delivery.
     *
     * <p>Defaults to {@code true}.  Setting to {@code false} effectively disables
     * Outbox routing while keeping the annotation as documentation intent.</p>
     *
     * @return {@code true} if Outbox routing is required
     */
    boolean outbox() default true;
}
