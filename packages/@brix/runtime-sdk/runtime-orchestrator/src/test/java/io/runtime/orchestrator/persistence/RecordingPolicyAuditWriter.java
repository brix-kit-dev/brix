/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Policy audit writer that keeps its records, can fail on demand, and notes whether each record
 * was written inside an owner's unit of work.
 */
final class RecordingPolicyAuditWriter implements PolicyAuditWriter {

    private final List<PolicyUseRecord> records = new ArrayList<>();
    private final List<Boolean> insideUnitOfWork = new ArrayList<>();
    private final Set<String> covered;
    private BooleanSupplier unitOfWork = () -> false;
    private RuntimeException failure;

    RecordingPolicyAuditWriter() {
        this(Set.of("LOGIN_TENANT_DISCOVERY", "FIRST_OWNER_INVITEE_SETUP_FANOUT"));
    }

    RecordingPolicyAuditWriter(Set<String> covered) {
        this.covered = Set.copyOf(covered);
    }

    @Override
    public boolean covers(String action) {
        return covered.contains(action);
    }

    @Override
    public void record(PolicyUseRecord record) {
        insideUnitOfWork.add(unitOfWork.getAsBoolean());
        if (failure != null) {
            throw failure;
        }
        records.add(record);
    }

    RecordingPolicyAuditWriter failingWith(RuntimeException failure) {
        this.failure = failure;
        return this;
    }

    RecordingPolicyAuditWriter watching(BooleanSupplier unitOfWork) {
        this.unitOfWork = unitOfWork;
        return this;
    }

    List<PolicyUseRecord> records() {
        return List.copyOf(records);
    }

    List<Boolean> insideUnitOfWork() {
        return List.copyOf(insideUnitOfWork);
    }
}
