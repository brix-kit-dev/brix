/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.query;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.runtime.orchestrator.invocation.RuntimeInvocations;
import io.runtime.sdk.capability.CommandCapability;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.NotificationCapability;
import io.runtime.sdk.query.BrixQueryException;

/**
 * Refuses side effects inside a query (root BRX-RS-MESSAGE-002; query design §5.9).
 *
 * <p>A query's provider runs in a read-only unit of work, so the database refuses its writes, including outbox rows.
 * What does not go through its database is refused here: an event published, a command submitted or a notification
 * sent while the current thread runs a query. The capabilities a plugin gets are wrapped once, and the check runs at
 * each call, so the same capability keeps working in the plugin's other handlers.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class QuerySideEffects {

    private static final Set<Class<?>> GUARDED = Set.of(
        EventBusCapability.class, CommandCapability.class, NotificationCapability.class);

    private QuerySideEffects() {
    }

    /**
     * Wraps a capability with side effects so that it refuses calls made inside a query.
     *
     * @param type the capability contract
     * @param capability the capability
     * @param <C> capability type
     * @return the guarded capability, or {@code capability} itself when it has no side effects to refuse
     */
    public static <C> C guard(Class<C> type, C capability) {
        Objects.requireNonNull(type, "type must not be null");
        if (capability == null || !GUARDED.contains(type) || Proxy.isProxyClass(capability.getClass())
                && Proxy.getInvocationHandler(capability) instanceof Guard) {
            return capability;
        }
        Object proxy = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
            new Guard(type, capability));
        return type.cast(proxy);
    }

    /**
     * Returns the capability a guard wraps.
     *
     * @param capability a capability, guarded or not
     * @return the wrapped capability, or {@code capability} itself
     */
    public static Object unwrap(Object capability) {
        if (capability != null && Proxy.isProxyClass(capability.getClass())
                && Proxy.getInvocationHandler(capability) instanceof Guard guard) {
            return guard.target();
        }
        return capability;
    }

    private record Guard(Class<?> type, Object target) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "equals" -> args != null && args.length == 1 && args[0] == proxy;
                    case "hashCode" -> System.identityHashCode(proxy);
                    default -> "Guarded(" + target + ")";
                };
            }
            if (RuntimeInvocations.inQuery()) {
                throw new BrixQueryException(BrixQueryException.Code.PROVIDER_FAILED,
                    Map.of("reason", "side-effect-in-query", "capability", type.getSimpleName()), null);
            }
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException failure) {
                throw failure.getCause();
            }
        }
    }
}
