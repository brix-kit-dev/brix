/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;

import io.runtime.orchestrator.event.ConsumedSubscription;
import io.runtime.orchestrator.event.EventDeliveryException;
import io.runtime.orchestrator.event.EventInboxStore;
import io.runtime.orchestrator.internalcontract.BrixRange;
import io.runtime.orchestrator.invocation.RuntimeInvocation;
import io.runtime.orchestrator.invocation.RuntimeInvocations;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.persistence.TenantScope;
import io.runtime.orchestrator.persistence.TenantScopes;
import io.runtime.orchestrator.plugin.PluginRuntimeManager;
import io.runtime.orchestrator.tracing.MessageTracing;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.command.CommandEnvelope;
import io.runtime.sdk.event.EventScope;
import io.runtime.sdk.plugin.CommandHandler;
import io.runtime.sdk.plugin.CommandInvocation;
import io.runtime.sdk.plugin.TraceContext;

/**
 * Delivers a canonical command to the one plugin that provides it (root BRX-RS-MESSAGE-010 to MESSAGE-016;
 * Kafka design §14 K22), for every transport: the in-process relay hands it over directly, a broker's consumer for
 * the command, or a replay, hands it to {@link #deliverTo}.
 *
 * <p>The command is admitted only from a plugin that declares it under {@code commands.consumes}, and only in a
 * schema version of the provider's major. In the provider's unit of work, bound to the command's tenant, the
 * runtime writes the provider's inbox record {@code (handlerId, commandId)} and its business idempotency record
 * {@code (handlerId, idempotencyScope, idempotencyKey)} before the handler runs: a redelivered command, or a second
 * command with a key already taken, commits without running the handler again (MESSAGE-014). Failures carry the
 * stable codes below, permanent ones parked at once.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class CommandDelivery {

    /** The message is not a command. */
    public static final String KIND_UNSUPPORTED = "COMMAND_KIND_UNSUPPORTED";
    /** No plugin on this host provides the command type. */
    public static final String PROVIDER_UNKNOWN = "COMMAND_PROVIDER_UNKNOWN";
    /** The submitter does not declare the command type under commands.consumes. */
    public static final String CONSUMER_UNDECLARED = "COMMAND_CONSUMER_UNDECLARED";
    /** The command's schema version is not of the provider's major. */
    public static final String SCHEMA_UNSUPPORTED = "COMMAND_SCHEMA_UNSUPPORTED";
    /** The payload does not decode to the handler's type. */
    public static final String PAYLOAD_INVALID = "COMMAND_PAYLOAD_INVALID";
    /** The plugins, or the provider, have not started yet. */
    public static final String PROVIDER_NOT_READY = "COMMAND_PROVIDER_NOT_READY";
    /** The command reached a consumer of another provider, for example one an upgrade removed. */
    public static final String SUBSCRIPTION_UNKNOWN = "COMMAND_SUBSCRIPTION_UNKNOWN";
    /** The handler failed. */
    public static final String HANDLER_FAILED = "COMMAND_HANDLER_FAILED";

    private static final Logger log = LoggerFactory.getLogger(CommandDelivery.class);

    private final CommandProviders providers;
    private final OwnerPersistenceProvider owners;
    private final EventInboxStore inbox;
    private final OwnerCommandIdempotency idempotency;
    private final ObjectMapper json;
    private final Clock clock;
    private final Duration deadline;
    private final MessageTracing tracing;
    private volatile boolean stopped;

    /**
     * Creates the command delivery core.
     *
     * @param providers who provides and who submits each command type
     * @param owners each provider's owner persistence
     * @param inbox the owners' persistent inboxes
     * @param idempotency the owners' business idempotency records
     * @param json decodes payloads
     * @param clock delivery clock
     * @param deadline how long a handler may take
     * @param tracing the spans of the deliveries
     */
    public CommandDelivery(
            CommandProviders providers,
            OwnerPersistenceProvider owners,
            EventInboxStore inbox,
            OwnerCommandIdempotency idempotency,
            ObjectMapper json,
            Clock clock,
            Duration deadline,
            MessageTracing tracing) {
        this.providers = Objects.requireNonNull(providers, "providers must not be null");
        this.owners = Objects.requireNonNull(owners, "owners must not be null");
        this.inbox = Objects.requireNonNull(inbox, "inbox must not be null");
        this.idempotency = Objects.requireNonNull(idempotency, "idempotency must not be null");
        this.json = Objects.requireNonNull(json, "json must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.deadline = Objects.requireNonNull(deadline, "deadline must not be null");
        this.tracing = Objects.requireNonNull(tracing, "tracing must not be null");
    }

    /** Tells handlers still running, through their cancellation signal, that the runtime no longer waits for them. */
    public void stop() {
        stopped = true;
    }

    /**
     * Returns one consumed subscription per provided command, for a transport that consumes per provider.
     *
     * @return the command subscriptions
     */
    public List<ConsumedSubscription> provisions() {
        return providers.providers().stream()
            .map(provider -> new ConsumedSubscription(provider.pluginId(), provider.command().handlerId(),
                provider.command().commandType(), ConsumedSubscription.COMMAND))
            .toList();
    }

    /**
     * Returns the command types the plugins provide.
     *
     * @return the provided command types
     */
    public List<String> providedCommandTypes() {
        return providers.providers().stream().map(provider -> provider.command().commandType()).toList();
    }

    /**
     * Delivers a command to its provider, as the in-process transport does.
     *
     * @param message the command
     * @throws EventDeliveryException when the provider did not commit it
     */
    public void deliver(CanonicalOutboxMessage message) {
        PluginRuntimeManager.CommandProvider provider = admit(message);
        handle(message, provider);
    }

    /**
     * Delivers a command to the given provider handler, as a broker's consumer of it, or a replay, does.
     *
     * @param pluginId the providing plugin
     * @param handlerId the provider's handler id
     * @param message the command
     * @throws EventDeliveryException when the provider did not commit it
     */
    public void deliverTo(String pluginId, String handlerId, CanonicalOutboxMessage message) {
        PluginRuntimeManager.CommandProvider provider = admit(message);
        if (!provider.pluginId().equals(pluginId) || !provider.command().handlerId().equals(handlerId)) {
            throw permanent(SUBSCRIPTION_UNKNOWN, pluginId + "." + handlerId + " does not provide "
                + message.messageType(), null);
        }
        handle(message, provider);
    }

    private PluginRuntimeManager.CommandProvider admit(CanonicalOutboxMessage message) {
        if (!"COMMAND".equals(message.messageKind())) {
            throw permanent(KIND_UNSUPPORTED, "only commands are delivered here, not " + message.messageKind(), null);
        }
        if (!providers.ready()) {
            throw new EventDeliveryException(PROVIDER_NOT_READY, "the plugins have not started", null, false);
        }
        String commandType = message.messageType();
        PluginRuntimeManager.CommandProvider provider = providers.providerOf(commandType)
            .orElseThrow(() -> permanent(PROVIDER_UNKNOWN, "no plugin provides " + commandType, null));
        if (providers.consumed(message.producerPluginId(), commandType).isEmpty()) {
            throw permanent(CONSUMER_UNDECLARED, message.producerPluginId() + " does not declare " + commandType
                + " under commands.consumes", null);
        }
        String version = provider.command().version();
        if (!version.isBlank() && !BrixRange.contains(message.schemaVersion(), sameMajor(version))) {
            throw permanent(SCHEMA_UNSUPPORTED, provider.pluginId() + " takes " + commandType + " " + version
                + ", not " + message.schemaVersion(), null);
        }
        if (provider.handler() == null) {
            throw new EventDeliveryException(PROVIDER_NOT_READY, provider.pluginId() + " has not started", null, false);
        }
        return provider;
    }

    /** Handles the command in its CONSUMER span, a child of the context it carries. */
    private void handle(CanonicalOutboxMessage message, PluginRuntimeManager.CommandProvider provider) {
        String handlerId = provider.command().handlerId();
        try (MessageTracing.MessageSpan span = tracing.consumer(message, provider.pluginId() + "." + handlerId)) {
            try {
                handle(message, provider, span);
            } catch (RuntimeException failure) {
                span.failed(failure);
                throw failure;
            }
        }
    }

    private void handle(CanonicalOutboxMessage message, PluginRuntimeManager.CommandProvider provider,
            MessageTracing.MessageSpan span) {
        String ownerId = provider.pluginId();
        String handlerId = provider.command().handlerId();
        CommandMessages.Decoded decoded;
        try {
            decoded = CommandMessages.decode(json, message.payload(), provider.payloadType());
        } catch (IllegalArgumentException invalid) {
            throw permanent(PAYLOAD_INVALID, message.messageType() + " payload does not decode to "
                + provider.payloadType().getSimpleName(), invalid);
        }
        Optional<PersistenceCapability> persistence = owners.forOwner(ownerId);
        if (persistence.isEmpty()) {
            throw new EventDeliveryException(PROVIDER_NOT_READY, ownerId + " has no persistence unit for its inbox",
                null, false);
        }
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        CommandInvocation<Object> invocation = invocation(message, decoded, span, cancelled);
        @SuppressWarnings("unchecked")
        CommandHandler<Object> handler = (CommandHandler<Object>) provider.handler();
        TenantScope scope = message.tenantId() == null ? TenantScope.none() : TenantScope.tenant(message.tenantId());
        try {
            Supplier<String> work = () -> {
                if (!inbox.insertProcessed(ownerId, handlerId, message)) {
                    return "duplicate";
                }
                if (!idempotency.reserve(ownerId, handlerId, message, decoded.idempotencyScope(),
                        decoded.idempotencyKey())) {
                    return "duplicate_key";
                }
                // Typed queries the handler makes run as this provider, in the command's tenant (root
                // BRX-RS-MANIFEST-028).
                RuntimeInvocations.within(RuntimeInvocation.forCommand(ownerId, invocation), () -> {
                    await(handler.handle(invocation), invocation.deadline(), cancelled);
                    return null;
                });
                return "handled";
            };
            String outcome = TenantScopes.within(scope, () -> persistence.get().unitOfWork().required(work));
            log.info("event=command.delivered type={} command_id={} handler={} owner={} outcome={} attempt={}",
                message.messageType(), message.messageId(), handlerId, ownerId, outcome, invocation.deliveryAttempt());
        } catch (RuntimeException failure) {
            log.warn("event=command.delivery_failed type={} command_id={} handler={} owner={} attempt={}",
                message.messageType(), message.messageId(), handlerId, ownerId, invocation.deliveryAttempt(), failure);
            throw new EventDeliveryException(HANDLER_FAILED, handlerId + " failed to handle " + message.messageId(),
                failure, false);
        }
    }

    private CommandInvocation<Object> invocation(CanonicalOutboxMessage message, CommandMessages.Decoded decoded,
            MessageTracing.MessageSpan span, java.util.concurrent.atomic.AtomicBoolean cancelled) {
        String traceparent = span.traceparent() == null || span.traceparent().isBlank()
            ? newTraceparent()
            : span.traceparent();
        CommandEnvelope<Object> envelope = new CommandEnvelope<>(
            message.messageId(),
            message.messageType(),
            message.schemaVersion(),
            message.occurredAt().toInstant(),
            message.producerPluginId(),
            EventScope.valueOf(message.scope().toUpperCase(Locale.ROOT)),
            message.tenantId() == null ? null : String.valueOf(message.tenantId()),
            message.correlationId() == null || message.correlationId().isBlank() ? message.messageId()
                : message.correlationId(),
            message.causationId(),
            traceparent,
            span.tracestate(),
            message.partitionKey() == null || message.partitionKey().isBlank() ? message.messageId()
                : message.partitionKey(),
            decoded.idempotencyScope(),
            decoded.idempotencyKey(),
            decoded.payload());
        Instant now = clock.instant();
        return new CommandInvocation<>(envelope, new TraceContext(traceparent, span.tracestate()), now.plus(deadline),
            () -> stopped || cancelled.get(), Math.max(1, message.attemptCount()));
    }

    /** The versions of the provider's major: a minor or patch change keeps a command readable. */
    private static String sameMajor(String version) {
        int major;
        try {
            major = Integer.parseInt(version.substring(0, version.indexOf('.') < 0 ? version.length() : version.indexOf('.')));
        } catch (NumberFormatException invalid) {
            return version;
        }
        return ">=" + major + ".0.0 <" + (major + 1) + ".0.0";
    }

    private static String newTraceparent() {
        HexFormat hex = HexFormat.of();
        byte[] trace = new byte[16];
        byte[] span = new byte[8];
        ThreadLocalRandom.current().nextBytes(trace);
        ThreadLocalRandom.current().nextBytes(span);
        trace[0] |= 1;
        span[0] |= 1;
        return "00-" + hex.formatHex(trace) + "-" + hex.formatHex(span) + "-01";
    }

    /**
     * Waits for the handler until the invocation's deadline (root BRX-RS-RESILIENCE-001; Kafka design §14 K10). A
     * handler still running then is told through its cancellation signal, and the delivery fails, so its unit of
     * work rolls back and the attempt counts.
     */
    private void await(CompletionStage<Void> handled, Instant deadline, java.util.concurrent.atomic.AtomicBoolean cancelled) {
        java.util.concurrent.CompletableFuture<Void> future = handled.toCompletableFuture();
        long wait = Math.max(0L, Duration.between(clock.instant(), deadline).toMillis());
        try {
            future.get(wait, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (java.util.concurrent.TimeoutException late) {
            cancelled.set(true);
            future.cancel(true);
            throw new IllegalStateException("the handler did not finish by its deadline " + deadline, late);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            cancelled.set(true);
            throw new IllegalStateException("the delivery was interrupted", interrupted);
        } catch (java.util.concurrent.ExecutionException failure) {
            if (failure.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw new CompletionException(failure.getCause());
        }
    }

    private static EventDeliveryException permanent(String code, String message, Throwable cause) {
        return new EventDeliveryException(code, message, cause, true);
    }
}
