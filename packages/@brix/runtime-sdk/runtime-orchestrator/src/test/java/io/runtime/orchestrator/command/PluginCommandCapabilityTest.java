/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.invocation.RuntimeInvocation;
import io.runtime.orchestrator.invocation.RuntimeInvocations;
import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.orchestrator.plugin.PluginRuntimeManager;
import io.runtime.sdk.capability.CommandCapability;
import io.runtime.sdk.capability.CommandReceipt;
import io.runtime.sdk.capability.CommandSubmission;
import io.runtime.sdk.capability.CommandSubmitException;
import io.runtime.sdk.command.CommandRequest;
import io.runtime.sdk.command.CommandStatus;
import io.runtime.sdk.command.CommandType;
import io.runtime.sdk.plugin.AccessDecision;
import io.runtime.sdk.plugin.CallerIdentity;
import io.runtime.sdk.plugin.CancellationSignal;
import io.runtime.sdk.plugin.InvocationScope;
import io.runtime.sdk.query.BrixQueryException;

/**
 * Typed command clients (root A-22, ERROR-006; standard-link plan L4.2).
 */
class PluginCommandCapabilityTest {

    record Cancel(String orderId) {
    }

    private static final CommandType<Cancel> CANCEL = CommandType.of("sample-orders.cancel-order", "1.0.0", Cancel.class);

    private final RecordingBus bus = new RecordingBus();
    private String accessPolicy = "declared-consumers";
    private boolean providerPresent = true;
    private final List<List<Object>> statusReads = new ArrayList<>();
    private final CommandStatuses statuses = (senderId, commandType, tenantId, commandId, sent) -> {
        statusReads.add(java.util.Arrays.asList(senderId, commandType, tenantId, commandId, sent));
        return new CommandStatus(commandId, CommandStatus.State.SUCCEEDED, null, null, Instant.EPOCH);
    };

    private final CommandProviders providers = new CommandProviders() {
        @Override
        public boolean ready() {
            return true;
        }

        @Override
        public Optional<PluginRuntimeManager.CommandProvider> providerOf(String commandType) {
            if (!providerPresent || !CANCEL.commandType().equals(commandType)) {
                return Optional.empty();
            }
            return Optional.of(new PluginRuntimeManager.CommandProvider("sample-orders",
                new PluginRuntimeDescriptor.ProvidedCommand(commandType, "cancel-order.v1", "1.0.0",
                    "command-transient-retry", "command-standard", "partition-serial", "command-envelope", accessPolicy,
                    "command-key"),
                null, Cancel.class));
        }

        @Override
        public List<PluginRuntimeManager.CommandProvider> providers() {
            return List.of();
        }

        @Override
        public Optional<PluginRuntimeDescriptor.ConsumedCommand> consumed(String pluginId, String commandType) {
            return Optional.empty();
        }

        @Override
        public CommandStatuses statuses() {
            return statuses;
        }
    };

    @Test
    void aSubmissionCarriesTheGeneratedTypesVersionAndTheCallersTenant() {
        CommandSubmission submission = RuntimeInvocations.within(call(InvocationScope.tenant("42"), null, Set.of()),
            () -> capability(providers).client(CANCEL).submit(new Cancel("o-7"), "o-7"));

        Sent sent = bus.sent.get(0);
        assertEquals("c-1", submission.commandId());
        assertEquals("sample-orders.cancel-order", sent.request().commandType());
        assertEquals("42", sent.request().tenantId());
        assertEquals("sample-orders.cancel-order", sent.request().idempotencyScope());
        assertEquals("o-7", sent.request().idempotencyKey());
        assertEquals("1.0.0", sent.version());
        assertNull(sent.actorId());
    }

    @Test
    void outsideAnyCallTheCommandIsAPlatformCommand() {
        capability(providers).client(CANCEL).submit(new Cancel("o-7"), "o-7");

        assertNull(bus.sent.get(0).request().tenantId());
    }

