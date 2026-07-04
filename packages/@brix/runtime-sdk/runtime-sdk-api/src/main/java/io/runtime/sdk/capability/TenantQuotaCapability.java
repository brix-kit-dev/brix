/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.runtime.sdk.capability;

import java.time.OffsetDateTime;

import io.runtime.sdk.annotation.Since;

/**
 * Tenant quota capability contract.
 *
 * <p>This capability exposes deployment-instance tenant quota and license
 * admission semantics without exposing the persistence model. Implementations
 * must enforce fail-closed tenant creation and restoration decisions with an
 * installation-level lock.</p>
 *
 * @since 3.2.3
 */
@Since("3.2.3")
public interface TenantQuotaCapability {

    /**
     * Returns the current installation quota and license admission snapshot.
     *
     * @return installation quota snapshot
     */
    InstallationQuotaSnapshot getInstallationQuota();

    /**
     * Requires that a tenant can be admitted under the current license/quota.
     *
     * @throws QuotaAdmissionException when the installation cannot admit another tenant
     */
    default void requireCanCreateTenant() {
        InstallationQuotaSnapshot snapshot = getInstallationQuota();
        if (!snapshot.canCreateTenant()) {
            throw new QuotaAdmissionException(snapshot.refusalReason());
        }
    }

    /**
     * Read-only quota and license admission view.
     *
     * @param installationId deployment installation ID
     * @param quota maximum active/trial tenants allowed
     * @param used current active/trial tenants counted by the quota row
     * @param licenseStatus current license state summary
     * @param expiresAt license expiry timestamp, or {@code null} for non-expiring open-core mode
     * @param canCreateTenant whether a new active/trial tenant can be admitted
     * @param refusalReason stable reason code when creation is refused, otherwise {@code null}
     * @param updatedAt last quota row update timestamp
     */
    record InstallationQuotaSnapshot(
            String installationId,
            int quota,
            int used,
            String licenseStatus,
            OffsetDateTime expiresAt,
            boolean canCreateTenant,
            String refusalReason,
            OffsetDateTime updatedAt
    ) {}

    /**
     * Exception thrown when quota/license admission is denied.
     */
    final class QuotaAdmissionException extends RuntimeException {
        private final String reason;

        public QuotaAdmissionException(String reason) {
            super(reason == null ? "TENANT_QUOTA_DENIED" : reason);
            this.reason = reason == null ? "TENANT_QUOTA_DENIED" : reason;
        }

        /**
         * Returns the stable refusal reason code.
         *
         * @return refusal reason
         */
        public String reason() {
            return reason;
        }
    }
}