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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.query.QuerySideEffects;
import io.runtime.orchestrator.capability.DefaultCapabilityRegistry;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.persistence.RecordingPersistence;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.event.DomainEvent;
import io.runtime.sdk.event.IntegrationEvent;

class DefaultPluginContextTest {

    @Test
    void returnsOnlyManifestDeclaredRequiredCapability() {
        TestCapability capability = new TestCapability() {
        };
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        registry.register(TestCapability.class, capability);
        DefaultPluginContext context = new DefaultPluginContext(
            PluginRuntimeDescriptor.builder("plugin-a")
                .requiredCapabilities(List.of(TestCapability.class.getSimpleName()))
                .build(),
            registry);

        assertSame(capability, context.require(TestCapability.class));
        assertThrows(PluginRuntimeException.class, () -> context.find(TestCapability.class));
    }

    @Test
    void optionalCapabilityCanBeAbsentWhenDeclared() {
        DefaultPluginContext context = new DefaultPluginContext(
            PluginRuntimeDescriptor.builder("plugin-a")
                .optionalCapabilities(List.of(TestCapability.class.getName()))
                .build(),
            new DefaultCapabilityRegistry());

        assertTrue(context.find(TestCapability.class).isEmpty());
    }

    @Test
    void rejectsUndeclaredCapabilityAccess() {
        DefaultPluginContext context = new DefaultPluginContext(
            PluginRuntimeDescriptor.builder("plugin-a").build(),
            new DefaultCapabilityRegistry());

        assertThrows(PluginRuntimeException.class, () -> context.require(TestCapability.class));
        assertThrows(PluginRuntimeException.class, () -> context.find(TestCapability.class));
    }

    @Test
    void persistenceIsResolvedForThePluginOwner() {
        RecordingPersistence persistence = new RecordingPersistence();
        DefaultPluginContext context = new DefaultPluginContext(
            PluginRuntimeDescriptor.builder("plugin-a")
                .requiredCapabilities(List.of(PersistenceCapability.class.getName()))
                .build(),
            new DefaultCapabilityRegistry(),
            owner -> "plugin-a".equals(owner) ? Optional.of(persistence) : Optional.empty());

        assertSame(persistence, context.require(PersistenceCapability.class));
    }

    @Test
    void requiredPersistenceWithoutOwnerUnitFails() {
        DefaultPluginContext context = new DefaultPluginContext(
            PluginRuntimeDescriptor.builder("plugin-a")
                .requiredCapabilities(List.of(PersistenceCapability.class.getName()))
                .build(),
            new DefaultCapabilityRegistry(),
            OwnerPersistenceProvider.none());

        assertThrows(PluginRuntimeException.class, () -> context.require(PersistenceCapability.class));
    }

    @Test
    void optionalPersistenceMayBeAbsent() {
        DefaultPluginContext context = new DefaultPluginContext(
            PluginRuntimeDescriptor.builder("plugin-a")
                .optionalCapabilities(List.of(PersistenceCapability.class.getSimpleName()))
                .build(),
            new DefaultCapabilityRegistry(),
            OwnerPersistenceProvider.none());

        assertTrue(context.find(PersistenceCapability.class).isEmpty());
    }

    @Test
    void aPluginWithItsOwnOutboxPublishesThroughItWhileOthersGetTheHostsBus() {
        EventBusCapability host = new SilentBus();
        EventBusCapability outbox = new SilentBus();
        DefaultCapabilityRegistry registry = new DefaultCapabilityRegistry();
        registry.register(EventBusCapability.class, host);
        OwnerPersistenceProvider owners = new OwnerPersistenceProvider() {
            @Override
            public Optional<PersistenceCapability> forOwner(String ownerModuleId) {
                return Optional.empty();
            }

            @Override
            public Optional<EventBusCapability> eventBusFor(String ownerModuleId) {
                return "plugin-a".equals(ownerModuleId) ? Optional.of(outbox) : Optional.empty();
            }
        };

        DefaultPluginContext publisher = new DefaultPluginContext(PluginRuntimeDescriptor.builder("plugin-a")
            .requiredCapabilities(List.of(EventBusCapability.class.getName())).build(), registry, owners);
        DefaultPluginContext other = new DefaultPluginContext(PluginRuntimeDescriptor.builder("plugin-b")
            .optionalCapabilities(List.of(EventBusCapability.class.getName())).build(), registry, owners);

        assertSame(outbox, QuerySideEffects.unwrap(publisher.require(EventBusCapability.class)));
        assertSame(host, QuerySideEffects.unwrap(other.find(EventBusCapability.class).orElseThrow()));
    }

    interface TestCapability {
    }

    private static final class SilentBus implements EventBusCapability {
        @Override
        public void publish(DomainEvent event) {
        }

        @Override
        public void publishIntegration(IntegrationEvent event) {
        }
    }
}
