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
package io.runtime.orchestrator.plugin;

import java.util.Objects;

import io.runtime.sdk.plugin.BrixHealth;
import io.runtime.sdk.plugin.PluginIdentity;
import io.runtime.sdk.plugin.PluginLifecycleState;

/**
 * Immutable runtime state for one plugin.
 *
 * <p>Readiness is deliberately separate from lifecycle state. A plugin can be
 * {@link PluginLifecycleState#STARTED} while not ready if required runtime
 * dependencies or health checks do not satisfy the Runtime Shell readiness
 * predicate.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class PluginRuntimeState {

    private final PluginIdentity identity;
    private final PluginLifecycleState lifecycleState;
    private final boolean ready;
    private final BrixHealth health;
    private final String detail;

    /**
     * Creates a plugin runtime state.
     *
     * @param identity plugin identity
     * @param lifecycleState plugin lifecycle state
     * @param ready readiness flag
     * @param health plugin health value
     * @param detail state detail
     */
    public PluginRuntimeState(
            PluginIdentity identity,
            PluginLifecycleState lifecycleState,
            boolean ready,
            BrixHealth health,
            String detail) {
        this.identity = Objects.requireNonNull(identity, "identity must not be null");
        this.lifecycleState = Objects.requireNonNull(lifecycleState, "lifecycleState must not be null");
        this.ready = ready;
        this.health = health != null ? health : BrixHealth.unknown("Plugin health is unavailable");
        this.detail = detail != null ? detail : "";
    }

    /**
     * Returns plugin identity.
     *
     * @return plugin identity
     */
    public PluginIdentity identity() {
        return identity;
    }

    /**
     * Returns lifecycle state.
     *
     * @return lifecycle state
     */
    public PluginLifecycleState lifecycleState() {
        return lifecycleState;
    }

    /**
     * Returns readiness flag.
     *
     * @return true when this plugin contributes ready state
     */
    public boolean ready() {
        return ready;
    }

    /**
     * Returns plugin health.
     *
     * @return plugin health
     */
    public BrixHealth health() {
        return health;
    }

    /**
     * Returns state detail.
     *
     * @return state detail
     */
    public String detail() {
        return detail;
    }
}
