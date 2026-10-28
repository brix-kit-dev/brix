/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.persistence;

import java.util.Map;

import io.runtime.sdk.error.PolicyAuditException;

/**
 * Port of the runtime policy audit writer (root BRX-RS-DATA-047): the audit writer DATA-006 names,
 * registered by the platform audit owner as the custodian {@value #CUSTODIAN} of its table.
 *
 * <p>L2B defines the port and L2C implements it. It is not an L2A capability, so plugins never see
 * it, and it records only the uses of cross-tenant policies multi-tenant §6.4 registers.</p>
 */
public interface PolicyAuditWriter {

    /** Custodian id the platform audit owner registers in {@code data.custodialWrites}. */
    String CUSTODIAN = "runtime.tenant-policy-audit";

    /**
     * Returns whether the owner's custodial registration covers the action.
     *
     * @param action platform audit action id
     * @return {@code true} when {@link #record} may write it
     */
    boolean covers(String action);

    /**
     * Commits one record in a local transaction of its own, suspending the caller's.
     *
     * @param record the policy use
     * @throws PolicyAuditException when the record was not stored; the caller's use of the policy
     *     must then fail
     */
    void record(PolicyUseRecord record);

    /**
     * Writes a record or fails the use: a missing writer or registration is a write failure too.
     *
     * @param writer the writer, or {@code null} when the host has none
     * @param record the policy use
     */
    static void require(PolicyAuditWriter writer, PolicyUseRecord record) {
        if (writer == null || !writer.covers(record.action())) {
            throw new PolicyAuditException(Map.of("policy", record.policy().id(), "reason", "not_registered"));
        }
        writer.record(record);
    }
}
