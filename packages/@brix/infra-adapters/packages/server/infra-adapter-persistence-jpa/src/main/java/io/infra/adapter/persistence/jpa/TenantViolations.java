/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.infra.adapter.persistence.jpa;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.sdk.capability.PersistenceCapabilityException;

/**
 * Reports each tenant isolation violation once, where the adapter detects it: one structured
 * security log line and one {@value TenantIsolationMetrics#VIOLATIONS} count (multi-tenant
 * isolation design §4.6). The table and the SQL fingerprint go to the log only.
 */
final class TenantViolations {

    private static final Logger security = LoggerFactory.getLogger("brix.tenant.isolation.audit");

    private TenantViolations() {
    }

    /** The check that refused an access: the {@code reason} label. */
    enum Reason {

        /** The statement verifier refused a statement of an owner unit of work. */
        STATEMENT("statement"),

        /** The write guard refused an entity insert, update or delete. */
        ENTITY_WRITE("entity-write"),

        /** A policy or a platform operation reached more tenants than it may bind. */
        TENANT_BINDING("tenant-binding"),

        /** A statement outside owner units of work lacked its tenant condition. */
        LEGACY_STATEMENT("legacy-statement");

        private final String label;

        Reason(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    /**
     * Logs and counts a violation.
     *
     * @param violation the error the access fails with
     * @param reason the check that refused it
     * @param detail table, operation or SQL fingerprint for the log, without parameter values
     * @return {@code violation}, for the caller to throw
     */
    static PersistenceCapabilityException report(
            PersistenceCapabilityException violation,
            Reason reason,
            String detail) {
        Map<String, String> parameters = violation.safeParameters();
        String owner = parameters.getOrDefault("owner", "unknown");
        String scope = parameters.getOrDefault("scope", "none");
        String code = violation.code().wireCode();
        security.warn("event=tenant.isolation.violation reason={} owner={} scope={} code={} {}",
            reason.label(), owner, scope, code, detail);
        TenantIsolationMetrics.violation(owner, scope, reason.label(), code);
        return violation;
    }
}
