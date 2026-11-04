/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import io.runtime.orchestrator.event.ConsumedSubscription;
import io.runtime.orchestrator.event.EventDeliveryException;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.orchestrator.plugin.PluginRuntimeManager;
import io.runtime.orchestrator.tracing.MessageTracing;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.capability.UnitOfWork;
import io.runtime.sdk.command.CommandStatus;
import io.runtime.sdk.plugin.CommandHandler;
import io.runtime.sdk.plugin.CommandInvocation;
import io.runtime.sdk.plugin.IdempotencyContext;

/**
 * Kafka design §14 K22 (root BRX-RS-MESSAGE-010 to MESSAGE-016): a command reaches its one provider, once per command
 * id and once per business key, from a submitter that declares it.
 */
class CommandDeliveryTest {

    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");
    private static final ObjectMapper JSON = new ObjectMapper();

    record Reserve(String sku, int quantity) {
    }

    static final class ReserveHandler implements CommandHandler<Reserve> {
        final List<CommandInvocation<Reserve>> handled = new ArrayList<>();
        RuntimeException failure;
        Runnable whileHandling = () -> { };

        @Override
        public CompletionStage<Void> handle(CommandInvocation<Reserve> invocation) {
            if (failure != null) {
                throw failure;
            }
            whileHandling.run();
            handled.add(invocation);
            return CompletableFuture.completedFuture(null);
        }
    }

    private final ReserveHandler handler = new ReserveHandler();
    private final Set<String> inbox = new HashSet<>();
    private final Set<String> keys = new HashSet<>();
    private final Map<String, OwnerCommandIdempotency.Holder> holders = new HashMap<>();
    private final List<CommandRecord> outcomes = new ArrayList<>();
    private final List<String> conflicts = new ArrayList<>();
    private final MutableClock clock = new MutableClock(NOW);
    private final List<Duration> timeouts = new ArrayList<>();
    private final List<String> unitsOfWork = new ArrayList<>();
    private boolean started = true;
    private String providerVersion = "1.2.0";
    private String accessPolicy = "declared-consumers";
    private CommandDelivery delivery;

    @BeforeEach
    void setUp() {
        UnitOfWork unitOfWork = mock(UnitOfWork.class);
        when(unitOfWork.required(any())).thenAnswer(call -> {
            unitsOfWork.add("began");
            Object result = ((Supplier<?>) call.getArgument(0)).get();
            unitsOfWork.add("committed");
            return result;
        });
        PersistenceCapability persistence = mock(PersistenceCapability.class);
        when(persistence.unitOfWork()).thenReturn(unitOfWork);
        CommandProviders providers = new CommandProviders() {
            @Override
            public boolean ready() {
                return true;
            }

            @Override
            public Optional<PluginRuntimeManager.CommandProvider> providerOf(String commandType) {
                return "ReserveStock".equals(commandType) ? Optional.of(provider()) : Optional.empty();
            }

            @Override
            public List<PluginRuntimeManager.CommandProvider> providers() {
                return List.of(provider());
            }

            @Override
            public Optional<PluginRuntimeDescriptor.ConsumedCommand> consumed(String pluginId, String commandType) {
                return ("app-orders".equals(pluginId) || "app-billing".equals(pluginId))
                    && "ReserveStock".equals(commandType)
                    ? Optional.of(new PluginRuntimeDescriptor.ConsumedCommand(commandType, ">=1.0.0 <2.0.0", true))
                    : Optional.empty();
            }
        };
        OwnerPersistenceProvider owners = new OwnerPersistenceProvider() {
            @Override
            public Optional<PersistenceCapability> forOwner(String ownerModuleId) {
                return "app-stock".equals(ownerModuleId) ? Optional.of(persistence) : Optional.empty();
            }

            @Override
            public <T> T readWrite(String ownerModuleId, Duration timeout, Supplier<T> work) {
                timeouts.add(timeout);
                return forOwner(ownerModuleId).map(owner -> owner.unitOfWork().required(work)).orElseGet(work);
            }
        };
        delivery = new CommandDelivery(providers, owners,
            (owner, handlerId, message) -> inbox.add(owner + "/" + handlerId + "/" + message.messageId()),
            new OwnerCommandIdempotency() {
                @Override
                public boolean reserve(String owner, String handlerId, CanonicalOutboxMessage message, String scope,
                        String key, String fingerprint) {
                    holders.putIfAbsent(owner + "/" + handlerId + "/" + scope + "/" + key,
                        new OwnerCommandIdempotency.Holder(message.messageId(), fingerprint));
                    return keys.add(owner + "/" + handlerId + "/" + scope + "/" + key);
                }

                @Override
                public Optional<OwnerCommandIdempotency.Holder> holder(String owner, String handlerId, String scope,
                        String key) {
                    return Optional.ofNullable(holders.get(owner + "/" + handlerId + "/" + scope + "/" + key));
                }

                @Override
                public void recordOutcome(String owner, String handlerId, CommandRecord outcome) {
                    assertEquals("began", unitsOfWork.get(unitsOfWork.size() - 1),
                        "an outcome is written in the provider's unit of work");
                    outcomes.add(outcome);
                }
            },
            JSON, clock, Duration.ofSeconds(15), MessageTracing.NOOP,
            (owner, handlerId) -> conflicts.add(owner + "/" + handlerId));
    }

