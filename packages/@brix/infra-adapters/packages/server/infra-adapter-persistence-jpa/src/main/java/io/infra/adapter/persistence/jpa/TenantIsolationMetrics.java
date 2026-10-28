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

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Counters of the adapter's tenant isolation (multi-tenant isolation design §4.6).
 *
 * <ul>
 *   <li>{@value #VIOLATIONS}: every access the adapter refused, labelled {@code owner},
 *       {@code scope}, {@code reason} and {@code code};</li>
 *   <li>{@value #POLICY_USES}: every owner transaction that ran under a registered cross-tenant
 *       policy or a platform operation, labelled {@code owner}, {@code policy} and
 *       {@code outcome};</li>
 *   <li>{@value #OWNER_FAILURES}: every owner transaction the database or the transaction manager
 *       failed, labelled {@code owner} and {@code code}. It is not an isolation counter, but it
 *       travels the same way.</li>
 * </ul>
 *
 * <p>Every label has a small fixed set of values. Tenant ids, policy keys, tables and SQL stay in
 * the security log (root {@code BRX-RS-OTEL-006}). The counts go to the {@link Recorder} that the
 * adapter's auto-configuration installs when the Host has a meter registry; until then they are
 * dropped. A failing recorder never changes the outcome of the access it counts.</p>
 */
public final class TenantIsolationMetrics {

    /** Counter of refused accesses. */
    public static final String VIOLATIONS = "brix.tenant.isolation.violations";

    /** Counter of owner transactions under a cross-tenant policy or a platform operation. */
    public static final String POLICY_USES = "brix.tenant.isolation.policy.uses";

    /**
     * Counter of policy uses whose platform audit record could not be written, so the use failed
     * (root BRX-RS-DATA-047), labelled {@code policy} and {@code reason}.
     */
    public static final String POLICY_AUDIT_FAILURES = "brix.tenant.isolation.policy.audit.failures";

    /**
     * Counter of owner transactions that failed in the database or the transaction manager, labelled
     * {@code owner} and the stable {@code persistence.*} code. Business failures the owner raises
     * itself are not counted. An alert on a single owner, such as the platform audit, starts here.
     */
    public static final String OWNER_FAILURES = "brix.persistence.owner.failures";

    private static final Logger log = LoggerFactory.getLogger(TenantIsolationMetrics.class);
    private static final Recorder NONE = new Recorder() {
        @Override
        public void violation(String owner, String scope, String reason, String code) {
        }

        @Override
        public void policyUse(String owner, String policy, String outcome) {
        }
    };
    private static final AtomicReference<Recorder> RECORDER = new AtomicReference<>(NONE);

    private TenantIsolationMetrics() {
    }

    /** Receives the counts. */
    public interface Recorder {

        /**
         * Counts one refused access.
         *
         * @param owner data owner, or {@code legacy} outside owner units of work
         * @param scope scope kind: {@code tenant}, {@code policy:<id>}, {@code platform-operation} or {@code none}
         * @param reason the check that refused it
         * @param code stable error code
         */
        void violation(String owner, String scope, String reason, String code);

        /**
         * Counts one owner transaction under a policy or a platform operation.
         *
         * @param owner data owner
         * @param policy policy id, or {@code platform-operation}
         * @param outcome {@code success} or {@code failure}
         */
        void policyUse(String owner, String policy, String outcome);

        /**
         * Counts one policy use that failed because its platform audit record was not written.
         *
         * @param policy policy id
         * @param reason {@code not_registered}, {@code integrity}, {@code timeout}, {@code unavailable} or {@code failure}
         */
        default void policyAuditFailure(String policy, String reason) {
        }

        /**
         * Counts one owner transaction that failed in the database or the transaction manager.
         *
         * @param owner data owner
         * @param code stable {@code persistence.*} error code
         */
        default void ownerFailure(String owner, String code) {
        }
    }

    /** Stops sending counts to an installed recorder. */
    @FunctionalInterface
    public interface Installation extends AutoCloseable {

        @Override
        void close();
    }

    /**
     * Sends the counts to {@code recorder} until the returned installation is closed or another
     * recorder is installed.
     *
     * @param recorder recorder to install
     * @return handle that uninstalls the recorder
     */
    public static Installation install(Recorder recorder) {
        Recorder installed = Objects.requireNonNull(recorder, "recorder must not be null");
        RECORDER.set(installed);
        return () -> RECORDER.compareAndSet(installed, NONE);
    }

    static void violation(String owner, String scope, String reason, String code) {
        try {
            RECORDER.get().violation(owner, scope, reason, code);
        } catch (RuntimeException failure) {
            log.debug("A tenant isolation violation was not counted", failure);
        }
    }

    static void policyUse(String owner, String policy, boolean succeeded) {
        try {
            RECORDER.get().policyUse(owner, policy, succeeded ? "success" : "failure");
        } catch (RuntimeException failure) {
            log.debug("A tenant policy use was not counted", failure);
        }
    }

    static void policyAuditFailure(String policy, String reason) {
        try {
            RECORDER.get().policyAuditFailure(policy, reason);
        } catch (RuntimeException failure) {
            log.debug("A policy audit failure was not counted", failure);
        }
    }

    static void ownerFailure(String owner, String code) {
        try {
            RECORDER.get().ownerFailure(owner, code);
        } catch (RuntimeException failure) {
            log.debug("An owner transaction failure was not counted", failure);
        }
    }
}
