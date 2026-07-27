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
 * Plugin health snapshot separated into liveness and readiness signals.
 *
 * <p>Lifecycle state and readiness remain separate. This snapshot can be used by
 * runtime-facing APIs without turning readiness into a lifecycle state.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class PluginHealth {

    private final BrixHealth liveness;
    private final BrixHealth readiness;

    /**
     * Creates a plugin health snapshot.
     *
     * @param liveness liveness health
     * @param readiness readiness health
     */
    public PluginHealth(BrixHealth liveness, BrixHealth readiness) {
        this.liveness = Objects.requireNonNull(liveness, "liveness must not be null");
        this.readiness = Objects.requireNonNull(readiness, "readiness must not be null");
    }

    /**
     * Returns the liveness health.
     *
     * @return liveness health
     */
    public BrixHealth liveness() {
        return liveness;
    }

    /**
     * Returns the readiness health.
     *
     * @return readiness health
     */
    public BrixHealth readiness() {
        return readiness;
    }
}
