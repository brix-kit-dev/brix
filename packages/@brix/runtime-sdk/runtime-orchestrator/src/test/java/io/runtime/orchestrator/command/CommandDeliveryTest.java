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
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.orchestrator.plugin.PluginRuntimeManager;
import io.runtime.orchestrator.tracing.MessageTracing;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.capability.UnitOfWork;
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

        @Override
        public CompletionStage<Void> handle(CommandInvocation<Reserve> invocation) {
            if (failure != null) {
                throw failure;
            }
            handled.add(invocation);
            return CompletableFuture.completedFuture(null);
        }
    }

    private final ReserveHandler handler = new ReserveHandler();
    private final Set<String> inbox = new HashSet<>();
    private final Set<String> keys = new HashSet<>();
    private boolean started = true;
    private String providerVersion = "1.2.0";
    private String accessPolicy = "declared-consumers";
    private CommandDelivery delivery;

    @BeforeEach
    void setUp() {
        UnitOfWork unitOfWork = mock(UnitOfWork.class);
        when(unitOfWork.required(any())).thenAnswer(call -> ((Supplier<?>) call.getArgument(0)).get());
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
        delivery = new CommandDelivery(providers, owner -> "app-stock".equals(owner) ? Optional.of(persistence)
            : Optional.empty(), (owner, handlerId, message) -> inbox.add(owner + "/" + handlerId + "/" + message.messageId()),
            (owner, handlerId, message, scope, key) -> keys.add(owner + "/" + handlerId + "/" + scope + "/" + key),
            JSON, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofSeconds(15), MessageTracing.NOOP);
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
        return new CanonicalOutboxMessage(commandId, commandId, "COMMAND", "ReserveStock", version, "STANDARD", producer,
            tenantId == null ? "PLATFORM" : "TENANT", tenantId, key, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC),
            commandId, null, null, null, CommandMessages.encode(JSON, new Reserve("sku-1", 2), "order", key, actorId), 1);
    }
}
