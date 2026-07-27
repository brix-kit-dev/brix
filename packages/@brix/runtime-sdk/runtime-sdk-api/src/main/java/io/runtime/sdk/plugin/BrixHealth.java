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
package io.runtime.sdk.plugin;

import java.util.Objects;

/**
 * Health value reported by a v3.0.10 plugin.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class BrixHealth {

    /**
     * Plugin health status values defined by the v3.0.10 Runtime Shell baseline.
     */
    public enum Status {
        /**
         * The plugin is fully healthy.
         */
        UP,

        /**
         * The plugin can serve core traffic with reduced behavior.
         */
        DEGRADED,

        /**
         * The plugin cannot serve traffic.
         */
        DOWN,

        /**
         * The plugin health cannot be determined.
         */
        UNKNOWN
    }

    private static final String DEFAULT_MESSAGE = "No health details reported";

    private final Status status;
    private final String message;

    private BrixHealth(Status status, String message) {
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.message = normalizeMessage(message);
    }

    /**
     * Creates an UP health value.
     *
     * @return UP health value
     */
    public static BrixHealth up() {
        return new BrixHealth(Status.UP, DEFAULT_MESSAGE);
    }

    /**
     * Creates a DEGRADED health value.
     *
     * @param message health detail message
     * @return DEGRADED health value
     */
    public static BrixHealth degraded(String message) {
        return new BrixHealth(Status.DEGRADED, message);
    }

    /**
     * Creates a DOWN health value.
     *
     * @param message health detail message
     * @return DOWN health value
     */
    public static BrixHealth down(String message) {
        return new BrixHealth(Status.DOWN, message);
    }

    /**
     * Creates an UNKNOWN health value.
     *
     * @param message health detail message
     * @return UNKNOWN health value
     */
    public static BrixHealth unknown(String message) {
        return new BrixHealth(Status.UNKNOWN, message);
    }

    /**
     * Returns the health status.
     *
     * @return health status
     */
    public Status status() {
        return status;
    }

    /**
     * Returns the health detail message.
     *
     * @return health detail message
     */
    public String message() {
        return message;
    }

    /**
     * Returns whether the status can contribute to plugin readiness.
     *
     * @return true when the health status is UP or DEGRADED
     */
    public boolean isReadyStatus() {
        return status == Status.UP || status == Status.DEGRADED;
    }

    private static String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            return DEFAULT_MESSAGE;
        }
        return message;
    }
}
