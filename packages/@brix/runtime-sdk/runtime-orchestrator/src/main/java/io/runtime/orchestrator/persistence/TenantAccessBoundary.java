/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.persistence;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.sdk.capability.TenantAccessCapability;
import io.runtime.sdk.capability.TenantAccessCapability.DiscoveryContext;
import io.runtime.sdk.capability.TenantAccessCapability.TenantMembershipRecord;

/**
 * Binds every call the identity protocol surface makes to {@link TenantAccessCapability} to the
 * tenant scope multi-tenant §6.4 allows for it.
 *
 * <p>The callers are trusted L2C and platform code (the hosted login, the BFF, token issuance and
 * validation); the capability is never visible to business plugins (§6.3). A call that names
 * a tenant reaches that tenant only. Listing an identity's memberships is login-time discovery
 * and resolving a FIRST_OWNER invitation by its token is invitation resolution, the two
 * registered policies this capability uses. Any other method, including the ones RFC-006 and
 * RFC-007 retire, gets no tenant scope, so the persistence adapter refuses its tenant data.</p>
 *
 * <p>Every discovery is written to the platform audit before its result leaves the boundary (root
 * BRX-RS-DATA-047). When the record cannot be written the call fails and the result is dropped.</p>
 */
public final class TenantAccessBoundary {

    private static final Logger audit = LoggerFactory.getLogger("brix.tenant.isolation.audit");

    private TenantAccessBoundary() {
    }

    /**
     * Wraps the Data Owner's implementation without a policy audit writer: every discovery fails.
     *
     * @param target the platform-tenant implementation
     * @return the bounded capability
     */
    public static TenantAccessCapability wrap(TenantAccessCapability target) {
        return wrap(target, () -> null);
    }

    /**
     * Wraps the Data Owner's implementation.
     *
     * @param target the platform-tenant implementation
     * @param writer supplies the policy audit writer when a policy is used; may supply {@code null}
     * @return the bounded capability
     */
    public static TenantAccessCapability wrap(TenantAccessCapability target, Supplier<PolicyAuditWriter> writer) {
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(writer, "writer must not be null");
        if (Proxy.isProxyClass(target.getClass()) && Proxy.getInvocationHandler(target) instanceof Handler) {
            return target;
        }
        return (TenantAccessCapability) Proxy.newProxyInstance(
            TenantAccessCapability.class.getClassLoader(),
            new Class<?>[] {TenantAccessCapability.class},
            new Handler(target, writer));
    }

    /**
     * Returns the scope a capability call may use.
     *
     * @param method capability method
     * @param args call arguments
     * @return the scope the Data Owner's unit of work binds
     */
    static TenantScope scopeOf(Method method, Object[] args) {
        int arity = args == null ? 0 : args.length;
        switch (method.getName()) {
            case "getActiveMemberships":
                return (arity == 1 || arity == 2) && args[0] instanceof Long identityId
                    ? TenantScope.policy(TenantAccessPolicy.LOGIN_TENANT_DISCOVERY, identityId)
                    : TenantScope.none();
            case "findPendingFirstOwnerInvitation":
                return TenantScope.policy(TenantAccessPolicy.FIRST_OWNER_INVITATION_RESOLUTION, null);
            case "findMembership":
            case "findPrincipalship":
                return arity == 2 ? tenant(args[1]) : TenantScope.none();
            case "findMembershipByContext":
            case "findPrincipalshipByContext":
            case "touchMemberAccess":
            case "touchPrincipalAccess":
                return arity == 2 ? tenant(args[0]) : TenantScope.none();
            default:
                return TenantScope.none();
        }
    }

    private static TenantScope tenant(Object tenantId) {
        return tenantId instanceof Long id ? TenantScope.tenant(id) : TenantScope.none();
    }

    private static final class Handler implements InvocationHandler {

        private final TenantAccessCapability target;
        private final Supplier<PolicyAuditWriter> writer;

        private Handler(TenantAccessCapability target, Supplier<PolicyAuditWriter> writer) {
            this.target = target;
            this.writer = writer;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "TenantAccessBoundary[" + target + "]";
                    default -> throw new IllegalStateException("Unsupported Object method " + method);
                };
            }
            TenantScope scope = scopeOf(method, args);
            Object result;
            try {
                result = TenantScopes.within(scope, () -> invokeTarget(method, args));
            } catch (TargetFailure failure) {
                throw failure.getCause();
            }
            if (scope instanceof TenantScope.Policy policy
                    && policy.policy() == TenantAccessPolicy.LOGIN_TENANT_DISCOVERY) {
                recordDiscovery((Long) policy.key(), result, args.length == 2 ? args[1] : null);
            }
            return result;
        }

        private void recordDiscovery(Long identityId, Object result, Object discoveryContext) {
            List<?> found = result instanceof List<?> list ? list : List.of();
            List<Long> tenants = found.stream()
                .filter(TenantMembershipRecord.class::isInstance)
                .map(membership -> ((TenantMembershipRecord) membership).tenantId())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
            PolicyUseRecord record = PolicyUseRecord.discovery(identityId, tenants, found.size(),
                discoveryContext instanceof DiscoveryContext context ? context : null, PolicyUseRecord.currentTraceId());
            PolicyAuditWriter.require(writer.get(), record);
            audit.info("event=tenant.policy.used policy={} action={} identity={} matched={} trace={}",
                record.policy().id(), record.action(), identityId, found.size(), record.traceId());
        }

        private Object invokeTarget(Method method, Object[] args) {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new TargetFailure(cause);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Tenant access boundary cannot invoke " + method, e);
            }
        }
    }

    private static final class TargetFailure extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private TargetFailure(Throwable cause) {
            super(cause);
        }
    }
}