    @Test
    void aClientReadsTheStatusAsThisPluginInTheCallersTenantWithItsOwnOutbox() {
        // Standard-link plan point 7: the sender, its tenant and its outbox are the runtime's, never the plugin's.
        CommandStatus status = RuntimeInvocations.within(call(InvocationScope.tenant("42"), null, Set.of()),
            () -> capability(providers).client(CANCEL).status("c-9"));

        assertEquals(CommandStatus.State.SUCCEEDED, status.state());
        assertEquals(List.of(java.util.Arrays.asList("sample-shipping", "sample-orders.cancel-order", 42L, "c-9", bus)),
            statusReads);
    }

    @Test
    void outsideAnyCallTheStatusIsReadOutsideATenant() {
        capability(providers).client(CANCEL).status("c-9");

        assertNull(statusReads.get(0).get(2));
    }

    @Test
    void aCallerInATenantThatIsNoNumberReadsEveryCommandAsUnknown() {
        CommandStatus status = RuntimeInvocations.within(call(InvocationScope.tenant("acme"), null, Set.of()),
            () -> capability(providers).client(CANCEL).status("c-9"));

        assertEquals(CommandStatus.unknown("c-9"), status);
        assertTrue(statusReads.isEmpty());
    }

    @Test
    void withoutTheHostsProvidersEveryCommandIsUnknown() {
        assertEquals(CommandStatus.unknown("c-9"), capability(null).client(CANCEL).status("c-9"));
        assertThrows(IllegalArgumentException.class, () -> capability(providers).client(CANCEL).status(" "));
    }

    @Test
    void anUndeclaredCommandHasNoClient() {
        CommandSubmitException refused = assertThrows(CommandSubmitException.class,
            () -> capability(providers).client(CommandType.of("other.command", "1.0.0", Cancel.class)));

        assertEquals(CommandSubmitException.Code.UNDECLARED_COMMAND, refused.errorCode());
    }

    @Test
    void aTypeOutsideTheDeclaredRangeHasNoClient() {
        CommandSubmitException refused = assertThrows(CommandSubmitException.class,
            () -> capability(providers).client(CommandType.of(CANCEL.commandType(), "2.0.0", Cancel.class)));

        assertEquals(CommandSubmitException.Code.INVALID_COMMAND, refused.errorCode());
    }

    @Test
    void withoutAnOwnerOnThisHostTheSubmissionIsRefusedAtOnce() {
        providerPresent = false;

        CommandSubmitException refused = assertThrows(CommandSubmitException.class,
            () -> capability(providers).client(CANCEL).submit(new Cancel("o-7"), "o-7"));

        assertEquals(CommandSubmitException.Code.PROVIDER_MISSING, refused.errorCode());
        assertEquals(List.of(), bus.sent);
    }

    @Test
    void onBehalfOfAUserNeedsAUser() {
        accessPolicy = "on-behalf-of:orders:cancel";

        CommandSubmitException refused = assertThrows(CommandSubmitException.class,
            () -> capability(providers).client(CANCEL).submit(new Cancel("o-7"), "o-7"));

        assertEquals(CommandSubmitException.Code.ACCESS_DENIED, refused.errorCode());
        assertEquals(List.of(), bus.sent);
    }

    @Test
    void onBehalfOfAUserNeedsThePermission() {
        accessPolicy = "on-behalf-of:orders:cancel";

        CommandSubmitException refused = assertThrows(CommandSubmitException.class,
            () -> RuntimeInvocations.within(call(InvocationScope.platform(), "u-1", Set.of("orders:read")),
                () -> capability(providers).client(CANCEL).submit(new Cancel("o-7"), "o-7")));

        assertEquals(CommandSubmitException.Code.ACCESS_DENIED, refused.errorCode());
    }

