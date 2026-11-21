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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import io.runtime.orchestrator.query.QuerySideEffects;
import io.runtime.orchestrator.capability.DefaultCapabilityRegistry;
import io.runtime.orchestrator.command.PluginCommandCapability;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.persistence.RecordingPersistence;
import io.runtime.sdk.capability.CommandCapability;
import io.runtime.sdk.capability.CommandSubmission;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.command.CommandRequest;
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

    @Test
    @SuppressWarnings("removal")
    void aPluginIsWarnedOnceAboutTheStringSubmissionHoweverOftenItLooksUpItsCommands() {
        // D170: every lookup builds a capability of its own, and they share the context's flag. The plugin's next
        // start has a context of its own, which warns again.
        CountingCommandBus bus = new CountingCommandBus();
        OwnerPersistenceProvider owners = new OwnerPersistenceProvider() {
            @Override
            public Optional<PersistenceCapability> forOwner(String ownerModuleId) {
                return Optional.empty();
            }

            @Override
            public Optional<CommandCapability> commandBusFor(String ownerModuleId) {
                return Optional.of(bus);
            }
        };
        PluginRuntimeDescriptor descriptor = PluginRuntimeDescriptor.builder("plugin-a")
            .requiredCapabilities(List.of(CommandCapability.class.getName())).build();
        DefaultPluginContext started = new DefaultPluginContext(descriptor, new DefaultCapabilityRegistry(), owners);
        DefaultPluginContext restarted = new DefaultPluginContext(descriptor, new DefaultCapabilityRegistry(), owners);
        CommandRequest<String> request = CommandRequest.of("plugin-b.reserve-stock", "sku-1", "order", "o-7");

        List<String> warnings = stringSubmissionWarnings(() -> {
            started.require(CommandCapability.class).submit(request);
            started.require(CommandCapability.class).submit(request);
            restarted.require(CommandCapability.class).submit(request);
        });

        assertEquals(2, warnings.size(), warnings.toString());
        assertTrue(warnings.stream().allMatch(line -> line.startsWith(
            "event=command.deprecated_submit sender=plugin-a type=plugin-b.reserve-stock ")), warnings.toString());
        assertEquals(3, bus.submitted);
    }

    /** The WARN lines the plugins' command capabilities log while the work runs. */
    private static List<String> stringSubmissionWarnings(Runnable work) {
        Logger logger = (Logger) LoggerFactory.getLogger(PluginCommandCapability.class);
        ListAppender<ILoggingEvent> logged = new ListAppender<>();
        logged.start();
        logger.addAppender(logged);
        try {
            work.run();
        } finally {
            logger.detachAppender(logged);
        }
        return logged.list.stream().filter(event -> event.getLevel() == Level.WARN)
            .map(ILoggingEvent::getFormattedMessage).toList();
    }

    interface TestCapability {
    }

    /** An outbox bus that counts the commands it is given. */
    private static final class CountingCommandBus implements CommandCapability {

        int submitted;

        @Override
        @SuppressWarnings("removal")
        public <C> CommandSubmission submit(CommandRequest<C> request) {
            submitted++;
            return null;
        }
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
