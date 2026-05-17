/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.runtime.sdk.event;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the schema version of an {@link IntegrationEvent} subclass and the
 * minimum schema version that the producer commits to remain wire-compatible
 * with on the consumer side.
 *
 * <p><b>Why a class-level annotation instead of relying on the runtime field?</b>
 * The {@link IntegrationEvent#getSchemaVersion()} field is a per-instance value
 * that the producer can mutate. Consumers, however, need a <i>compile-time
 * declared contract</i> against which to validate incoming payloads. This
 * annotation gives the platform a static, reflectable source of truth that can
 * be picked up by:</p>
 * <ul>
 *   <li><b>Build-time tooling</b> &mdash; CI guards that flag breaking schema
 *       changes when {@link #value()} is incremented across a MAJOR boundary
 *       without bumping {@link #minCompatible()}.</li>
 *   <li><b>EventBus adapters</b> &mdash; on subscribe, drop or quarantine
 *       events whose runtime {@code schemaVersion} is below
 *       {@link #minCompatible()} (with a WARN log; never silently swallow).</li>
 *   <li><b>Schema registries</b> &mdash; upload contract metadata derived from
 *       the annotation rather than from arbitrary instance values.</li>
 * </ul>
 *
 * <h3>Versioning Protocol (red-line R3.6)</h3>
 * <ul>
 *   <li><b>MAJOR (multiples of 10: 10, 20, 30 ...)</b>: Breaking change. Removed
 *       fields, renamed fields, semantics changed. Consumers running with a
 *       schema version below the producer's MAJOR boundary <b>must</b> reject
 *       the event (see {@link IntegrationEvent#MIN_SUPPORTED_SCHEMA_VERSION}).</li>
 *   <li><b>MINOR (1-9 between MAJOR boundaries)</b>: Additive only. New optional
 *       fields with documented defaults. Consumers <b>must</b> remain
 *       backward-compatible across MINOR increments within the same MAJOR
 *       window.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * @SchemaVersion(value = 1, minCompatible = 1)
 * public class UserCreatedIntegrationEvent extends IntegrationEvent { ... }
 *
 * // After an additive change (new optional field): bump value, keep minCompatible
 * @SchemaVersion(value = 2, minCompatible = 1)
 *
 * // After a breaking change (field removed): bump value across MAJOR boundary,
 * // and raise minCompatible accordingly so older consumers are rejected
 * @SchemaVersion(value = 10, minCompatible = 10)
 * }</pre>
 *
 * @author Runtime SDK Team
 * @since 3.2.0
 * @see IntegrationEvent#MIN_SUPPORTED_SCHEMA_VERSION
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SchemaVersion {

    /**
     * Current schema version of the annotated event class.
     *
     * <p>Must be a positive integer. Producers stamp this value into the
     * runtime {@link IntegrationEvent#getSchemaVersion()} field on
     * construction (typically through an EventBus adapter that reads the
     * annotation reflectively).</p>
     *
     * @return current schema version (>= 1)
     */
    int value();

    /**
     * Minimum schema version the producer guarantees backward compatibility
     * with. Consumers running below this value <b>must</b> reject the event.
     *
     * <p>Defaults to {@code 1}, i.e. compatible with the original schema.</p>
     *
     * @return minimum supported (compatible) schema version (>= 1)
     */
    int minCompatible() default 1;
}
