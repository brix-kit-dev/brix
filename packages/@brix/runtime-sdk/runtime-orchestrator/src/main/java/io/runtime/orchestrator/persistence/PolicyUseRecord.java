/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.persistence;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.MDC;

import io.runtime.sdk.capability.TenantAccessCapability.DiscoveryContext;
import io.runtime.sdk.security.RequestMetadataMasking;

/**
 * One use of a registered cross-tenant policy, as the platform audit stores it (platform-audit design
 * §5.3, record schema {@code brix://schemas/platform-audit/policy-use-record/1.0.0}).
 *
 * <p>Only typed fields: no e-mail address, token or URL can get in. The client address is masked
 * when the record is made.</p>
 *
 * @param policy the policy used
 * @param actorIdentityId identity the use was made for
 * @param resourceType what the policy reached
 * @param affectedTenants tenants the use reached, in binding order
 * @param context typed keys only ({@code entry}, {@code client}, {@code matched}, {@code subscription})
 * @param success whether the use succeeded
 * @param errorCode stable code of a failed use, or {@code null}
 * @param traceId trace id, or a correlation id made for this use
 * @param sourceAddress masked client address, or {@code null}
 * @param userAgent truncated user agent, or {@code null}
 */
public record PolicyUseRecord(
        TenantAccessPolicy policy,
        long actorIdentityId,
        String resourceType,
        List<Long> affectedTenants,
        Map<String, String> context,
        boolean success,
        String errorCode,
        String traceId,
        String sourceAddress,
        String userAgent) {

    /** MDC key the observability adapter fills with the current trace id. */
    static final String TRACE_KEY = "traceId";

    private static final Pattern TRACE_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");
    private static final Pattern CLIENT = Pattern.compile("[a-z][a-z0-9-]{0,31}");

    public PolicyUseRecord {
        Objects.requireNonNull(policy, "policy must not be null");
        if (policy.auditAction().isEmpty()) {
            throw new IllegalArgumentException("Policy " + policy.id() + " records no platform audit");
        }
        Objects.requireNonNull(resourceType, "resourceType must not be null");
        affectedTenants = List.copyOf(affectedTenants);
        context = Map.copyOf(context);
        Objects.requireNonNull(traceId, "traceId must not be null");
    }

    /** Platform audit action of the policy. */
    public String action() {
        return policy.auditAction().orElseThrow();
    }

    /** Identity the use ran as. */
    public String executionIdentity() {
        return "runtime:policy:" + policy.id();
    }

    /** Stable reason of the use: the policy id (multi-tenant §6.3). */
    public String reasonCode() {
        return policy.id();
    }

    /**
     * A login-time discovery (P-1): the memberships it returned and where it came from.
     */
    static PolicyUseRecord discovery(long identityId, List<Long> tenants, int matched, DiscoveryContext from, String traceId) {
        Map<String, String> context = new LinkedHashMap<>();
        context.put("entry", from == null || from.purpose() == null ? "unknown" : from.purpose().name().toLowerCase(Locale.ROOT));
        if (from != null && from.client() != null && CLIENT.matcher(from.client()).matches()) {
            context.put("client", from.client());
        }
        context.put("matched", Integer.toString(matched));
        return new PolicyUseRecord(TenantAccessPolicy.LOGIN_TENANT_DISCOVERY, identityId, "SELF", tenants, context,
            true, null, traceId, RequestMetadataMasking.maskAddress(from == null ? null : from.sourceAddress()), null);
    }

    /**
     * The resend after an invitee completed setup (P-3), with the tenants the adapter bound.
     */
    static PolicyUseRecord inviteeSetupFanout(
            long identityId, Collection<Long> tenants, String subscriptionId, boolean success, String errorCode, String traceId) {
        return new PolicyUseRecord(TenantAccessPolicy.FIRST_OWNER_INVITEE_SETUP_FANOUT, identityId, "INVITATION",
            List.copyOf(tenants), Map.of("subscription", subscriptionId), success, errorCode, traceId, null, null);
    }

    /**
     * Returns the current trace id, or a new correlation id when there is none (root ERROR-013).
     */
    static String currentTraceId() {
        String current = MDC.get(TRACE_KEY);
        return current != null && TRACE_ID.matcher(current).matches() ? current : UUID.randomUUID().toString();
    }
}
