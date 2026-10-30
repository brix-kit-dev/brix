/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.event;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * The platform's catalog of the policies an event subscription names in its manifest (root BRX-RS-MESSAGE-029,
 * BOOT-005; Kafka design §14 K20). A subscription that names a policy the runtime does not know is refused at
 * startup; the known ones take effect:
 *
 * <ul>
 *   <li>retry {@value #STANDARD}: the Host's attempts, exponential backoff and jitter
 *       ({@code brix.outbox.relay.*});</li>
 *   <li>timeout {@value #STANDARD}: the Host's delivery deadline;</li>
 *   <li>concurrency {@value #PARTITION_SERIAL}: one delivery at a time per partition key, the only order the
 *       platform keeps (MESSAGE-027);</li>
 *   <li>publisher {@code <pluginId>-owner}: the subscriber trusts the plugin that declares the event type, and the
 *       runtime checks that this is the plugin that does.</li>
 * </ul>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class EventPolicies {

    /** The platform's standard retry and timeout policy. */
    public static final String STANDARD = "event-standard";

    /** Deliveries of one partition key, one at a time and in order. */
    public static final String PARTITION_SERIAL = "partition-serial";

    /** Retry policies a subscription can name. */
    public static final Set<String> RETRY = Set.of(STANDARD);

    /** Timeout policies a subscription can name. */
    public static final Set<String> TIMEOUT = Set.of(STANDARD);

    /** Concurrency policies a subscription can name. */
    public static final Set<String> CONCURRENCY = Set.of(PARTITION_SERIAL);

    /**
     * Event types and handler ids end up in Kafka topic and consumer group names: letters, digits, dot, dash and
     * underscore, at most 100 characters, starting with a letter or digit.
     */
    public static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,99}");

    private static final String SCHEMA_PREFIX = "brix://schemas/";

    private EventPolicies() {
    }

    /**
     * Returns the publisher policy that trusts the given plugin.
     *
     * @param publisherPluginId the plugin that declares the event type
     * @return {@code <pluginId>-owner}
     */
    public static String publisherPolicyOf(String publisherPluginId) {
        return publisherPluginId + "-owner";
    }

    /**
     * Returns the classpath resource of a {@code brix://schemas/...} reference.
     *
     * @param schemaRef the manifest's {@code schemaRef}
     * @return {@code META-INF/brix/schemas/<path>.json}, or {@code null} when it is not a brix schema reference
     */
    public static String schemaResource(String schemaRef) {
        if (schemaRef == null || !schemaRef.startsWith(SCHEMA_PREFIX) || schemaRef.length() == SCHEMA_PREFIX.length()) {
            return null;
        }
        return "META-INF/brix/schemas/" + schemaRef.substring(SCHEMA_PREFIX.length()) + ".json";
    }
}
