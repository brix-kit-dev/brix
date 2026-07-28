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
 * Legacy marker for an {@link IntegrationEvent} subclass that documents a critical
 * delivery intent.
 *
 * <p>This annotation is not the source of truth for reliable delivery. Under the
 * v3.0.10 Runtime Shell baseline, CRITICAL/STANDARD/BEST_EFFORT is declared in
 * {@code META-INF/brix/plugin-manifest.yaml} and enforced by L2B Runtime startup
 * validation. Adapter or AOP behavior must not infer reliability from this
 * annotation alone.</p>
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
 * <p>This annotation resides in <em>Layer 2A - Contract Layer</em>
 * ({@code runtime-sdk-api}) for backward compatibility. Plugins must still
 * publish through {@link io.runtime.sdk.capability.EventBusCapability} and must
 * not depend on Outbox, Relay, broker, or adapter implementation types.</p>
 *
 * <h3>Incremental Adoption</h3>
 * <p>Existing events that are <strong>not</strong> annotated remain unaffected.
 * Teams must adopt reliable delivery by updating the plugin manifest and the
 * Runtime-managed publish path.</p>
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
     * Whether this event documents an outbox requirement.
     *
     * <p>Defaults to {@code true}. This value is compatibility metadata only and
     * must not override the active plugin manifest reliability declaration.</p>
     *
     * @return {@code true} if Outbox routing is required
     */
    boolean outbox() default true;
}
