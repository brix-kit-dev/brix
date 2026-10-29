/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.persistence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.orchestrator.event.EventDeliveryScope;
import io.runtime.orchestrator.event.EventDeliveryScopes;
import io.runtime.orchestrator.outbox.CanonicalOutboxMessage;
import io.runtime.orchestrator.plugin.PluginRuntimeManager.BoundEventHandler;
import io.runtime.sdk.capability.FirstOwnerInviteeIdentitySetupCapability;
import io.runtime.sdk.error.BrixException;
import io.runtime.sdk.error.PolicyAuditException;
import io.runtime.sdk.event.identity.FirstOwnerInviteeSetupCompletedEvent;

/**
 * Delivery scopes of the registered cross-tenant policies (multi-tenant §6.4; P4 design §4.3, D16).
 *
 * <p>A PLATFORM event whose subscription names a registered policy is handled in that policy's scope.
 * For the post-setup resend, {@code first-owner-invitee-setup-fanout}, the access key is the e-mail of
 * the identity that completed setup, which the runtime takes from the identity owner when it delivers
 * that owner's committed fact; the handler cannot choose it. A policy whose key the runtime binds
 * elsewhere reaches no tenant data here. Every other delivery keeps its envelope's scope.</p>
 *
 * <p>Each use of an audited policy is written to the platform audit before the subscriber commits (root
 * BRX-RS-DATA-047): when the record cannot be written the unit of work rolls back and the relay retries
 * the event. A failed use is recorded after the rollback, as far as possible. A redelivered event that
 * the inbox skips is not a use and is not recorded. The record carries the delivered fact's trace.</p>
 */
public final class PolicyEventDeliveryScopes implements EventDeliveryScopes {

    private static final String HANDLER_FAILED = "policy.handler_failed";
    private static final Logger audit = LoggerFactory.getLogger("brix.tenant.isolation.audit");
    private static final Logger log = LoggerFactory.getLogger(PolicyEventDeliveryScopes.class);

    private final Supplier<FirstOwnerInviteeIdentitySetupCapability> identitySetup;
    private final Supplier<PolicyAuditWriter> writer;

    /**
     * Creates the scopes.
     *
     * @param identitySetup the identity owner's setup capability, resolved at delivery
     * @param writer the policy audit writer, resolved at delivery
     */
    public PolicyEventDeliveryScopes(
            Supplier<FirstOwnerInviteeIdentitySetupCapability> identitySetup,
            Supplier<PolicyAuditWriter> writer) {
        this.identitySetup = Objects.requireNonNull(identitySetup, "identitySetup must not be null");
        this.writer = Objects.requireNonNull(writer, "writer must not be null");
    }

    @Override
    public EventDeliveryScope scopeOf(BoundEventHandler bound, CanonicalOutboxMessage message, Object payload) {
        Optional<TenantAccessPolicy> policy = TenantAccessPolicy.byId(bound.subscription().scopePolicyRef());
        if (policy.isEmpty() || !"PLATFORM".equals(message.scope())) {
            return EventDeliveryScopes.envelope(message);
        }
        if (policy.get() != TenantAccessPolicy.FIRST_OWNER_INVITEE_SETUP_FANOUT) {
            TenantScope scope = policy.get().keyBoundByRuntime() ? TenantScope.none() : TenantScope.policy(policy.get(), null);
            return () -> scope;
        }
        if (!(payload instanceof FirstOwnerInviteeSetupCompletedEvent setup)) {
            throw new IllegalStateException(bound.subscription().handlerId() + " of " + bound.pluginId() + " names "
                + policy.get().id() + " but does not take FirstOwnerInviteeSetupCompleted");
        }
        FirstOwnerInviteeIdentitySetupCapability identities = identitySetup.get();
        if (identities == null) {
            throw new IllegalStateException("No identity owner can bind " + policy.get().id() + " for "
                + bound.subscription().handlerId());
        }
        return new InviteeSetupFanout(setup.identityId(), identities.requireActiveIdentityEmail(setup.identityId()),
            bound.subscription().subscriptionId(), traceId(message));
    }

    /** The trace of the delivered fact, so the record and the outbox row share it; a new id without one. */
    private static String traceId(CanonicalOutboxMessage message) {
        String traceparent = message.traceparent();
        if (traceparent != null && traceparent.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")) {
            return traceparent.substring(3, 35);
        }
        return PolicyUseRecord.currentTraceId();
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

    /** The resend after an invitee completed setup (P-3), keyed by that identity's e-mail. */
    private final class InviteeSetupFanout implements EventDeliveryScope {

        private final long identityId;
        private final String inviteeEmail;
        private final String subscription;
        private final String traceId;

        InviteeSetupFanout(long identityId, String inviteeEmail, String subscription, String traceId) {
            this.identityId = identityId;
            this.inviteeEmail = inviteeEmail;
            this.subscription = subscription;
            this.traceId = traceId;
        }

        @Override
        public TenantScope tenantScope() {
            return TenantScope.policy(TenantAccessPolicy.FIRST_OWNER_INVITEE_SETUP_FANOUT, inviteeEmail);
        }

        @Override
        public void handled(List<Long> reached) {
            PolicyAuditWriter.require(writer.get(), PolicyUseRecord.inviteeSetupFanout(
                identityId, reached, subscription, true, null, traceId));
        }

        @Override
        public void committed(List<Long> reached) {
            audit.info("event=tenant.policy.used policy={} identity={} subscription={} tenants={} trace={}",
                TenantAccessPolicy.FIRST_OWNER_INVITEE_SETUP_FANOUT.id(), identityId, subscription, reached.size(), traceId);
        }

        @Override
        public void failed(List<Long> reached, RuntimeException failure) {
            if (isAuditWriteFailure(failure)) {
                return;
            }
            try {
                PolicyAuditWriter.require(writer.get(), PolicyUseRecord.inviteeSetupFanout(
                    identityId, reached, subscription, false, errorCode(failure), traceId));
            } catch (RuntimeException auditFailure) {
                log.error("event=tenant.policy.audit_failed policy={} identity={} subscription={} trace={}",
                    TenantAccessPolicy.FIRST_OWNER_INVITEE_SETUP_FANOUT.id(), identityId, subscription, traceId,
                    auditFailure);
                failure.addSuppressed(auditFailure);
            }
        }
    }
}