    @Test
    void aUserWithThePermissionIsRecordedWithTheCommand() {
        accessPolicy = "on-behalf-of:orders:cancel";

        RuntimeInvocations.within(call(InvocationScope.platform(), "u-1", Set.of("orders:cancel")),
            () -> capability(providers).client(CANCEL).submit(new Cancel("o-7"), "o-7"));

        assertEquals("u-1", bus.sent.get(0).actorId());
    }

    @Test
    void aQueryCannotSubmitACommandEvenWithAClientItHolds() {
        var client = capability(providers).client(CANCEL);
        RuntimeInvocation query = new RuntimeInvocation(CallerIdentity.service("sample-orders"),
            RuntimeInvocation.Kind.QUERY, Set.of(), InvocationScope.platform(), AccessDecision.none(), Optional.empty(),
            Instant.now().plusSeconds(5), CancellationSignal.none(), 1);

        BrixQueryException refused = assertThrows(BrixQueryException.class,
            () -> RuntimeInvocations.within(query, () -> client.submit(new Cancel("o-7"), "o-7")));

        assertEquals(BrixQueryException.Code.PROVIDER_FAILED, refused.code());
        assertEquals(List.of(), bus.sent);
    }

    @Test
    void withoutKnownProvidersTheOwnersPolicyIsNotChecked() {
        accessPolicy = "on-behalf-of:orders:cancel";

        capability(null).client(CANCEL).submit(new Cancel("o-7"), "o-7");

        assertNull(bus.sent.get(0).actorId());
    }

    @Test
    @SuppressWarnings("removal")
    void theStringSubmissionStillReachesTheBus() {
        CommandRequest<Cancel> request = CommandRequest.of(CANCEL.commandType(), new Cancel("o-7"), "order", "o-7");

        capability(providers).submit(request);

        assertSame(request, bus.submitted.get(0));
    }

    @Test
    void anOrderingKeyIsPassedOn() {
        capability(providers).client(CANCEL).submit(new Cancel("o-7"), "o-7", "customer-3");

        assertEquals("customer-3", bus.sent.get(0).request().partitionKey());
        assertTrue(bus.submitted.isEmpty());
    }

    private PluginCommandCapability capability(CommandProviders known) {
        return new PluginCommandCapability("sample-shipping", bus,
            Map.of(CANCEL.commandType(), new PluginRuntimeDescriptor.ConsumedCommand(CANCEL.commandType(),
                ">=1.0.0 <2.0.0", false)),
            known);
    }

    private static RuntimeInvocation call(InvocationScope scope, String user, Set<String> permissions) {
        CallerIdentity caller = user == null
            ? CallerIdentity.service("sample-shipping")
            : new CallerIdentity("sample-shipping", Optional.of(user), Optional.of("platform-admin"));
        return new RuntimeInvocation(caller, RuntimeInvocation.Kind.HTTP, permissions, scope, AccessDecision.none(),
            Optional.empty(), Instant.now().plusSeconds(5), CancellationSignal.none(), 0);
    }

    private record Sent(CommandRequest<?> request, String version, String actorId) {
    }

    /** An outbox bus that records what it is given. */
    private static final class RecordingBus implements CommandCapability, CommandSender, SentCommands {

        final List<Sent> sent = new ArrayList<>();
        final List<CommandRequest<?>> submitted = new ArrayList<>();

        @Override
        @SuppressWarnings("removal")
        public <C> CommandSubmission submit(CommandRequest<C> request) {
            submitted.add(request);
            return submission();
        }

        @Override
        public CommandSubmission send(CommandRequest<?> request, String schemaVersion, String actorId) {
            sent.add(new Sent(request, schemaVersion, actorId));
            return submission();
        }

        @Override
        public Optional<SentCommand> sent(String commandId) {
            return Optional.empty();
        }

        private static CommandSubmission submission() {
            return new CommandSubmission("c-1", CompletableFuture.completedFuture(
                new CommandReceipt("c-1", "sample-orders.cancel-order", Instant.EPOCH, CommandReceipt.Status.QUEUED)));
        }
    }
}
