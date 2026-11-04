/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.event.ConsumedSubscription;
import io.runtime.orchestrator.event.EventSubscriptionTransport;
import io.runtime.orchestrator.event.ParkedEvent;
import io.runtime.orchestrator.event.SubscriptionDelivery;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.orchestrator.plugin.PluginRuntimeManager;
import io.runtime.sdk.command.CommandStatus;

/**
 * A sender reads its commands' status (root BRX-RS-MESSAGE-013; standard-link plan point 7, idempotency design §4.3,
 * §4.8): the provider's records first, then the provider's parked commands, then the sender's outbox, and nothing
 * about commands that are not its own.
 */
class CommandStatusReaderTest {

    private static final String TYPE = "sample-orders.cancel-order";
    private static final String SENDER = "sample-shipping";
    private static final OffsetDateTime AT = OffsetDateTime.parse("2026-11-04T03:00:00Z");
    private static final String OWN_SCOPE = CommandPolicies.ownerScope(SENDER, 42L, TYPE);

    private final Map<String, CommandRecord> records = new HashMap<>();
    private final Map<String, SentCommand> outbox = new HashMap<>();
    private final ParkingBroker broker = new ParkingBroker();
    private boolean providerPresent = true;

    private final CommandProviders providers = new CommandProviders() {
        @Override
        public boolean ready() {
            return true;
        }

        @Override
        public Optional<PluginRuntimeManager.CommandProvider> providerOf(String commandType) {
            return providerPresent && TYPE.equals(commandType)
                ? Optional.of(new PluginRuntimeManager.CommandProvider("sample-orders",
                    new PluginRuntimeDescriptor.ProvidedCommand(TYPE, "cancel-order.v1", "1.0.0", "event-standard",
                        "event-standard", "partition-serial", "command-envelope", "declared-consumers",
                        "persistent-inbox"), null, Object.class))
                : Optional.empty();
        }

        @Override
        public List<PluginRuntimeManager.CommandProvider> providers() {
            return List.of();
        }

        @Override
        public Optional<PluginRuntimeDescriptor.ConsumedCommand> consumed(String pluginId, String commandType) {
            return Optional.empty();
        }
    };

    private final OwnerCommandIdempotency store = new OwnerCommandIdempotency() {
        @Override
        public boolean reserve(String ownerId, String handlerId, CanonicalOutboxMessage message, String scope,
                String key, String fingerprint) {
            throw new UnsupportedOperationException("statuses only read");
        }

        @Override
        public Optional<CommandRecord> find(String ownerId, String handlerId, String commandId) {
            assertEquals("sample-orders", ownerId);
            assertEquals("cancel-order.v1", handlerId);
            return Optional.ofNullable(records.get(commandId));
        }
    };

    private final CommandStatusReader reader = new CommandStatusReader(providers, () -> store, () -> broker);

    @Test
    void theCommandHoldingItsKeySucceeded() {
        records.put("c-1", record("c-1", CommandStatus.State.SUCCEEDED, OWN_SCOPE, null, null));
        outbox.put("c-1", sent("c-1", "PUBLISHED", 42L));

        assertEquals(new CommandStatus("c-1", CommandStatus.State.SUCCEEDED, null, null, AT.toInstant()), read("c-1"));
    }

    @Test
    void aDuplicateNamesTheCommandHoldingTheKey() {
        records.put("c-2", record("c-2", CommandStatus.State.DUPLICATE, OWN_SCOPE, null, "c-1"));

        assertEquals(new CommandStatus("c-2", CommandStatus.State.DUPLICATE, null, "c-1", AT.toInstant()), read("c-2"));
    }

    @Test
    void anotherSendersOrTenantsRecordTellsNothing() {
        records.put("c-3", record("c-3", CommandStatus.State.SUCCEEDED,
            CommandPolicies.ownerScope("sample-billing", 42L, TYPE), null, null));
        records.put("c-4", record("c-4", CommandStatus.State.SUCCEEDED,
            CommandPolicies.ownerScope(SENDER, 43L, TYPE), null, null));
        outbox.put("c-4", sent("c-4", "PUBLISHED", 42L));

        assertEquals(CommandStatus.unknown("c-3"), read("c-3"));
        assertEquals(CommandStatus.unknown("c-4"), read("c-4"), "the provider's record wins over an outbox row");
    }

    @Test
    void aCommandTheProviderParkedReadsAsParkedWithItsCode() {
        broker.park("c-5", SENDER, 42L, SubscriptionDelivery.ENVELOPE_INVALID);
        outbox.put("c-5", sent("c-5", "PUBLISHED", 42L));

        assertEquals(new CommandStatus("c-5", CommandStatus.State.PARKED, SubscriptionDelivery.ENVELOPE_INVALID, null,
            AT.toInstant()), read("c-5"));
    }

