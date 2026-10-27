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
import java.util.Collection;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.sdk.capability.TenantAccessCapability;

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
 */
public final class TenantAccessBoundary {

    private static final Logger audit = LoggerFactory.getLogger("brix.tenant.isolation.audit");

    private TenantAccessBoundary() {
    }

    /**
     * Wraps the Data Owner's implementation.
     *
     * @param target the platform-tenant implementation
     * @return the bounded capability
     */
    public static TenantAccessCapability wrap(TenantAccessCapability target) {
        Objects.requireNonNull(target, "target must not be null");
        if (Proxy.isProxyClass(target.getClass()) && Proxy.getInvocationHandler(target) instanceof Handler) {
            return target;
        }
        return (TenantAccessCapability) Proxy.newProxyInstance(
            TenantAccessCapability.class.getClassLoader(),
            new Class<?>[] {TenantAccessCapability.class},
            new Handler(target));
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
                return arity == 1 && args[0] instanceof Long identityId
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

        private Handler(TenantAccessCapability target) {
            this.target = target;
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
                audit.info("event=tenant.policy.used policy={} action={} identity={} matched={}",
                    policy.policy().id(), policy.policy().auditAction().orElse(""), policy.key(),
                    result instanceof Collection<?> found ? found.size() : 0);
            }
            return result;
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
