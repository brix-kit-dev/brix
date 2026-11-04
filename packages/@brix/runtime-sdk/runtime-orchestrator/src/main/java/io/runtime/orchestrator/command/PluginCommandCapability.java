/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.runtime.orchestrator.internalcontract.BrixRange;
import io.runtime.orchestrator.invocation.RuntimeInvocation;
import io.runtime.orchestrator.invocation.RuntimeInvocations;
import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.sdk.capability.CommandCapability;
import io.runtime.sdk.capability.CommandSubmission;
import io.runtime.sdk.capability.CommandSubmitException;
import io.runtime.sdk.command.CommandClient;
import io.runtime.sdk.command.CommandRequest;
import io.runtime.sdk.command.CommandStatus;
import io.runtime.sdk.command.CommandType;
import io.runtime.sdk.query.BrixQueryException;

/**
 * The {@link CommandCapability} a plugin gets from its {@code PluginContext} (root §6.3, A-22, ERROR-006;
 * standard-link plan L4.2): typed clients over the plugin's own outbox bus.
 *
 * <p>A client checks, when it is created, that the plugin declares the command under {@code commands.consumes} and
 * that the generated type's version is in the declared range. A submission takes the tenant and the user from the
 * current call; the plugin states neither. When the owner runs on this host, the submission is refused at once if
 * the owner accepts the command only on behalf of a user holding a permission the caller lacks
 * ({@code command.access_denied}), and the user is recorded with the command. When no plugin on this host provides
 * the command, the submission is refused as {@code command.provider_missing} instead of waiting in the outbox for an
 * owner that is not there.</p>
 *
 * <p>A client reads the status of the commands this plugin submitted, in the tenant of the current call (standard-link
 * plan point 7): from the owner's records, the commands the owner parked, and this plugin's own outbox.</p>
 */
public final class PluginCommandCapability implements CommandCapability {

    private final String pluginId;
    private final CommandCapability bus;
    private final Map<String, PluginRuntimeDescriptor.ConsumedCommand> consumed;
    private final CommandProviders providers;

    /**
     * Creates the capability.
     *
     * @param pluginId the sending plugin
     * @param bus the plugin's outbox bus
     * @param consumed the commands the plugin declares under {@code commands.consumes}
     * @param providers the providers on this host, or {@code null} when they are not known, as in a unit test of a
     *     plugin context: then neither the owner's access policy nor its presence is checked at submission
     */
    public PluginCommandCapability(String pluginId, CommandCapability bus,
            Map<String, PluginRuntimeDescriptor.ConsumedCommand> consumed, CommandProviders providers) {
        this.pluginId = Objects.requireNonNull(pluginId, "pluginId must not be null");
        this.bus = Objects.requireNonNull(bus, "bus must not be null");
        this.consumed = Map.copyOf(Objects.requireNonNull(consumed, "consumed must not be null"));
        this.providers = providers;
    }

    @Override
    @Deprecated(since = "3.3.0", forRemoval = true)
    public <C> CommandSubmission submit(CommandRequest<C> request) {
        return bus.submit(request);
    }

    @Override
    public <C> CommandClient<C> client(CommandType<C> type) {
        Objects.requireNonNull(type, "type must not be null");
        PluginRuntimeDescriptor.ConsumedCommand declared = consumed.get(type.commandType());
        if (declared == null) {
            throw refused(CommandSubmitException.Code.UNDECLARED_COMMAND, type, "undeclared");
        }
        if (!declared.versionRange().isEmpty() && !BrixRange.contains(type.version(), declared.versionRange())) {
            throw refused(CommandSubmitException.Code.INVALID_COMMAND, type, "version-outside-declared-range");
        }
        return new Client<>(type);
    }

    /** A typed client of one command type: it submits through the plugin's outbox bus and reads its statuses. */
    private final class Client<C> implements CommandClient<C> {

        private final CommandType<C> type;

        private Client(CommandType<C> type) {
            this.type = type;
        }

        @Override
        public CommandSubmission submit(C command, String idempotencyKey, String partitionKey) {
            return PluginCommandCapability.this.submit(type, command, idempotencyKey, partitionKey);
        }

        @Override
        public CommandStatus status(String commandId) {
            if (commandId == null || commandId.isBlank()) {
                throw new IllegalArgumentException("commandId must not be blank");
            }
            String tenant = RuntimeInvocations.current().flatMap(call -> call.scope().tenantId()).orElse(null);
            Long tenantId;
            try {
                tenantId = tenant == null ? null : Long.valueOf(tenant);
            } catch (NumberFormatException notATenantOfCommands) {
                // A command's tenant is a number, or it could not have been submitted.
                return CommandStatus.unknown(commandId);
            }
            CommandStatuses statuses = providers == null ? CommandStatuses.NONE : providers.statuses();
            SentCommands sent = bus instanceof SentCommands own ? own : SentCommands.NONE;
            return statuses.status(pluginId, type.commandType(), tenantId, commandId, sent);
        }
    }

    private <C> CommandSubmission submit(CommandType<C> type, C command, String idempotencyKey, String partitionKey) {
        if (RuntimeInvocations.inQuery()) {
            // A query has no side effects (root BRX-RS-MESSAGE-002), whichever way the client was obtained.
            throw new BrixQueryException(BrixQueryException.Code.PROVIDER_FAILED,
                Map.of("reason", "side-effect-in-query", "capability", CommandCapability.class.getSimpleName()), null);
        }
        if (!type.requestType().isInstance(command)) {
            throw refused(CommandSubmitException.Code.INVALID_COMMAND, type, "request-type");
        }
        Optional<RuntimeInvocation> current = RuntimeInvocations.current();
        String actorId = null;
        if (providers != null) {
            OwnerAccess owner = ownerOf(type);
            if (owner.access().kind() == CommandPolicies.Access.Kind.ON_BEHALF_OF) {
                RuntimeInvocation caller = current.filter(call -> call.caller().hasPrincipal())
                    .orElseThrow(() -> refused(CommandSubmitException.Code.ACCESS_DENIED, type, "no-principal"));
                if (!caller.holds(owner.access().permission())) {
                    throw refused(CommandSubmitException.Code.ACCESS_DENIED, type, "permission");
                }
                actorId = caller.caller().principalId().orElseThrow();
            }
        }
        String tenantId = current.flatMap(call -> call.scope().tenantId()).orElse(null);
        CommandRequest<C> request = CommandRequest.of(type.commandType(), command, type.commandType(), idempotencyKey)
            .forTenant(tenantId)
            .withPartitionKey(partitionKey);
        if (bus instanceof CommandSender sender) {
            return sender.send(request, type.version(), actorId);
        }
        return bus.submit(request);
    }

    private OwnerAccess ownerOf(CommandType<?> type) {
        return providers.providerOf(type.commandType())
            .map(provider -> new OwnerAccess(
                CommandPolicies.access(provider.command().accessPolicyRef())))
            .orElseThrow(() -> refused(CommandSubmitException.Code.PROVIDER_MISSING, type, "no-provider"));
    }

    private CommandSubmitException refused(CommandSubmitException.Code code, CommandType<?> type, String reason) {
        return new CommandSubmitException(code,
            Map.of("commandType", type.commandType(), "sender", pluginId, "reason", reason), null);
    }

    /** The owner's access policy, as the provider on this host declares it. */
    private record OwnerAccess(CommandPolicies.Access access) {
    }
}
