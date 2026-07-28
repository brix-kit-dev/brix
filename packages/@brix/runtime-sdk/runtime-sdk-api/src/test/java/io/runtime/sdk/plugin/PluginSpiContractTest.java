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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

/**
 * Contract tests for the v3.0.10 plugin SPI.
 */
class PluginSpiContractTest {

    @Test
    void brixPluginDoesNotExposeManifestAccessor() {
        boolean hasManifestAccessor = Arrays.stream(BrixPlugin.class.getMethods())
            .map(Method::getName)
            .anyMatch("manifest"::equals);

        assertFalse(hasManifestAccessor);
    }

    @Test
    void pluginLifecycleStateDoesNotEncodeReadiness() {
        EnumSet<PluginLifecycleState> states = EnumSet.allOf(PluginLifecycleState.class);

        assertEquals(
            EnumSet.of(
                PluginLifecycleState.DISCOVERED,
                PluginLifecycleState.RESOLVED,
                PluginLifecycleState.WIRED,
                PluginLifecycleState.STARTED,
                PluginLifecycleState.DRAINING,
                PluginLifecycleState.STOPPED,
                PluginLifecycleState.FAILED
            ),
            states
        );
        assertFalse(Arrays.stream(PluginLifecycleState.values()).anyMatch(state -> "READY".equals(state.name())));
        assertFalse(Arrays.stream(PluginLifecycleState.values()).anyMatch(state -> "STARTING".equals(state.name())));
    }

    @Test
    void brixHealthUsesFourRuntimeShellStatuses() {
        assertEquals(
            EnumSet.of(
                BrixHealth.Status.UP,
                BrixHealth.Status.DEGRADED,
                BrixHealth.Status.DOWN,
                BrixHealth.Status.UNKNOWN
            ),
            EnumSet.allOf(BrixHealth.Status.class)
        );
        assertTrue(BrixHealth.up().isReadyStatus());
        assertTrue(BrixHealth.degraded("limited").isReadyStatus());
        assertFalse(BrixHealth.down("unavailable").isReadyStatus());
        assertFalse(BrixHealth.unknown("not checked").isReadyStatus());
    }

    @Test
    void pluginContextExposesRequiredOptionalAndIdentityOnly() {
        SampleCapability capability = new SampleCapability();
        PluginIdentity identity = new PluginIdentity("brix-app-sample");
        SamplePluginContext context = new SamplePluginContext(identity, capability);

        assertSame(capability, context.require(SampleCapability.class));
        assertEquals(Optional.of(capability), context.find(SampleCapability.class));
        assertSame(identity, context.pluginIdentity());
    }

    @Test
    void configureBindsCodeHandlersThroughBootstrapContext() {
        SamplePlugin plugin = new SamplePlugin();
        RecordingBootstrapContext bootstrap = new RecordingBootstrapContext();

        plugin.configure(bootstrap);

        assertEquals("sample.endpoint", bootstrap.endpointId);
        assertEquals("sample.query", bootstrap.queryId);
        assertEquals("sample.command", bootstrap.commandId);
        assertEquals("sample.subscription", bootstrap.subscriptionId);
        assertEquals("sample.task", bootstrap.taskId);
    }

    @Test
    void commandHandlerUsesFrozenInvocationAbi() throws NoSuchMethodException {
        Method handle = CommandHandler.class.getMethod("handle", CommandInvocation.class);

        assertEquals("handle", handle.getName());
        assertEquals("java.util.concurrent.CompletionStage", handle.getReturnType().getName());
        assertEquals(1L, Arrays.stream(CommandHandler.class.getMethods())
            .filter(method -> "handle".equals(method.getName()))
            .count());
    }

    private static final class SampleCapability {
    }

    private static final class SamplePluginContext implements PluginContext {
        private final PluginIdentity identity;
        private final SampleCapability capability;

        private SamplePluginContext(PluginIdentity identity, SampleCapability capability) {
            this.identity = identity;
            this.capability = capability;
        }

        @Override
        public <C> C require(Class<C> capabilityType) {
            return capabilityType.cast(capability);
        }

        @Override
        public <C> Optional<C> find(Class<C> capabilityType) {
            return Optional.of(capabilityType.cast(capability));
        }

        @Override
        public PluginIdentity pluginIdentity() {
            return identity;
        }
    }

    private static final class SamplePlugin implements BrixPlugin {
        @Override
        public void configure(PluginBootstrapContext bootstrap) {
            bootstrap.bindEndpoint("sample.endpoint", request -> "ok");
            bootstrap.bindQueryHandler("sample.query", query -> "answer");
            bootstrap.bindCommandHandler("sample.command", invocation -> CompletableFuture.completedFuture(null));
            bootstrap.bindEventHandler("sample.subscription", event -> { });
            bootstrap.bindTask("sample.task", () -> { });
        }

        @Override
        public void onStart(PluginContext context) {
            context.pluginIdentity();
        }

        @Override
        public void onStop() {
        }

        @Override
        public BrixHealth health() {
            return BrixHealth.up();
        }
    }

    private static final class RecordingBootstrapContext implements PluginBootstrapContext {
        private String endpointId;
        private String queryId;
        private String commandId;
        private String subscriptionId;
        private String taskId;

        @Override
        public void bindEndpoint(String manifestEndpointId, EndpointHandler<?, ?> handler) {
            this.endpointId = manifestEndpointId;
        }

        @Override
        public void bindQueryHandler(String manifestQueryId, QueryHandler<?, ?> handler) {
            this.queryId = manifestQueryId;
        }

        @Override
        public void bindCommandHandler(String manifestCommandId, CommandHandler<?> handler) {
            this.commandId = manifestCommandId;
        }

        @Override
        public void bindEventHandler(String manifestSubscriptionId, EventHandler<?> handler) {
            this.subscriptionId = manifestSubscriptionId;
        }

        @Override
        public void bindTask(String manifestTaskId, ManagedTask task) {
            this.taskId = manifestTaskId;
        }
    }
}
