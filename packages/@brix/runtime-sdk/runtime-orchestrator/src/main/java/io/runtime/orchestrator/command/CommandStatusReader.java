/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.orchestrator.event.ConsumedSubscription;
import io.runtime.orchestrator.event.EventSubscriptionTransport;
import io.runtime.orchestrator.event.ParkedEvent;
import io.runtime.orchestrator.plugin.PluginRuntimeManager;
import io.runtime.sdk.command.CommandStatus;

/**
 * The runtime's command statuses (root BRX-RS-MESSAGE-013, MESSAGE-014; standard-link plan point 7, idempotency
 * design §4.3, §4.8). The first source that knows the command answers, in this order:
 * <ol>
 *   <li>the provider's idempotency key record: {@code SUCCEEDED};</li>
 *   <li>the provider's outcome record: {@code DUPLICATE}, {@code CONFLICT}, {@code REJECTED} or {@code DISCARDED};</li>
 *   <li>the commands the provider parked, on a broker transport: {@code PARKED}. A host still reading what is parked
 *   does not know them yet, and the next source answers;</li>
 *   <li>the sender's outbox: {@code QUEUED} while the command waits or travels, {@code PARKED} or
 *   {@code DISCARDED} when the relay parked it;</li>
 *   <li>otherwise {@code UNKNOWN}.</li>
 * </ol>
 *
 * <p>A record counts only when it belongs to the asking sender in the asking tenant. The provider's records carry
 * the scope that the sender, the tenant and the command type make; a parked command carries its producer and
 * tenant; the sender's outbox holds only the sender's commands, each with its tenant. A record of anyone else reads
 * as unknown, so a plugin learns nothing about commands that are not its own.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class CommandStatusReader implements CommandStatuses {

    private static final Logger log = LoggerFactory.getLogger(CommandStatusReader.class);

    private final CommandProviders providers;
    private final Supplier<OwnerCommandIdempotency> records;
    private final Supplier<EventSubscriptionTransport> brokers;

    /**
     * Creates the reader.
     *
     * @param providers who provides each command type
     * @param records supplies the providers' records; may supply {@code null}
     * @param brokers supplies the broker transport whose parked commands count; supplies {@code null} on the
     *     in-process transport, which parks in the sender's outbox
     */
    public CommandStatusReader(CommandProviders providers, Supplier<OwnerCommandIdempotency> records,
            Supplier<EventSubscriptionTransport> brokers) {
        this.providers = Objects.requireNonNull(providers, "providers must not be null");
        this.records = Objects.requireNonNull(records, "records must not be null");
        this.brokers = Objects.requireNonNull(brokers, "brokers must not be null");
    }

    @Override
    public CommandStatus status(String senderId, String commandType, Long tenantId, String commandId,
            SentCommands sent) {
        Objects.requireNonNull(senderId, "senderId must not be null");
        Objects.requireNonNull(commandType, "commandType must not be null");
        Objects.requireNonNull(sent, "sent must not be null");
        if (commandId == null || commandId.isBlank()) {
            throw new IllegalArgumentException("commandId must not be blank");
        }
        Optional<PluginRuntimeManager.CommandProvider> provider = providers.providerOf(commandType);
        if (provider.isPresent()) {
            String ownerId = provider.get().pluginId();
            String handlerId = provider.get().command().handlerId();
            OwnerCommandIdempotency store = records.get();
            Optional<CommandRecord> record = store == null ? Optional.empty()
                : store.find(ownerId, handlerId, commandId);
            if (record.isPresent()) {
                // A typed client's scope is its command type (PluginCommandCapability), so the sender and tenant
                // that ask make the scope the record must carry.
                String scope = CommandPolicies.ownerScope(senderId, tenantId, commandType);
                return scope.equals(record.get().idempotencyScope()) ? statusOf(record.get())
                    : CommandStatus.unknown(commandId);
            }
            Optional<CommandStatus> parked = parkedByProvider(ownerId, handlerId, commandType, senderId, tenantId,
                commandId);
            if (parked.isPresent()) {
                return parked.get();
            }
        }
        return sent.sent(commandId)
            .map(row -> commandType.equals(row.commandType()) && Objects.equals(tenantId, row.tenantId())
                ? statusOf(row) : CommandStatus.unknown(commandId))
            .orElseGet(() -> CommandStatus.unknown(commandId));
    }

    /** A command the provider's consumer parked on a broker transport, if the sender in its tenant sent it. */
    private Optional<CommandStatus> parkedByProvider(String ownerId, String handlerId, String commandType,
            String senderId, Long tenantId, String commandId) {
        EventSubscriptionTransport broker = brokers.get();
        if (broker == null) {
            return Optional.empty();
        }
        ConsumedSubscription subscription = new ConsumedSubscription(ownerId, handlerId, commandType,
            ConsumedSubscription.COMMAND);
        Optional<ParkedEvent> parked;
        try {
            parked = broker.parkedEvent(subscription, commandId);
        } catch (RuntimeException notKnownYet) {
            // A host that has not read what is parked yet: the sender's own record answers, as for a command not parked.
            log.debug("event=command.parked_not_known command_id={}", commandId, notKnownYet);
            return Optional.empty();
        }
        if (parked.isEmpty()) {
            return Optional.empty();
        }
        boolean own = senderId.equals(parked.get().producerPluginId()) && Objects.equals(tenantId, parked.get().tenantId());
        return Optional.of(own
            ? new CommandStatus(commandId, CommandStatus.State.PARKED, parked.get().errorCode(), null,
                instant(parked.get().parkedAt()))
            : CommandStatus.unknown(commandId));
    }

    private static CommandStatus statusOf(CommandRecord record) {
        return new CommandStatus(record.commandId(), record.state(), record.errorCode(), record.holderCommandId(),
            instant(record.at()));
    }

    private static CommandStatus statusOf(SentCommand row) {
        CommandStatus.State state = switch (row.status()) {
            case "PARKED" -> CommandStatus.State.PARKED;
            case "DISCARDED" -> CommandStatus.State.DISCARDED;
            default -> CommandStatus.State.QUEUED;
        };
        return new CommandStatus(row.commandId(), state, state == CommandStatus.State.QUEUED ? null : row.errorCode(),
            null, instant(row.at()));
    }

    private static Instant instant(OffsetDateTime at) {
        return at == null ? null : at.toInstant();
    }
}
