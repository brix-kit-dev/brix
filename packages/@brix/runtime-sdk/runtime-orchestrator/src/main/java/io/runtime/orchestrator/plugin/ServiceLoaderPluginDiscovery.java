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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import io.runtime.sdk.plugin.BrixPlugin;

/**
 * Discovers backend plugin providers through Java {@link ServiceLoader}.
 *
 * <p>The Runtime Shell v3.0.10 baseline requires backend plugin-server artifacts
 * to publish exactly one {@link BrixPlugin} provider in
 * {@code META-INF/services/io.runtime.sdk.plugin.BrixPlugin}. This class only
 * discovers providers already present on the Host classpath; it never uses
 * Spring component scanning to find plugin entry points.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class ServiceLoaderPluginDiscovery {

    private final ClassLoader classLoader;

    /**
     * Creates discovery using the current thread context class loader.
     */
    public ServiceLoaderPluginDiscovery() {
        this(Thread.currentThread().getContextClassLoader());
    }

    /**
     * Creates discovery using an explicit class loader.
     *
     * @param classLoader class loader used to locate ServiceLoader providers
     */
    public ServiceLoaderPluginDiscovery(ClassLoader classLoader) {
        this.classLoader = classLoader != null ? classLoader : BrixPlugin.class.getClassLoader();
    }

    /**
     * Finds all {@link BrixPlugin} providers visible to the configured class loader.
     *
     * @return discovered plugin providers in ServiceLoader order
     * @throws PluginRuntimeException if provider metadata or construction fails
     */
    public List<BrixPlugin> discover() {
        try {
            ServiceLoader<BrixPlugin> loader = ServiceLoader.load(BrixPlugin.class, classLoader);
            List<BrixPlugin> plugins = new ArrayList<>();
            for (BrixPlugin plugin : loader) {
                plugins.add(Objects.requireNonNull(plugin, "ServiceLoader returned a null BrixPlugin"));
            }
            return List.copyOf(plugins);
        } catch (ServiceConfigurationError e) {
            throw new PluginRuntimeException("Failed to discover BrixPlugin providers via ServiceLoader", e);
        }
    }
}
