/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.invocation;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.runtime.sdk.event.EventScope;
import io.runtime.sdk.plugin.AccessDecision;
import io.runtime.sdk.plugin.CallerIdentity;
import io.runtime.sdk.plugin.CancellationSignal;
import io.runtime.sdk.plugin.CommandInvocation;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.EventInvocation;
import io.runtime.sdk.plugin.InvocationScope;
import io.runtime.sdk.plugin.TraceContext;

/**
 * The call a module's handler is running in, as the runtime admitted it.
 *
 * <p>L2B internal. Dispatchers bind it around a handler; the query gateway derives a query's caller, scope,
 * access decision and deadline from it (root BRX-RS-MANIFEST-028; multi-tenant §11.1), so a plugin never passes
 * or forges them. It is not a plugin contract.</p>
 *
 * @param caller the module running the handler, and the user it acts for
 * @param kind what kind of entry admitted the call
 * @param platformPermissions verified permissions of a platform user; tenant users get theirs from the grant table
 * @param scope the tenant scope of the call
 * @param access the access decision of the call
 * @param trace the trace context of the call, when the entry carried one
 * @param deadline when the call's answer is no longer wanted
 * @param cancellation tells the handler its caller has given up
 * @param depth how many queries deep the call is; 0 for an entry the runtime admitted from outside
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public record RuntimeInvocation(
        CallerIdentity caller,
        Kind kind,
        Set<String> platformPermissions,
        InvocationScope scope,
        AccessDecision access,
        Optional<TraceContext> trace,
        Instant deadline,
        CancellationSignal cancellation,
        int depth) {

    /** The entry kinds that admit a call. */
    public enum Kind {
        /** An HTTP endpoint. */
        HTTP,
        /** An event delivery. */
        EVENT,
        /** A command delivery. */
        COMMAND,
        /** A typed query. */
        QUERY,
        /** A managed task. */
        TASK
    }

    /**
     * Validates the call.
     */
    public RuntimeInvocation {
        Objects.requireNonNull(caller, "caller must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        platformPermissions = Set.copyOf(Objects.requireNonNull(platformPermissions, "platformPermissions must not be null"));
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(access, "access must not be null");
        Objects.requireNonNull(trace, "trace must not be null");
        Objects.requireNonNull(deadline, "deadline must not be null");
        Objects.requireNonNull(cancellation, "cancellation must not be null");
        if (depth < 0) {
            throw new IllegalArgumentException("depth must not be negative");
        }
    }

    /**
     * Returns whether the call's user holds a permission: a tenant user holds what the grant table gives their role,
     * a platform user what their token carries.
     *
     * @param permission the permission
     * @return {@code true} when the user holds it; {@code false} without a user
     */
    public boolean holds(String permission) {
        if (!caller.hasPrincipal()) {
            return false;
        }
        boolean tenantUser = caller.principalRole()
            .filter(role -> "actor".equals(role) || "subject".equals(role)).isPresent();
        if (tenantUser) {
            return access.effectivePermissions().containsKey(permission);
        }
        return platformPermissions.contains(permission);
    }

    /**
     * Returns the call of an HTTP endpoint handler.
     *
     * @param pluginId the module that owns the endpoint
     * @param invocation the verified endpoint invocation
     * @param platformPermissions the verified permissions of a platform user, empty for a tenant user
     * @param trace the W3C trace context of the request, when it carried one
     * @return the call
     */
    public static RuntimeInvocation forEndpoint(String pluginId, EndpointInvocation<?> invocation,
            Set<String> platformPermissions, Optional<TraceContext> trace) {
        Optional<String> principal = invocation.actorId().filter(id -> !id.isBlank());
        Optional<String> role = principal.isPresent() ? invocation.tokenRole() : Optional.empty();
        return new RuntimeInvocation(
            new CallerIdentity(pluginId, principal, role),
            Kind.HTTP,
            principal.isPresent() ? platformPermissions : Set.of(),
            invocation.tenantId().filter(id -> !id.isBlank()).map(InvocationScope::tenant)
                .orElse(InvocationScope.platform()),
            invocation.access(),
            trace,
            invocation.deadline(),
            CancellationSignal.none(),
            0);
    }

    /**
     * Returns the call of an event handler: no user, the event's tenant.
     *
     * @param pluginId the subscribing module
     * @param invocation the event invocation
     * @return the call
     */
    public static RuntimeInvocation forEvent(String pluginId, EventInvocation<?> invocation) {
        return service(pluginId, Kind.EVENT, invocation.event().scope(), invocation.event().tenantId(),
            invocation.trace(), invocation.deadline(), invocation.cancellation());
    }

    /**
     * Returns the call of a command handler: no user, the command's tenant.
     *
     * @param pluginId the providing module
     * @param invocation the command invocation
     * @return the call
     */
    public static RuntimeInvocation forCommand(String pluginId, CommandInvocation<?> invocation) {
        return service(pluginId, Kind.COMMAND, invocation.command().scope(), invocation.command().tenantId(),
            invocation.trace(), invocation.deadline(), invocation.cancellation());
    }

    /**
     * Returns the call a query's provider runs in: the same user and scope, one level deeper.
     *
     * @param providerPluginId the providing module
     * @param queryTrace the trace context of the query
     * @param deadline the query's own deadline
     * @param cancellation tells the provider the query was given up
     * @return the call
     */
    public RuntimeInvocation nested(String providerPluginId, TraceContext queryTrace, Instant deadline,
            CancellationSignal cancellation) {
        return new RuntimeInvocation(
            new CallerIdentity(providerPluginId, caller.principalId(), caller.principalRole()),
            Kind.QUERY,
            platformPermissions,
            scope,
            access,
            Optional.of(queryTrace),
            deadline,
            cancellation,
            depth + 1);
    }

    private static RuntimeInvocation service(String pluginId, Kind kind, EventScope scope, String tenantId,
            TraceContext trace, Instant deadline, CancellationSignal cancellation) {
        InvocationScope invocationScope = scope == EventScope.TENANT && tenantId != null && !tenantId.isBlank()
            ? InvocationScope.tenant(tenantId)
            : InvocationScope.platform();
        return new RuntimeInvocation(CallerIdentity.service(pluginId), kind, Set.of(), invocationScope,
            AccessDecision.none(), Optional.ofNullable(trace), deadline, cancellation, 0);
    }
}
