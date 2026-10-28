/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.persistence;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import io.runtime.orchestrator.plugin.PluginRuntimeManager;
import io.runtime.orchestrator.plugin.PluginRuntimeManager.BoundEventHandler;
import io.runtime.sdk.capability.FirstOwnerInviteeIdentitySetupCapability;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.error.BrixException;
import io.runtime.sdk.error.PolicyAuditException;
import io.runtime.sdk.event.EventEnvelope;
import io.runtime.sdk.event.EventReliability;
import io.runtime.sdk.event.EventScope;
import io.runtime.sdk.event.identity.FirstOwnerInviteeSetupCompletedEvent;
import io.runtime.sdk.plugin.CancellationSignal;
import io.runtime.sdk.plugin.EventHandler;
import io.runtime.sdk.plugin.EventInvocation;
import io.runtime.sdk.plugin.PublisherDecision;
import io.runtime.sdk.plugin.TraceContext;

/**
 * Delivers the identity owner's setup-completed fact, in process and after the identity commit,
 * to the plugin handlers bound for it (multi-tenant §6.4, D2-A of the isolation design).
 *
 * <p>Each handler runs in its owner's unit of work with the scope its manifest subscription
 * names. For the post-setup resend that is the {@code first-owner-invitee-setup-fanout} policy,
 * keyed by the e-mail of the identity that completed setup, which the runtime takes from the
 * identity owner; the handler cannot choose it. The delivery is reliable once the identity owner
 * publishes this fact through an outbox; until then it keeps the in-process semantics the plugin
 * listener had.</p>
 *
 * <p>A policy use is written to the platform audit before the owner's transaction commits (root
 * BRX-RS-DATA-047): when the record cannot be written the transaction rolls back. When the handler
 * fails, a failure record is written after the rollback, as far as possible.</p>
 */
public final class InviteeSetupEventDelivery {

    /** Event type plugin manifests subscribe to. */
    public static final String EVENT_TYPE = "FirstOwnerInviteeSetupCompleted";

    private static final String HANDLER_FAILED = "policy.handler_failed";

    /** The identity owner publishes this fact (identity-auth OWNER-001). */
    private static final String PUBLISHER = "platform-identity";

    private static final Duration DEADLINE = Duration.ofSeconds(30);

    private static final Logger audit = LoggerFactory.getLogger("brix.tenant.isolation.audit");
    private static final Logger log = LoggerFactory.getLogger(InviteeSetupEventDelivery.class);

    private final Function<String, List<BoundEventHandler>> handlers;
    private final OwnerPersistenceProvider ownerPersistence;
    private final ObjectProvider<FirstOwnerInviteeIdentitySetupCapability> identitySetup;
    private final Supplier<PolicyAuditWriter> writer;

    public InviteeSetupEventDelivery(
            PluginRuntimeManager plugins,
            OwnerPersistenceProvider ownerPersistence,
            ObjectProvider<FirstOwnerInviteeIdentitySetupCapability> identitySetup,
            Supplier<PolicyAuditWriter> writer) {
        this(Objects.requireNonNull(plugins, "plugins must not be null")::eventHandlers, ownerPersistence, identitySetup, writer);
    }

    InviteeSetupEventDelivery(
            Function<String, List<BoundEventHandler>> handlers,
            OwnerPersistenceProvider ownerPersistence,
            ObjectProvider<FirstOwnerInviteeIdentitySetupCapability> identitySetup,
            Supplier<PolicyAuditWriter> writer) {
        this.handlers = Objects.requireNonNull(handlers, "handlers must not be null");
        this.ownerPersistence = Objects.requireNonNull(ownerPersistence, "ownerPersistence must not be null");
        this.identitySetup = Objects.requireNonNull(identitySetup, "identitySetup must not be null");
        this.writer = Objects.requireNonNull(writer, "writer must not be null");
    }

