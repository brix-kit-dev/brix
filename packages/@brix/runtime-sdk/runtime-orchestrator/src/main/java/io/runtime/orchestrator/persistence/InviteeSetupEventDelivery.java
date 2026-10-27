/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.persistence;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
import io.runtime.sdk.event.identity.FirstOwnerInviteeSetupCompletedEvent;
import io.runtime.sdk.plugin.EventHandler;

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
 */
public final class InviteeSetupEventDelivery {

    /** Event type plugin manifests subscribe to. */
    public static final String EVENT_TYPE = "FirstOwnerInviteeSetupCompleted";

    private static final Logger audit = LoggerFactory.getLogger("brix.tenant.isolation.audit");

    private final Function<String, List<BoundEventHandler>> handlers;
    private final OwnerPersistenceProvider ownerPersistence;
    private final ObjectProvider<FirstOwnerInviteeIdentitySetupCapability> identitySetup;

    public InviteeSetupEventDelivery(
            PluginRuntimeManager plugins,
            OwnerPersistenceProvider ownerPersistence,
            ObjectProvider<FirstOwnerInviteeIdentitySetupCapability> identitySetup) {
        this(Objects.requireNonNull(plugins, "plugins must not be null")::eventHandlers, ownerPersistence, identitySetup);
    }

    InviteeSetupEventDelivery(
            Function<String, List<BoundEventHandler>> handlers,
            OwnerPersistenceProvider ownerPersistence,
            ObjectProvider<FirstOwnerInviteeIdentitySetupCapability> identitySetup) {
        this.handlers = Objects.requireNonNull(handlers, "handlers must not be null");
        this.ownerPersistence = Objects.requireNonNull(ownerPersistence, "ownerPersistence must not be null");
        this.identitySetup = Objects.requireNonNull(identitySetup, "identitySetup must not be null");
    }

    /**
     * Delivers the fact once the identity owner has committed it.
     *
     * @param event setup-completed fact
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSetupCompleted(FirstOwnerInviteeSetupCompletedEvent event) {
        for (BoundEventHandler bound : handlers.apply(EVENT_TYPE)) {
            TenantScope scope = scopeOf(bound, event);
            @SuppressWarnings("unchecked")
            EventHandler<Object> handler = (EventHandler<Object>) bound.handler();
            Supplier<Object> call = () -> {
                handler.handle(event);
                return null;
            };
            Optional<PersistenceCapability> persistence = ownerPersistence.forOwner(bound.pluginId());
            TenantScopes.within(scope, () -> persistence.isPresent() ? persistence.get().unitOfWork().required(call) : call.get());
            if (scope instanceof TenantScope.Policy policy) {
                audit.info("event=tenant.policy.used policy={} action={} identity={} subscription={}",
                    policy.policy().id(), policy.policy().auditAction().orElse(""), event.identityId(),
                    bound.subscription().subscriptionId());
            }
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
