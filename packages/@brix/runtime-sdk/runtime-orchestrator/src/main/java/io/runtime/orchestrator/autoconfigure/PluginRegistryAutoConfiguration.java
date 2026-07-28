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
package io.runtime.orchestrator.autoconfigure;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.runtime.orchestrator.endpoint.PluginRegistryEndpoint;
import io.runtime.orchestrator.manifest.UIManifestLoader;
import io.runtime.orchestrator.registry.ModuleRegistry;
import io.runtime.sdk.capability.registry.CapabilityDescriptor;
import io.runtime.sdk.capability.registry.CapabilityRegistry;

/**
 * Auto-configuration for plugin registry endpoint.
 *
 * <h2>Architecture Position (Manifest-Driven Dynamic Discovery)</h2>
 * <p>
 * Auto-configures {@link PluginRegistryEndpoint}, providing /api/plugins REST endpoint.
 * Activates only in web applications when {@link ModuleRegistry} bean exists.
 * </p>
 *
 * <h2>Activation Conditions</h2>
 * <ul>
 *   <li>Web application (DispatcherServlet present)</li>
 *   <li>ModuleRegistry bean exists</li>
 *   <li>@RestController annotation support present</li>
 *   <li>Configuration brix.plugin-registry.enabled=true (default true)</li>
 * </ul>
 *
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * brix:
 *   plugin-registry:
 *     enabled: true
 * host:
 *   mode: product
 *   plugins:
 *     base-url: /plugins
 * }</pre>
 *
 * @author Runtime SDK Team
 * @since 3.0.5
 * @see PluginRegistryEndpoint
 */
@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnClass(RestController.class)
@ConditionalOnBean(ModuleRegistry.class)
@ConditionalOnProperty(prefix = "brix.plugin-registry", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PluginRegistryAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PluginRegistryAutoConfiguration.class);

    /**
     * Creates UI Manifest loader bean.
     *
     * @param objectMapper JSON serializer
     * @return UI Manifest loader
     */
    @Bean
    @ConditionalOnMissingBean(UIManifestLoader.class)
    public UIManifestLoader uiManifestLoader(ObjectMapper objectMapper) {
        log.info("Creating UIManifestLoader - loading UI manifests from classpath");
        return new UIManifestLoader(objectMapper);
    }

    /**
     * Bridges plugin-manifest declared {@code capabilities.required} to the running
     * {@link CapabilityRegistry}, enforcing fail-fast at startup
     * (Architecture Red-Line P0-2).
     *
     * <p>If a {@code CapabilityRegistry} bean is present in the context, this bean
     * builds an alphabet of {@code descriptor.name} + all {@code descriptor.aliases}
     * exposed by registered capabilities and asks {@link UIManifestLoader} to verify
     * every plugin's required-capability list against it. Any mismatch throws
     * {@link io.runtime.orchestrator.manifest.MissingRequiredCapabilityException} and
     * aborts Spring context startup.</p>
     *
     * <p>If no {@code CapabilityRegistry} is wired (e.g. test slices, embedded mode
     * with capability scanning disabled), verification is skipped with an explicit
     * INFO log line — never silently passed.</p>
     *
     * @param manifestLoader      loader holding the validated manifest map
     * @param capabilityRegistry  optional capability registry, injected lazily so
     *                            absence does not fail bean wiring
     * @return a marker object whose construction triggers the verification side-effect
     */
    @Bean
    public PluginCapabilityVerifier pluginCapabilityVerifier(
            UIManifestLoader manifestLoader,
            ObjectProvider<CapabilityRegistry> capabilityRegistry) {
        CapabilityRegistry registry = capabilityRegistry.getIfAvailable();
        if (registry == null) {
            log.info("No CapabilityRegistry bean found - skipping plugin required-capability verification");
            return new PluginCapabilityVerifier();
        }
        // Build the alphabet of capability identifiers the Host advertises.
        // Four identifier classes are accepted, in order of canonicity:
        //   (1) Contract-interface simple name (e.g. "EventBusCapability")
        //       -- the vendor-neutral, blueprint-canonical identifier; plugin
        //          manifests SHOULD declare requirements at this level so they
        //          remain decoupled from the concrete Adapter implementation
        //          (Blueprint v3.0.x §3 Capability Contract; Constraint P0-2).
        //   (2) Super-contract simple names walked transitively up the type
        //       hierarchy (e.g. an AuthCapability adapter also satisfies
        //       AuthContextCapability requirements). This mirrors OSGi
        //       service-registration where a service published for interface X
        //       is also discoverable for every super-interface of X — required
        //       for graceful evolution of contract names (e.g. AuthContextCapability
        //       → AuthCapability rename in 3.2.0).
        //   (3) Implementation name from @Capability(name="...") (e.g. "in-memory-event-bus")
        //       -- allows fine-grained selection when a plugin really requires
        //          a specific implementation flavor.
        //   (4) Implementation aliases (e.g. "simpleEventBus")
        //       -- backwards-compat / convenience names exposed by the adapter.
        Set<String> available = new HashSet<>();
        for (CapabilityDescriptor d : registry.getAllDescriptors()) {
            if (d.getType() != null) {
                collectCapabilitySupertypes(d.getType(), available);
            }
            if (d.getName() != null) {
                available.add(d.getName());
            }
            if (d.getAliases() != null) {
                available.addAll(d.getAliases());
            }
        }
        log.info("Verifying plugin required-capabilities against {} registered capability identifier(s)",
            available.size());
        manifestLoader.verifyRequiredCapabilities(available::contains);
        return new PluginCapabilityVerifier();
    }

    /**
     * Walks the type hierarchy of a Capability contract type and adds every
     * super-interface whose simple name ends with "Capability" into {@code sink}.
     *
     * <p>This implements OSGi-style transitive service publication: a service
     * registered for an interface is discoverable under that interface AND any
     * of its super-interfaces (within the Capability namespace). Without this,
     * plugins would have to know the latest concrete contract name even after
     * a contract rename / split (e.g. AuthContextCapability → AuthCapability).</p>
     */
    private static void collectCapabilitySupertypes(Class<?> type, Set<String> sink) {
        if (type == null || type == Object.class) {
            return;
        }
        String simpleName = type.getSimpleName();
        if (simpleName.endsWith("Capability")) {
            sink.add(simpleName);
            sink.add(type.getName());
        }
        for (Class<?> iface : type.getInterfaces()) {
            collectCapabilitySupertypes(iface, sink);
        }
        // For impls registered with their concrete class, also walk superclass.
        collectCapabilitySupertypes(type.getSuperclass(), sink);
    }

    /**
     * Internal marker bean — its sole purpose is to provide a Spring lifecycle hook
     * for {@link #pluginCapabilityVerifier(UIManifestLoader, ObjectProvider)}.
     * Consumer code should not depend on it.
     */
    public static final class PluginCapabilityVerifier {
        PluginCapabilityVerifier() {
            // Intentionally package-private — produced exclusively by the autoconfiguration above.
        }
    }

    /**
     * Creates plugin registry endpoint bean.
     *
     * @param moduleRegistry module registry
     * @param manifestLoader UI Manifest loader
     * @return plugin registry endpoint
     */
    @Bean
    @ConditionalOnMissingBean(PluginRegistryEndpoint.class)
    public PluginRegistryEndpoint pluginRegistryEndpoint(
            ModuleRegistry moduleRegistry,
            UIManifestLoader manifestLoader) {
        log.info("Creating PluginRegistryEndpoint - /api/plugins endpoint will be available");
        return new PluginRegistryEndpoint(moduleRegistry, manifestLoader);
    }
}
