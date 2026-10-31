/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.tenant.query;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.brix.platform.tenant.api.TenantReference;
import io.brix.platform.tenant.api.TenantReferenceQuery;
import io.brix.platform.tenant.enums.MemberStatus;
import io.brix.platform.tenant.enums.PrincipalStatus;
import io.brix.platform.tenant.repository.TenantMemberRepository;
import io.brix.platform.tenant.repository.TenantPrincipalRepository;
import io.runtime.sdk.plugin.QueryHandler;
import io.runtime.sdk.plugin.QueryInvocation;

/**
 * Answers {@code TenantReferenceQuery}: whether the member or principal id another plugin stores
 * belongs to the caller's tenant, and whether it is active (multi-tenant §7.1,
 * {@code BRX-TENANT-CONTRACT-003}, T-7).
 *
 * <p>Bound for the manifest query {@code platform-tenant.tenant-reference}. The runtime runs it in
 * platform-tenant's own read-only unit of work, with the caller's write transaction suspended. The
 * tenant comes from the caller's call, never from the request, so an id of another tenant is not
 * found, like one that does not exist.</p>
 */
public final class TenantReferenceQueryHandler
        implements QueryHandler<TenantReferenceQuery, Optional<TenantReference>> {

    private final TenantMemberRepository members;
    private final TenantPrincipalRepository principals;

    public TenantReferenceQueryHandler(TenantMemberRepository members, TenantPrincipalRepository principals) {
        this.members = Objects.requireNonNull(members, "members must not be null");
        this.principals = Objects.requireNonNull(principals, "principals must not be null");
    }

    @Override
    public CompletionStage<Optional<TenantReference>> handle(QueryInvocation<TenantReferenceQuery> query) {
        long tenantId = Long.parseLong(query.scope().tenantId().orElseThrow());
        TenantReferenceQuery reference = query.request();
        Optional<Boolean> active = switch (reference.refKind()) {
            case MEMBER -> members.findByIdAndTenantId(reference.refId(), tenantId)
                .map(member -> member.getStatus() == MemberStatus.ACTIVE);
            case PRINCIPAL -> principals.findByIdAndTenantId(reference.refId(), tenantId)
                .map(principal -> principal.getStatus() == PrincipalStatus.ACTIVE);
        };
        return CompletableFuture.completedFuture(active.map(TenantReference::new));
    }
}