    /**
     * Delivers the fact once the identity owner has committed it.
     *
     * @param event setup-completed fact
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSetupCompleted(FirstOwnerInviteeSetupCompletedEvent event) {
        for (BoundEventHandler bound : handlers.apply(EVENT_TYPE)) {
            deliver(bound, event);
        }
    }

    private void deliver(BoundEventHandler bound, FirstOwnerInviteeSetupCompletedEvent event) {
        TenantScope scope = scopeOf(bound, event);
        boolean audited = scope instanceof TenantScope.Policy policy && policy.policy().auditAction().isPresent();
        String subscription = bound.subscription().subscriptionId();
        String traceId = PolicyUseRecord.currentTraceId();
        List<Long> reached = new ArrayList<>();
        @SuppressWarnings("unchecked")
        EventHandler<Object> handler = (EventHandler<Object>) bound.handler();
        if (!bound.payloadType().isInstance(event)) {
            throw new IllegalStateException("Event handler " + bound.subscription().handlerId() + " of " + bound.pluginId()
                + " does not take " + EVENT_TYPE);
        }
        EventInvocation<Object> invocation = invocation(event, bound, traceId);
        Supplier<Object> call = () -> {
            try {
                await(handler.handle(invocation));
                if (audited) {
                    PolicyAuditWriter.require(writer.get(), PolicyUseRecord.inviteeSetupFanout(
                        event.identityId(), TenantScopes.bound(), subscription, true, null, traceId));
                }
                return null;
            } finally {
                reached.addAll(TenantScopes.bound());
            }
        };
        Optional<PersistenceCapability> persistence = ownerPersistence.forOwner(bound.pluginId());
        try {
            TenantScopes.within(scope, () -> persistence.isPresent() ? persistence.get().unitOfWork().required(call) : call.get());
        } catch (RuntimeException failure) {
            if (audited && !isAuditWriteFailure(failure)) {
                recordFailure(event, reached, subscription, failure, traceId);
            }
            throw failure;
        }
        if (audited) {
            audit.info("event=tenant.policy.used policy={} identity={} subscription={} tenants={} trace={}",
                ((TenantScope.Policy) scope).policy().id(), event.identityId(), subscription, reached.size(), traceId);
        }
    }

    private void recordFailure(
            FirstOwnerInviteeSetupCompletedEvent event, List<Long> reached, String subscription, RuntimeException failure,
            String traceId) {
        try {
            PolicyAuditWriter.require(writer.get(), PolicyUseRecord.inviteeSetupFanout(
                event.identityId(), reached, subscription, false, errorCode(failure), traceId));
        } catch (RuntimeException auditFailure) {
            log.error("event=tenant.policy.audit_failed policy={} identity={} subscription={} trace={}",
                TenantAccessPolicy.FIRST_OWNER_INVITEE_SETUP_FANOUT.id(), event.identityId(), subscription, traceId, auditFailure);
            failure.addSuppressed(auditFailure);
        }
    }

    private static boolean isAuditWriteFailure(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof PolicyAuditException) {
                return true;
            }
        }
        return false;
    }

    private static String errorCode(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof BrixException brix && brix.errorCode() != null) {
                return brix.errorCode().wireCode();
            }
        }
        return HANDLER_FAILED;
    }

    /**
     * The delivery as the plugin event SPI describes it (root BRX-RS-CAP-025). Until the identity owner
     * publishes this fact through its outbox (P4d) it is delivered in process, once.
     */
    private static EventInvocation<Object> invocation(
            FirstOwnerInviteeSetupCompletedEvent event, BoundEventHandler bound, String traceId) {
        String traceparent = traceparent(traceId);
        EventEnvelope<Object> envelope = new EventEnvelope<>(
            UUID.randomUUID().toString(), EVENT_TYPE, "1.0.0", Instant.now(), PUBLISHER, EventScope.PLATFORM, null,
            traceId, null, traceparent, String.valueOf(event.identityId()), event, EventReliability.CRITICAL);
        return new EventInvocation<>(envelope, new PublisherDecision(PUBLISHER, null), new TraceContext(traceparent, null),
            Instant.now().plus(DEADLINE), CancellationSignal.none(), 1);
    }

    /** A W3C traceparent for the trace id, or a new trace when the id is not a 32-digit hexadecimal one. */
    static String traceparent(String traceId) {
        String candidate = traceId == null ? "" : traceId.replace("-", "").toLowerCase(Locale.ROOT);
        String trace = candidate.matches("[0-9a-f]{32}") && !candidate.matches("0{32}")
            ? candidate
            : HexFormat.of().formatHex(randomBytes(16));
        return "00-" + trace + "-" + HexFormat.of().formatHex(randomBytes(8)) + "-01";
    }

    private static byte[] randomBytes(int count) {
        byte[] bytes = new byte[count];
        ThreadLocalRandom.current().nextBytes(bytes);
        bytes[0] |= 1;
        return bytes;
    }

    private static void await(CompletionStage<Void> handled) {
        try {
            handled.toCompletableFuture().join();
        } catch (CompletionException failure) {
            if (failure.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            throw failure;
        }
    }

    private TenantScope scopeOf(BoundEventHandler bound, FirstOwnerInviteeSetupCompletedEvent event) {
        Optional<TenantAccessPolicy> policy = TenantAccessPolicy.byId(bound.subscription().scopePolicyRef());
        if (policy.isEmpty()) {
            return TenantScope.none();
        }
        if (policy.get() == TenantAccessPolicy.FIRST_OWNER_INVITEE_SETUP_FANOUT) {
            FirstOwnerInviteeIdentitySetupCapability identities = identitySetup.getIfAvailable();
            if (identities == null) {
                return TenantScope.none();
            }
            return TenantScope.policy(policy.get(), identities.requireActiveIdentityEmail(event.identityId()));
        }
        return policy.get().keyBoundByRuntime() ? TenantScope.none() : TenantScope.policy(policy.get(), null);
    }
}
