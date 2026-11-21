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
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.runtime.manifest.validation.ManifestValidationException;

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
    void refusesAFieldTheManifestSchemaDoesNotAllowWithoutAnyHost(@TempDir Path dir) throws Exception {
        // Standard-link plan D152 (queue item 17): the runtime checks a plugin's manifest against the JSON Schema when
        // it reads it, not only a web host's plugin registry; a sent command names no handler (root MANIFEST-025).
        String manifest;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("META-INF/brix/plugin-manifest.yaml")) {
            manifest = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String sender = manifest.replace("commands:\n  provides: []\n  consumes: []\n", """
            commands:
              provides: []
              consumes:
                - commandType: other-plugin.ship-order
                  versionRange: ">=1.0.0 <2.0.0"
                  required: true
                  handlerId: service-loaded-test.ship-order.v1
            """);
        assertTrue(sender.contains("handlerId: service-loaded-test.ship-order.v1"), sender);
        Path file = Files.writeString(dir.resolve("plugin-manifest.yaml"), sender);
        Method load = ClasspathPluginRuntimeDescriptorResolver.class.getDeclaredMethod("loadActiveDescriptor", URL.class);
        load.setAccessible(true);
        ClasspathPluginRuntimeDescriptorResolver resolver =
            new ClasspathPluginRuntimeDescriptorResolver(Thread.currentThread().getContextClassLoader());

        InvocationTargetException failure = org.junit.jupiter.api.Assertions.assertThrows(
            InvocationTargetException.class, () -> load.invoke(resolver, file.toUri().toURL()));

        assertTrue(failure.getCause() instanceof ManifestValidationException, String.valueOf(failure.getCause()));
        assertTrue(failure.getCause().getMessage().contains("[commands.consumes[0].handlerId]"),
            failure.getCause().getMessage());
    }

    @Test
    void keepsTheRequestSchemaAProvidedCommandDeclares(@TempDir Path dir) throws Exception {
        // Standard-link plan queue item 20, D169: a sender's client checks its generated request type against it.
        String manifest;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("META-INF/brix/plugin-manifest.yaml")) {
            manifest = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        // A command provider keeps an inbox, so it needs persistence (root MANIFEST-025).
        String owner = manifest.replace("commands:\n  provides: []\n  consumes: []\n", """
            commands:
              provides:
                - commandType: service-loaded-test.reserve-stock
                  request:
                    schemaRef: brix://schemas/service-loaded-test/reserve-stock/1.2.0
                  handlerId: service-loaded-test.reserve-stock.v1
                  scopePolicyRef: command-envelope
                  accessPolicyRef: declared-consumers
                  timeoutPolicyRef: command-standard
                  concurrencyPolicyRef: partition-serial
                  retryPolicyRef: command-transient-retry
                  idempotencyPolicyRef: command-key
              consumes: []
            data:
              storageId: service_loaded_test
              inbox: service_loaded_test_inbox
            """).replace("    - id: TestCapability\n      version: \">=3.0.10 <4.0.0\"\n",
            "    - id: TestCapability\n      version: \">=3.0.10 <4.0.0\"\n"
                + "    - id: io.runtime.sdk.capability.PersistenceCapability\n      version: \">=3.2.0 <4.0.0\"\n");
        assertTrue(owner.contains("reserve-stock/1.2.0"), owner);
        assertTrue(owner.contains("- id: io.runtime.sdk.capability.PersistenceCapability\n"), owner);
        Path file = Files.writeString(dir.resolve("plugin-manifest.yaml"), owner);
        Method load = ClasspathPluginRuntimeDescriptorResolver.class.getDeclaredMethod("loadActiveDescriptor", URL.class);
        load.setAccessible(true);
        ClasspathPluginRuntimeDescriptorResolver resolver =
            new ClasspathPluginRuntimeDescriptorResolver(Thread.currentThread().getContextClassLoader());

        PluginRuntimeDescriptor descriptor = (PluginRuntimeDescriptor) load.invoke(resolver, file.toUri().toURL());

        PluginRuntimeDescriptor.ProvidedCommand provided =
            descriptor.providedCommands().get("service-loaded-test.reserve-stock");
        assertEquals("brix://schemas/service-loaded-test/reserve-stock/1.2.0", provided.requestSchemaRef());
        assertEquals("1.2.0", provided.version());
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
