/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.query;

import java.time.Duration;
import java.util.Objects;

/**
 * The platform's catalog of the policies a query declaration names (root BRX-RS-MANIFEST-025, MANIFEST-026; query
 * design §5.3). A manifest refers to these names and cannot define its own; a name the runtime does not know stops
 * the plugin at startup.
 *
 * <ul>
 *   <li>scope {@value #SCOPE_CALLER_TENANT}: the provider runs in the caller's tenant; {@value #SCOPE_PLATFORM}: the
 *       provider reads only platform-wide data;</li>
 *   <li>access {@value #ACCESS_DECLARED_CONSUMERS}: any plugin declaring the query may call it;
 *       {@code on-behalf-of:<permission>}: the user the caller acts for must hold the provider's permission;
 *       {@code on-behalf-of-or-service:<permission>}: the same, or a call without a user;</li>
 *   <li>timeout {@value #TIMEOUT_INTERACTIVE} and {@value #TIMEOUT_BACKGROUND}, each set by the Host;</li>
 *   <li>concurrency {@value #CONCURRENCY_STANDARD}, set by the Host;</li>
 *   <li>retry {@value #RETRY_NONE} and {@value #RETRY_TRANSIENT}.</li>
 * </ul>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class QueryPolicies {

    /** The provider runs in the caller's tenant. */
    public static final String SCOPE_CALLER_TENANT = "query-caller-tenant";

    /** The provider reads only platform-wide data, for tenant and platform callers alike. */
    public static final String SCOPE_PLATFORM = "query-platform";

    /** Any plugin that declares the query under {@code queries.consumes} may call it. */
    public static final String ACCESS_DECLARED_CONSUMERS = "declared-consumers";

    /** Prefix of the policy whose permission the caller's user must hold. */
    public static final String ACCESS_ON_BEHALF_OF = "on-behalf-of:";

    /** Prefix of the policy whose permission the caller's user must hold, when there is a user. */
    public static final String ACCESS_ON_BEHALF_OF_OR_SERVICE = "on-behalf-of-or-service:";

    /** A query a user waits for. */
    public static final String TIMEOUT_INTERACTIVE = "query-interactive";

    /** A query run while handling an event, a command or a task. */
    public static final String TIMEOUT_BACKGROUND = "query-background";

    /** The Host's standard concurrency limit. */
    public static final String CONCURRENCY_STANDARD = "query-standard";

    /** No retry. */
    public static final String RETRY_NONE = "query-no-retry";

    /** One retry after a transient failure, within the deadline. */
    public static final String RETRY_TRANSIENT = "query-transient-retry";

    private QueryPolicies() {
    }

    /** Where a provider runs. */
    public enum Scope {
        /** In the caller's tenant; a caller in no tenant is refused. */
        CALLER_TENANT,
        /** On platform-wide data only. */
        PLATFORM
    }

    /**
     * An access policy.
     *
     * @param kind what the policy checks
     * @param permission the provider's permission it requires, blank for {@link Kind#DECLARED_CONSUMERS}
     */
    public record Access(Kind kind, String permission) {

        /** What an access policy checks. */
        public enum Kind {
            /** That the caller declares the query. */
            DECLARED_CONSUMERS,
            /** That the caller's user holds the permission; a call without a user is refused. */
            ON_BEHALF_OF,
            /** That the caller's user, when there is one, holds the permission. */
            ON_BEHALF_OF_OR_SERVICE
        }

        /**
         * Validates the policy.
         */
        public Access {
            Objects.requireNonNull(kind, "kind must not be null");
            permission = permission == null ? "" : permission;
            if ((kind == Kind.DECLARED_CONSUMERS) != permission.isEmpty()) {
                throw new IllegalArgumentException("only on-behalf-of policies name a permission");
            }
        }
    }

    /**
     * Resolves a scope policy.
     *
     * @param reference the manifest's {@code scopePolicyRef}
     * @return the scope
     * @throws IllegalArgumentException for an unknown policy
     */
    public static Scope scope(String reference) {
        if (SCOPE_CALLER_TENANT.equals(reference)) {
            return Scope.CALLER_TENANT;
        }
        if (SCOPE_PLATFORM.equals(reference)) {
            return Scope.PLATFORM;
        }
        throw new IllegalArgumentException("scopePolicyRef '" + reference + "' is not a query scope policy; expected "
            + SCOPE_CALLER_TENANT + " or " + SCOPE_PLATFORM);
    }

    /**
     * Resolves an access policy.
     *
     * @param reference the manifest's {@code accessPolicyRef}
     * @return the policy
     * @throws IllegalArgumentException for an unknown policy
     */
    public static Access access(String reference) {
        if (ACCESS_DECLARED_CONSUMERS.equals(reference)) {
            return new Access(Access.Kind.DECLARED_CONSUMERS, "");
        }
        if (reference != null && reference.startsWith(ACCESS_ON_BEHALF_OF_OR_SERVICE)
                && reference.length() > ACCESS_ON_BEHALF_OF_OR_SERVICE.length()) {
            return new Access(Access.Kind.ON_BEHALF_OF_OR_SERVICE,
                reference.substring(ACCESS_ON_BEHALF_OF_OR_SERVICE.length()));
        }
        if (reference != null && reference.startsWith(ACCESS_ON_BEHALF_OF)
                && reference.length() > ACCESS_ON_BEHALF_OF.length()) {
            return new Access(Access.Kind.ON_BEHALF_OF, reference.substring(ACCESS_ON_BEHALF_OF.length()));
        }
        throw new IllegalArgumentException("accessPolicyRef '" + reference + "' is not a query access policy; expected "
            + ACCESS_DECLARED_CONSUMERS + ", " + ACCESS_ON_BEHALF_OF + "<permission> or "
            + ACCESS_ON_BEHALF_OF_OR_SERVICE + "<permission>");
    }

    /**
     * Resolves a timeout policy.
     *
     * @param reference the manifest's {@code timeoutPolicyRef}
     * @param settings the Host's settings
     * @return the timeout
     * @throws IllegalArgumentException for an unknown policy
     */
    public static Duration timeout(String reference, QuerySettings settings) {
        if (TIMEOUT_INTERACTIVE.equals(reference)) {
            return settings.interactiveTimeout();
        }
        if (TIMEOUT_BACKGROUND.equals(reference)) {
            return settings.backgroundTimeout();
        }
        throw new IllegalArgumentException("timeoutPolicyRef '" + reference + "' is not a query timeout policy; expected "
            + TIMEOUT_INTERACTIVE + " or " + TIMEOUT_BACKGROUND);
    }

    /**
     * Resolves a concurrency policy.
     *
     * @param reference the manifest's {@code concurrencyPolicyRef}
     * @param settings the Host's settings
     * @return how many calls of the query may run at once
     * @throws IllegalArgumentException for an unknown policy
     */
    public static int concurrency(String reference, QuerySettings settings) {
        if (CONCURRENCY_STANDARD.equals(reference)) {
            return settings.standardConcurrency();
        }
        throw new IllegalArgumentException("concurrencyPolicyRef '" + reference
            + "' is not a query concurrency policy; expected " + CONCURRENCY_STANDARD);
    }

    /**
     * Resolves a retry policy.
     *
     * @param reference the manifest's {@code retryPolicyRef}
     * @return {@code true} when the policy retries a transient failure once
     * @throws IllegalArgumentException for an unknown policy
     */
    public static boolean retriesTransient(String reference) {
        if (RETRY_NONE.equals(reference)) {
            return false;
        }
        if (RETRY_TRANSIENT.equals(reference)) {
            return true;
        }
        throw new IllegalArgumentException("retryPolicyRef '" + reference + "' is not a query retry policy; expected "
            + RETRY_NONE + " or " + RETRY_TRANSIENT);
    }
}