    @Test
    void aCommandAnotherSenderParkedTellsNothing() {
        broker.park("c-6", "sample-billing", 42L, CommandDelivery.HANDLER_FAILED);
        broker.park("c-7", SENDER, 43L, CommandDelivery.HANDLER_FAILED);

        assertEquals(CommandStatus.unknown("c-6"), read("c-6"));
        assertEquals(CommandStatus.unknown("c-7"), read("c-7"));
    }

    @Test
    void untilTheProviderKnowsTheCommandItsSendersOutboxTells() {
        outbox.put("c-8", sent("c-8", "PENDING", 42L));
        outbox.put("c-9", sent("c-9", "PUBLISHED", 42L));
        outbox.put("c-10", new SentCommand("c-10", TYPE, "PARKED", 42L, CommandDelivery.HANDLER_FAILED, AT));
        outbox.put("c-11", new SentCommand("c-11", TYPE, "DISCARDED", 42L, CommandDelivery.HANDLER_FAILED, AT));

        assertEquals(CommandStatus.State.QUEUED, read("c-8").state());
        assertEquals(new CommandStatus("c-9", CommandStatus.State.QUEUED, null, null, AT.toInstant()), read("c-9"));
        assertEquals(new CommandStatus("c-10", CommandStatus.State.PARKED, CommandDelivery.HANDLER_FAILED, null,
            AT.toInstant()), read("c-10"));
        assertEquals(CommandStatus.State.DISCARDED, read("c-11").state());
    }

    @Test
    void anOutboxRowOfAnotherTenantOrTypeTellsNothing() {
        outbox.put("c-12", sent("c-12", "PENDING", 43L));
        outbox.put("c-13", new SentCommand("c-13", "other.command", "PENDING", 42L, null, AT));

        assertEquals(CommandStatus.unknown("c-12"), read("c-12"));
        assertEquals(CommandStatus.unknown("c-13"), read("c-13"));
    }

    @Test
    void withoutAProviderOnThisHostOnlyTheOutboxTells() {
        providerPresent = false;
        records.put("c-14", record("c-14", CommandStatus.State.SUCCEEDED, OWN_SCOPE, null, null));
        outbox.put("c-14", sent("c-14", "PENDING", 42L));

        assertEquals(CommandStatus.State.QUEUED, read("c-14").state());
    }

    @Test
    void whatNoSourceKnowsIsUnknown() {
        assertEquals(CommandStatus.unknown("c-15"), read("c-15"));
        assertEquals(CommandStatus.unknown("c-15"), new CommandStatusReader(providers, () -> null, () -> null)
            .status(SENDER, TYPE, 42L, "c-15", SentCommands.NONE), "a host without records or a broker");
        assertThrows(IllegalArgumentException.class, () -> read(" "));
    }

    private CommandStatus read(String commandId) {
        return reader.status(SENDER, TYPE, 42L, commandId, id -> Optional.ofNullable(outbox.get(id)));
    }

    private static CommandRecord record(String commandId, CommandStatus.State state, String scope, String errorCode,
            String holder) {
        return new CommandRecord(commandId, state, scope, "o-7", 42L, errorCode, holder, AT);
    }

    private static SentCommand sent(String commandId, String status, Long tenantId) {
        return new SentCommand(commandId, TYPE, status, tenantId, null, AT);
    }

    /** A broker transport that only answers which commands the provider parked. */
    private static final class ParkingBroker implements EventSubscriptionTransport {

        private final Map<String, CanonicalOutboxMessage> parked = new HashMap<>();
        private final Map<String, String> codes = new HashMap<>();

        void park(String commandId, String producer, Long tenantId, String errorCode) {
            parked.put(commandId, new CanonicalOutboxMessage(commandId, commandId, "COMMAND", TYPE, "1.0.0",
                "STANDARD", producer, tenantId == null ? "PLATFORM" : "TENANT", tenantId, "o-7", AT, commandId, null,
                null, null, "{}", 1));
            codes.put(commandId, errorCode);
        }

        @Override
        public Optional<ParkedEvent> parkedEvent(ConsumedSubscription subscription, String eventId) {
            assertEquals(new ConsumedSubscription("sample-orders", "cancel-order.v1", TYPE, ConsumedSubscription.COMMAND),
                subscription);
            return Optional.ofNullable(parked.get(eventId)).map(message -> new ParkedEvent(subscription.name(),
                eventId, TYPE, codes.get(eventId), 1, AT, "conformance"));
        }

        @Override
        public Optional<CanonicalOutboxMessage> parkedMessage(ConsumedSubscription subscription, String eventId) {
            return Optional.ofNullable(parked.get(eventId));
        }

        @Override
        public boolean ready() {
            return true;
        }

        @Override
        public void prepare(Set<String> eventTypes, List<ConsumedSubscription> subscriptions) {
        }

        @Override
        public Consumption consume(ConsumedSubscription subscription, SubscriptionDelivery delivery) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Map<String, List<ParkedEvent>> parked(int limit) {
            return Map.of();
        }

        @Override
        public void release(ConsumedSubscription subscription, String eventId) {
        }
    }
}
