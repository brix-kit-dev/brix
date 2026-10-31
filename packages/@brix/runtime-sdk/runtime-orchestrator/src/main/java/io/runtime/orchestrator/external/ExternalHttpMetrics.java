/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.external;

import java.time.Duration;

/**
 * What the runtime reports about external calls (root BRX-RS-OTEL-006; standard-link plan L8.4). Labels are the
 * module, the service and the outcome only, never URLs.
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public interface ExternalHttpMetrics {

    /** Records nothing. */
    ExternalHttpMetrics NOOP = new ExternalHttpMetrics() {
    };

    /**
     * A call ended, retries included.
     *
     * @param pluginId the calling module
     * @param serviceId the service
     * @param outcome {@code 2xx}, {@code 3xx}, {@code 4xx}, {@code 5xx}, or the failure: {@code target_forbidden},
     *     {@code deadline_exceeded}, {@code circuit_open}, {@code rejected} or {@code failed}
     * @param took how long the call took
     */
    default void finished(String pluginId, String serviceId, String outcome, Duration took) {
    }
}
