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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Optional;

import org.junit.jupiter.api.Test;

class ClasspathPluginRuntimeDescriptorResolverTest {

    @Test
    void resolvesSameArtifactPluginManifest() {
        ClasspathPluginRuntimeDescriptorResolver resolver =
            new ClasspathPluginRuntimeDescriptorResolver(Thread.currentThread().getContextClassLoader());

        Optional<PluginRuntimeDescriptor> descriptor = resolver.resolve(new ServiceLoadedTestPlugin());

        assertTrue(descriptor.isPresent());
        assertEquals("service-loaded-test", descriptor.get().identity().pluginId());
        assertTrue(descriptor.get().requiredCapabilities().contains("TestCapability"));
        assertTrue(descriptor.get().optionalCapabilities().contains("OptionalCapability"));
    }

    @Test
    void treatsSpringBootNestedJarManifestAsSameArtifact()
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        ClasspathPluginRuntimeDescriptorResolver resolver =
            new ClasspathPluginRuntimeDescriptorResolver(Thread.currentThread().getContextClassLoader());
        Method method = ClasspathPluginRuntimeDescriptorResolver.class.getDeclaredMethod(
            "belongsToCodeSource", URL.class, URL.class);
        method.setAccessible(true);

        URL codeSource = url("jar:nested:/app/app.jar/!BOOT-INF/lib/platform-tenant-3.2.0.jar!/");
        URL manifest = url("jar:nested:/app/app.jar/!BOOT-INF/lib/platform-tenant-3.2.0.jar!/META-INF/brix/plugin-manifest.yaml");

        assertTrue((Boolean) method.invoke(resolver, manifest, codeSource));
    }

    @Test
    void readsTheEndpointsTimeoutPolicyAndRefusesOneTheRuntimeDoesNotKnow() throws Exception {
        // Root BRX-RS-MANIFEST-026; standard-link plan L7.5: a policy the runtime cannot resolve stops the plugin.
        Method contract = ClasspathPluginRuntimeDescriptorResolver.class.getDeclaredMethod(
            "contract", String.class, io.runtime.manifest.model.PluginManifest.Route.class);
        contract.setAccessible(true);
        io.runtime.manifest.model.PluginManifest.Route route = new io.runtime.manifest.model.PluginManifest.Route();
        route.setEndpointId("plugin-x.read.v1");

        route.setTimeout(java.util.Map.of("policyRef", "interactive-read"));
        assertEquals("interactive-read",
            ((io.runtime.orchestrator.endpoint.EndpointContract) contract.invoke(null, "plugin-x", route))
                .timeoutPolicyRef());
        route.setTimeout(null);
        assertEquals("endpoint-default",
            ((io.runtime.orchestrator.endpoint.EndpointContract) contract.invoke(null, "plugin-x", route))
                .timeoutPolicyRef());
        route.setTimeout(java.util.Map.of("policyRef", "interactive-forever"));
        InvocationTargetException failure = org.junit.jupiter.api.Assertions.assertThrows(
            InvocationTargetException.class, () -> contract.invoke(null, "plugin-x", route));
        assertTrue(failure.getCause() instanceof PluginRuntimeException, String.valueOf(failure.getCause()));
        assertTrue(failure.getCause().getMessage().contains("unknown endpoint timeout policy"));
    }

    @Test
    void readsHowAnEndpointsResultsAreReplayedAndRefusesARetentionTheRuntimeDoesNotKnow() throws Exception {
        // Root BRX-RS-MANIFEST-018; standard-link plan point 10a, idempotency design D73.
        Method contract = ClasspathPluginRuntimeDescriptorResolver.class.getDeclaredMethod(
            "contract", String.class, io.runtime.manifest.model.PluginManifest.Route.class);
        contract.setAccessible(true);
        io.runtime.manifest.model.PluginManifest.Route route = new io.runtime.manifest.model.PluginManifest.Route();
        route.setEndpointId("plugin-x.create.v1");
        route.setHandlerId("plugin-x.create-handler.v1");
        io.runtime.orchestrator.endpoint.EndpointContract.Replay replay =
            new io.runtime.orchestrator.endpoint.EndpointContract.Replay("plugin-x.create-handler.v1",
                java.time.Duration.ofHours(24));

        route.setIdempotency(java.util.Map.of("mode", "not-applicable"));
        assertEquals(null, ((io.runtime.orchestrator.endpoint.EndpointContract) contract.invoke(null, "plugin-x", route))
            .replay());
        route.setIdempotency(java.util.Map.of("mode", "required"));
        assertEquals(replay, ((io.runtime.orchestrator.endpoint.EndpointContract) contract.invoke(null, "plugin-x", route))
            .replay());
        route.setIdempotency(java.util.Map.of("mode", "required", "retentionPolicyRef", "idempotency-24h"));
        assertEquals(replay, ((io.runtime.orchestrator.endpoint.EndpointContract) contract.invoke(null, "plugin-x", route))
            .replay());
        route.setIdempotency(java.util.Map.of("mode", "required", "retentionPolicyRef", "idempotency-forever"));
        InvocationTargetException failure = org.junit.jupiter.api.Assertions.assertThrows(
            InvocationTargetException.class, () -> contract.invoke(null, "plugin-x", route));
        assertTrue(failure.getCause() instanceof PluginRuntimeException, String.valueOf(failure.getCause()));
        assertTrue(failure.getCause().getMessage().contains("unknown endpoint retention policy"));
    }

    @Test
    void aPluginCannotAcceptTheBootstrapSession() throws Exception {
        // super-admin 4.0.1 SA-7; standard-link plan point 10b-7a (D97): only the platform's bootstrap entries may.
        Method accessPolicy = ClasspathPluginRuntimeDescriptorResolver.class.getDeclaredMethod(
            "accessPolicy", String.class, io.runtime.manifest.model.PluginManifest.Route.class);
        accessPolicy.setAccessible(true);
        io.runtime.manifest.model.PluginManifest.Route route = new io.runtime.manifest.model.PluginManifest.Route();
        route.setEndpointId("plugin-x.setup.v1");
        route.setAuthorization(java.util.Map.of("permissions", java.util.List.of("bootstrap:create-first-admin")));
        route.setTenantContext(java.util.Map.of("mode", "forbidden"));

        route.setAuthentication(java.util.Map.of("mode", "required", "policyRef", "platform-token"));
        assertEquals(io.runtime.orchestrator.endpoint.EndpointAccessPolicy.TokenPolicy.PLATFORM_TOKEN,
            ((io.runtime.orchestrator.endpoint.EndpointAccessPolicy) accessPolicy.invoke(null, "plugin-x", route))
                .tokenPolicy());
        route.setAuthentication(java.util.Map.of("mode", "required", "policyRef", "bootstrap-setup"));
        InvocationTargetException failure = org.junit.jupiter.api.Assertions.assertThrows(
            InvocationTargetException.class, () -> accessPolicy.invoke(null, "plugin-x", route));
        assertTrue(failure.getCause() instanceof PluginRuntimeException, String.valueOf(failure.getCause()));
        assertTrue(failure.getCause().getMessage().contains("only a platform operational module may declare"),
            failure.getCause().getMessage());
    }

    private static URL url(String value) {
        try {
            return new URL(null, value, new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL url) throws IOException {
                    throw new IOException("Connection is not available for synthetic test URLs");
                }
            });
        } catch (java.net.MalformedURLException e) {
            throw new IllegalArgumentException(value, e);
        }
    }
}
