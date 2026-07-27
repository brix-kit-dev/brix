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
package io.runtime.orchestrator.bootstrap;

import java.util.List;
import java.util.Objects;

import org.springframework.context.ConfigurableApplicationContext;

import io.runtime.orchestrator.plugin.PluginRuntimeManager;
import io.runtime.orchestrator.plugin.PluginRuntimeState;

/**
 * Host-facing Runtime Shell bootstrap API.
 *
 * <p>Host code uses this L2B API to start and stop the Runtime Shell plugin
 * chain. The API intentionally does not expose plugin implementations,
 * {@code CapabilityRegistry}, Spring container objects, or business types.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class RuntimeShellBootstrap {

    private final PluginRuntimeManager pluginRuntimeManager;

    /**
     * Creates a bootstrap API backed by a plugin runtime manager.
     *
     * @param pluginRuntimeManager plugin runtime manager
     */
    public RuntimeShellBootstrap(PluginRuntimeManager pluginRuntimeManager) {
        this.pluginRuntimeManager = Objects.requireNonNull(
            pluginRuntimeManager,
            "pluginRuntimeManager must not be null");
    }

    /**
     * Starts the Runtime Shell from a Spring Boot Host context.
     *
     * <p>This helper keeps Host entry points ultra-thin: Host code passes the
     * freshly created application context, while bootstrap lookup, failure
     * close, and exception propagation stay inside L2B Runtime.</p>
     *
     * @param applicationContext Spring application context
     * @return plugin runtime states after startup
     */
    public static List<PluginRuntimeState> start(ConfigurableApplicationContext applicationContext) {
        Objects.requireNonNull(applicationContext, "applicationContext must not be null");
        try {
            return applicationContext.getBean(RuntimeShellBootstrap.class).start();
        } catch (RuntimeException e) {
            applicationContext.close();
            throw e;
        }
    }

    /**
     * Starts Runtime Shell plugin discovery and lifecycle orchestration.
     *
     * @return plugin runtime states after startup
     */
    public List<PluginRuntimeState> start() {
        return pluginRuntimeManager.start();
    }

    /**
     * Stops the Runtime Shell plugin chain.
     */
    public void stop() {
        pluginRuntimeManager.stop();
    }

    /**
     * Returns Runtime Shell readiness.
     *
     * @return true when every required plugin is ready
     */
    public boolean ready() {
        return pluginRuntimeManager.ready();
    }

    /**
     * Returns runtime plugin states.
     *
     * @return immutable plugin states
     */
    public List<PluginRuntimeState> pluginStates() {
        return pluginRuntimeManager.states();
    }
}