    private PluginRuntimeManager.CommandProvider provider() {
        return new PluginRuntimeManager.CommandProvider("app-stock",
            new PluginRuntimeDescriptor.ProvidedCommand("ReserveStock", "reserve-stock.v1", providerVersion,
                "event-standard", "event-standard", "partition-serial", "command-envelope", accessPolicy, "persistent-inbox"),
            started ? handler : null, Reserve.class);
    }

    @Test
    void aCommandRunsItsProvidersHandlerOnce() {
        delivery.deliver(command("c-1", "order-7", "app-orders", "1.0.0"));
        delivery.deliver(command("c-1", "order-7", "app-orders", "1.0.0"));

        assertEquals(1, handler.handled.size(), "the redelivered command id is recorded in the inbox");
        CommandInvocation<Reserve> invocation = handler.handled.get(0);
        assertEquals(new Reserve("sku-1", 2), invocation.command().payload());
        assertEquals("order-7", invocation.command().idempotencyKey());
        assertEquals("app-orders", invocation.command().producerPluginId());
        assertEquals(NOW.plusSeconds(15), invocation.deadline());
    }

    @Test
    void aSecondCommandWithAKeyAlreadyTakenDoesNotRunAgain() {
        // Root BRX-RS-MESSAGE-014: a repeated submission produces no second side effect.
        delivery.deliver(command("c-1", "order-7", "app-orders", "1.0.0"));
        delivery.deliver(command("c-2", "order-7", "app-orders", "1.0.0"));

        assertEquals(1, handler.handled.size());
        assertTrue(inbox.contains("app-stock/reserve-stock.v1/c-2"), "the second command is still acknowledged");
        // Standard-link plan point 7: its sender reads it as a duplicate of the command holding the key.
        assertEquals(List.of(new CommandRecord("c-2", CommandStatus.State.DUPLICATE, "app-orders|platform|order",
            "order-7", null, null, "c-1", OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC))), outcomes);
    }

    @Test
    void aSecondCommandWithTheKeyAndOtherContentIsAConflictItsHandlerNeverSees() {
        // Standard-link plan point 8: the same key with another payload is not a duplicate. It is recorded for its
        // sender, counted, and acknowledged, so it is neither retried nor parked.
        delivery.deliver(command("c-1", "order-7", "app-orders", "1.0.0"));
        delivery.deliver(command("c-2", "order-7", "app-orders", "1.0.0", null, null, new Reserve("sku-1", 3)));

        assertEquals(1, handler.handled.size());
        assertEquals(List.of(new CommandRecord("c-2", CommandStatus.State.CONFLICT, "app-orders|platform|order",
            "order-7", null, CommandStatus.IDEMPOTENCY_CONFLICT, "c-1", OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC))),
            outcomes);
        assertEquals(List.of("app-stock/reserve-stock.v1"), conflicts);
        assertTrue(inbox.contains("app-stock/reserve-stock.v1/c-2"), "acknowledged: never delivered again");
    }

    @Test
    void theSameContentWrittenAnotherWayIsADuplicate() {
        delivery.deliver(command("c-1", "order-7", "app-orders", "1.0.0"));
        String reordered = "{\"idempotencyKey\": \"order-7\", \"payload\": {\"quantity\": 2, \"sku\": \"sku-1\"},"
            + " \"idempotencyScope\": \"order\"}";
        CanonicalOutboxMessage first = command("c-2", "order-7", "app-orders", "1.0.0");
        delivery.deliver(new CanonicalOutboxMessage(first.messageId(), first.eventId(), first.messageKind(),
            first.messageType(), first.schemaVersion(), first.reliability(), first.producerPluginId(), first.scope(),
            first.tenantId(), first.partitionKey(), first.occurredAt(), first.correlationId(), first.causationId(),
            first.traceparent(), first.tracestate(), reordered, first.attemptCount()));

        assertEquals(CommandStatus.State.DUPLICATE, outcomes.get(0).state());
        assertTrue(conflicts.isEmpty());
    }

    @Test
    void aKeyTakenBeforeFingerprintsWereKeptMakesEveryLaterCommandADuplicate() {
        String taken = "app-stock/reserve-stock.v1/app-orders|platform|order/order-7";
        keys.add(taken);
        holders.put(taken, new OwnerCommandIdempotency.Holder("c-0", null));

        delivery.deliver(command("c-2", "order-7", "app-orders", "1.0.0", null, null, new Reserve("sku-9", 9)));

        assertEquals(CommandStatus.State.DUPLICATE, outcomes.get(0).state());
        assertEquals("c-0", outcomes.get(0).holderCommandId());
        assertTrue(conflicts.isEmpty());
        assertTrue(handler.handled.isEmpty());
    }

    @Test
    void aCommandAnOperatorDiscardedIsRecordedForItsSenderInTheProvidersUnitOfWork() {
        // Standard-link plan point 7: a command the provider parked on a broker, then an operator discarded.
        delivery.discarded("app-stock", "reserve-stock.v1", command("c-3", "order-9", "app-orders", "1.0.0", 42L,
            null), "COMMAND_HANDLER_FAILED");

        assertEquals(List.of(new CommandRecord("c-3", CommandStatus.State.DISCARDED, "app-orders|42|order", "order-9",
            42L, "COMMAND_HANDLER_FAILED", null, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC))), outcomes);
        assertEquals(List.of("began", "committed"), unitsOfWork);
        assertEquals(List.of(Duration.ofSeconds(15)), timeouts);
        assertTrue(handler.handled.isEmpty());
    }

    @Test
    void aDiscardedCommandWithoutAKeyIsNotRecorded() {
        CanonicalOutboxMessage keyless = new CanonicalOutboxMessage("c-4", "c-4", "COMMAND", "ReserveStock", "1.0.0",
            "STANDARD", "app-orders", "PLATFORM", null, "c-4", OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC), "c-4",
            null, null, null, "not json", 1);

        delivery.discarded("app-stock", "reserve-stock.v1", keyless, CommandDelivery.PAYLOAD_INVALID);

        assertTrue(outcomes.isEmpty());
        assertTrue(unitsOfWork.isEmpty());
    }

    @Test
    void aCommandFromAPluginThatDoesNotDeclareItIsParked() {
        EventDeliveryException refused = assertThrows(EventDeliveryException.class,
            () -> delivery.deliver(command("c-1", "order-7", "app-intruder", "1.0.0")));

        assertEquals(CommandDelivery.CONSUMER_UNDECLARED, refused.errorCode());
        assertTrue(refused.permanent());
        assertEquals(List.of(), handler.handled);
    }

    @Test
    void aCommandOfAnotherMajorIsParked() {
        EventDeliveryException refused = assertThrows(EventDeliveryException.class,
            () -> delivery.deliver(command("c-1", "order-7", "app-orders", "2.0.0")));

        assertEquals(CommandDelivery.SCHEMA_UNSUPPORTED, refused.errorCode());
        assertTrue(refused.permanent());
    }

    @Test
    void aCommandWaitsForItsProviderToStart() {
        started = false;

        EventDeliveryException waiting = assertThrows(EventDeliveryException.class,
            () -> delivery.deliver(command("c-1", "order-7", "app-orders", "1.0.0")));

        assertEquals(CommandDelivery.PROVIDER_NOT_READY, waiting.errorCode());
        assertFalse(waiting.permanent());
    }

    @Test
    void aFailedHandlerIsRetried() {
        handler.failure = new IllegalStateException("stock service down");

        EventDeliveryException failed = assertThrows(EventDeliveryException.class,
            () -> delivery.deliver(command("c-1", "order-7", "app-orders", "1.0.0")));

        assertEquals(CommandDelivery.HANDLER_FAILED, failed.errorCode());
        assertFalse(failed.permanent());
    }

    @Test
    void theProvidersUnitOfWorkLastsNoLongerThanTheTimeLeftBeforeItsDeadline() {
        // Standard-link plan point 3 (root BRX-RS-DATA-011): its statements stop, and its waits for a connection end,
        // at the delivery's deadline.
        delivery.deliver(command("c-1", "order-7", "app-orders", "1.0.0"));

        assertEquals(List.of(Duration.ofSeconds(15)), timeouts);
        assertEquals(List.of("began", "committed"), unitsOfWork);
    }

    @Test
    void aHandlerThatRunsPastItsDeadlineFailsBeforeItsUnitOfWorkCommits() {
        // Standard-link plan point 3: a synchronous handler did its work before it returned, past its deadline. The
        // delivery fails inside the unit of work, so the inbox and idempotency records and its writes roll back.
        handler.whileHandling = () -> clock.advance(Duration.ofSeconds(16));

        EventDeliveryException failed = assertThrows(EventDeliveryException.class,
            () -> delivery.deliver(command("c-1", "order-7", "app-orders", "1.0.0")));

        assertEquals(CommandDelivery.HANDLER_FAILED, failed.errorCode());
        assertFalse(failed.permanent());
        assertTrue(failed.getCause().getMessage().contains("finished after its deadline"),
            failed.getCause().getMessage());
        assertTrue(handler.handled.get(0).cancellation().isCancelled(),
            "the handler is told the runtime stopped waiting");
        assertEquals(List.of("began"), unitsOfWork, "its unit of work did not commit");
    }

    @Test
    void aBrokerConsumesEachProvidedCommandAsASubscriptionOfItsOwn() {
        assertEquals(List.of(new ConsumedSubscription("app-stock", "reserve-stock.v1", "ReserveStock",
            ConsumedSubscription.COMMAND)), delivery.provisions());
        assertEquals("app-stock.command.reserve-stock.v1", delivery.provisions().get(0).name());
    }

    @Test
    void aCommandForAnotherProviderHandlerIsRefused() {
        EventDeliveryException refused = assertThrows(EventDeliveryException.class,
            () -> delivery.deliverTo("app-stock", "other.v1", command("c-1", "order-7", "app-orders", "1.0.0")));

        assertEquals(CommandDelivery.SUBSCRIPTION_UNKNOWN, refused.errorCode());
    }

    @Test
    void aKeyIsUniqueForItsSenderAndTenantOnly() {
        // Standard-link plan D54: another sender or tenant using the same business key is another command.
        delivery.deliver(command("c-1", "order-7", "app-orders", "1.0.0"));
        delivery.deliver(command("c-2", "order-7", "app-billing", "1.0.0"));
        delivery.deliver(command("c-3", "order-7", "app-orders", "1.0.0", 42L, null));
        delivery.deliver(command("c-4", "order-7", "app-orders", "1.0.0", 42L, null));

        assertEquals(List.of("c-1", "c-2", "c-3"), handler.handled.stream()
            .map(invocation -> invocation.command().commandId()).toList());
        assertTrue(keys.contains("app-stock/reserve-stock.v1/app-orders|42|order/order-7"), keys.toString());
    }

    @Test
    void anOwnerThatTakesACommandOnlyForAUserParksOneWithoutAUser() {
        accessPolicy = "on-behalf-of:stock:reserve";

        EventDeliveryException refused = assertThrows(EventDeliveryException.class,
            () -> delivery.deliver(command("c-1", "order-7", "app-orders", "1.0.0")));

        assertEquals(CommandDelivery.ACCESS_DENIED, refused.errorCode());
        assertTrue(refused.permanent());
        assertEquals(List.of(), handler.handled);
    }

    @Test
    void theHandlerSeesTheUserAndThePermissionTheCommandWasAdmittedFor() {
        accessPolicy = "on-behalf-of:stock:reserve";

        delivery.deliver(command("c-1", "order-7", "app-orders", "1.0.0", null, "u-1"));

        CommandInvocation<Reserve> invocation = handler.handled.get(0);
        assertEquals("u-1", invocation.command().actorId());
        assertEquals(Set.of("stock:reserve"), invocation.access().permissions());
        assertEquals(new IdempotencyContext("order", "order-7"), invocation.idempotency());
    }

    @Test
    void withoutAnOnBehalfPolicyTheHandlerSeesNoPermission() {
        delivery.deliver(command("c-1", "order-7", "app-orders", "1.0.0"));

        CommandInvocation<Reserve> invocation = handler.handled.get(0);
        assertEquals(Set.of(), invocation.access().permissions());
        assertEquals(null, invocation.command().actorId());
    }

    private static CanonicalOutboxMessage command(String commandId, String key, String producer, String version) {
        return command(commandId, key, producer, version, null, null);
    }

    private static CanonicalOutboxMessage command(String commandId, String key, String producer, String version,
            Long tenantId, String actorId) {
        return command(commandId, key, producer, version, tenantId, actorId, new Reserve("sku-1", 2));
    }

    private static CanonicalOutboxMessage command(String commandId, String key, String producer, String version,
            Long tenantId, String actorId, Reserve payload) {
        return new CanonicalOutboxMessage(commandId, commandId, "COMMAND", "ReserveStock", version, "STANDARD", producer,
            tenantId == null ? "PLATFORM" : "TENANT", tenantId, key, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC),
            commandId, null, null, null, CommandMessages.encode(JSON, payload, "order", key, actorId), 1);
    }

    /** A clock the test moves forward, as time passes while a synchronous handler runs. */
    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
