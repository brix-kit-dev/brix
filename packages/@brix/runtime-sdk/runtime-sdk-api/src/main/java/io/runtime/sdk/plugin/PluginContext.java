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

import java.util.Optional;

/**
 * Plugin-scoped capability access context.
 *
 * <p>This is the only runtime capability entry point available to v3.0.10
 * plugins. The Runtime Shell must expose only capabilities declared by the
 * plugin YAML manifest. Required capabilities are resolved before startup;
 * optional capabilities that are absent are represented by {@link Optional#empty()}.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public interface PluginContext {

    /**
     * Returns a required capability declared by the plugin manifest.
     *
     * @param capabilityType capability contract type
     * @param <C> capability type
     * @return capability proxy scoped to this plugin
     * @throws RuntimeException if the capability was not declared or resolution failed
     */
    <C> C require(Class<C> capabilityType);

    /**
     * Returns an optional capability declared by the plugin manifest.
     *
     * @param capabilityType capability contract type
     * @param <C> capability type
     * @return optional capability proxy scoped to this plugin
     * @throws RuntimeException if the capability was not declared
     */
    <C> Optional<C> find(Class<C> capabilityType);

    /**
     * Returns the runtime-verified plugin identity.
     *
     * @return plugin identity
     */
    PluginIdentity pluginIdentity();
}
