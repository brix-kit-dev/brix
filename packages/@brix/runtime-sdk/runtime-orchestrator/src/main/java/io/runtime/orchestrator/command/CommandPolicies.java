/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.command;

import java.util.Objects;
import java.util.Set;

import io.runtime.orchestrator.event.EventPolicies;
import io.runtime.orchestrator.idempotency.IdempotencyScopes;

/**
 * The platform's policies for commands (root BRX-RS-MANIFEST-025, MANIFEST-026; standard-link plan L4.3).
 *
 * <p>A provided command names one of each; its sender names none, the command is delivered under its provider's (D102).
 * Unknown names stop the plugin at startup. Commands have names of their own, like queries and tasks, and the event
 * names are no aliases (D101).</p>
 * <ul>
 *   <li>scope {@value #SCOPE_ENVELOPE}: the handler runs in the scope the command carries, its tenant or the
 *   platform;</li>
 *   <li>access {@value #ACCESS_DECLARED_CONSUMERS}: any plugin declaring the command under {@code commands.consumes}
 *   may send it; {@value #ACCESS_ON_BEHALF_OF}{@code <permission>}: only while acting for a user who holds the
 *   permission, which the sender's runtime checks when the command is submitted and the command records;</li>
 *   <li>timeout {@value #TIMEOUT_STANDARD}: the host's delivery deadline;</li>
 *   <li>retry {@value #RETRY_TRANSIENT}: a failed delivery is tried again, up to the host's attempts and after a
 *   backoff, and then parked;</li>
 *   <li>concurrency {@value EventPolicies#PARTITION_SERIAL}: one delivery at a time per partition key, in order, the
 *   name events use for the same guarantee;</li>
 *   <li>idempotency {@value #IDEMPOTENCY_KEY}: the owner's inbox drops a repeated delivery, and its business key table
 *   a repeated key of the same sender and tenant.</li>
 * </ul>
 *
 * <p>A command travels the same outbox, relay and inbox as an event, so the timeout and the retry take the host's
 * settings that events take too; their own names let commands get values of their own without a change to any
 * manifest.</p>
 */
public final class CommandPolicies {

    /** The handler runs in the command's own scope. */
    public static final String SCOPE_ENVELOPE = "command-envelope";

    /** Any plugin that declares the command may send it. */
    public static final String ACCESS_DECLARED_CONSUMERS = "declared-consumers";

    /** Prefix of the policy that admits a command only on behalf of a user holding the permission after it. */
    public static final String ACCESS_ON_BEHALF_OF = "on-behalf-of:";

    /** The host's delivery deadline. */
    public static final String TIMEOUT_STANDARD = "command-standard";

    /** A failed delivery is tried again, up to the host's attempts and after a backoff, and then parked. */
    public static final String RETRY_TRANSIENT = "command-transient-retry";

    /** The inbox drops a repeated delivery, and the owner's business key table a repeated key. */
    public static final String IDEMPOTENCY_KEY = "command-key";

    /** The scope policies. */
    public static final Set<String> SCOPE = Set.of(SCOPE_ENVELOPE);

    /** The timeout policies. */
    public static final Set<String> TIMEOUT = Set.of(TIMEOUT_STANDARD);

    /** The retry policies. */
    public static final Set<String> RETRY = Set.of(RETRY_TRANSIENT);

    /** The concurrency policies. */
    public static final Set<String> CONCURRENCY = Set.of(EventPolicies.PARTITION_SERIAL);

    /** The idempotency policies. */
    public static final Set<String> IDEMPOTENCY = Set.of(IDEMPOTENCY_KEY);

    private CommandPolicies() {
    }

    /**
     * An access policy.
     *
     * @param kind who may send the command
     * @param permission the permission the user must hold, for {@link Kind#ON_BEHALF_OF}; empty otherwise
     */
    public record Access(Kind kind, String permission) {

        /** Who may send a command. */
        public enum Kind {
            /** Any plugin that declares it. */
            DECLARED_CONSUMERS,
            /** A plugin acting for a user holding the permission. */
            ON_BEHALF_OF
        }

        /**
         * Checks the policy.
         */
        public Access {
            Objects.requireNonNull(kind, "kind must not be null");
            permission = permission == null ? "" : permission;
            if (kind == Kind.ON_BEHALF_OF && permission.isBlank()) {
                throw new IllegalArgumentException("on-behalf-of names a permission");
            }
        }
    }

    /**
     * Resolves an access policy reference.
     *
     * @param reference the manifest's {@code accessPolicyRef}
     * @return the policy
     * @throws IllegalArgumentException for an unknown reference
     */
    public static Access access(String reference) {
        if (ACCESS_DECLARED_CONSUMERS.equals(reference)) {
            return new Access(Access.Kind.DECLARED_CONSUMERS, "");
        }
        if (reference != null && reference.startsWith(ACCESS_ON_BEHALF_OF)
                && reference.length() > ACCESS_ON_BEHALF_OF.length()) {
            return new Access(Access.Kind.ON_BEHALF_OF, reference.substring(ACCESS_ON_BEHALF_OF.length()));
        }
        throw new IllegalArgumentException("unknown command access policy '" + reference + "': expected "
            + ACCESS_DECLARED_CONSUMERS + " or " + ACCESS_ON_BEHALF_OF + "<permission>");
    }

    /**
     * Returns whether {@code reference} names a known timeout, retry or concurrency policy of the given set.
     *
     * @param known the known names
     * @param reference the reference
     * @return {@code true} when it is known
     */
    static boolean known(Set<String> known, String reference) {
        return reference != null && known.contains(reference);
    }

    /**
     * Returns the scope a business key is unique within at the owner: the sender, the tenant and the scope the sender
     * named. Keys of different senders or tenants never meet (standard-link plan D54). A combination longer than the
     * column is replaced by its SHA-256, which stays unique.
     *
     * @param producerPluginId the sending plugin, as the runtime recorded it
     * @param tenantId the command's tenant, or {@code null} for a platform command
     * @param senderScope the scope the sender named
     * @return the scope to deduplicate by
     */
    public static String ownerScope(String producerPluginId, Long tenantId, String senderScope) {
        return IdempotencyScopes.bounded(
            producerPluginId + "|" + (tenantId == null ? "platform" : tenantId) + "|" + senderScope);
    }
}
