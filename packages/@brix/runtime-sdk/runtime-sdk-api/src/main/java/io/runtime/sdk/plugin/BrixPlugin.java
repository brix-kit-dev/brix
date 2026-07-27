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

/**
 * v3.0.10 backend plugin lifecycle entry point.
 *
 * <p>A plugin-server artifact publishes exactly one provider of this type through
 * {@link java.util.ServiceLoader}. The Runtime Shell associates that provider with
 * the plugin YAML manifest found in the artifact. This interface intentionally
 * does not expose a manifest or descriptor accessor; YAML remains the declarative
 * source of truth, while {@link #configure(PluginBootstrapContext)} provides the
 * code-side binding surface that the runtime validates against the manifest.</p>
 *
 * <p>Implementations must not obtain capabilities through Spring container APIs,
 * static registries, reflection, or host-specific branches. Runtime capabilities
 * are available only through {@link PluginContext} during startup and managed
 * execution.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public interface BrixPlugin {

    /**
     * Binds code handlers declared by the plugin YAML manifest.
     *
     * <p>The runtime calls this method during the WIRED stage and must reject
     * undeclared, duplicated, or missing bindings during manifest/code
     * bidirectional validation.</p>
     *
     * @param bootstrap binding context supplied by the Runtime Shell
     */
    void configure(PluginBootstrapContext bootstrap);

    /**
     * Starts already wired plugin resources.
     *
     * <p>This method must not register endpoints, handlers, subscriptions,
     * schedulers, or other unmanaged resources. Capability access is constrained
     * by the plugin YAML manifest and exposed through {@code context}.</p>
     *
     * @param context plugin capability context supplied by the Runtime Shell
     * @throws PluginStartException when plugin startup fails
     */
    void onStart(PluginContext context);

    /**
     * Stops managed plugin resources during drain or shutdown.
     */
    void onStop();

    /**
     * Returns the current plugin health.
     *
     * <p>Plugins must report a non-null health value. Runtime readiness is derived
     * separately from lifecycle state, required capabilities, required query
     * providers, command durability, and this health value.</p>
     *
     * @return current plugin health
     */
    BrixHealth health();
}
