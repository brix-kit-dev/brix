/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.operational;

import java.util.Objects;

import io.runtime.orchestrator.internalcontract.InternalContractBinder;

/**
 * Allowlist-enforcing operational context.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
final class DefaultOperationalContext implements OperationalContext {

    private final OperationalModuleDescriptor descriptor;
    private final RuntimeOperationalView runtimeView;
    private final InternalContractBinder internalContracts;

    DefaultOperationalContext(
            OperationalModuleDescriptor descriptor,
            RuntimeOperationalView runtimeView,
            InternalContractBinder internalContracts) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor must not be null");
        this.runtimeView = Objects.requireNonNull(runtimeView, "runtimeView must not be null");
        this.internalContracts = Objects.requireNonNull(internalContracts, "internalContracts must not be null");
    }

    @Override
    public OperationalModuleIdentity moduleIdentity() {
        return descriptor.identity();
    }

    @Override
    public RuntimeOperationalView runtimeView() {
        return runtimeView;
    }

    @Override
    public <C> C requireInternalContract(Class<C> contractType) {
        Objects.requireNonNull(contractType, "contractType must not be null");
        OperationalModuleDescriptor.RequiredInternalContract requirement = descriptor.requiredContracts().stream()
            .filter(candidate -> candidate.contractType().equals(contractType.getName()))
            .findFirst()
            .orElseThrow(() -> new OperationalRuntimeException(
                "internal_contract.undeclared",
                "Operational module '" + descriptor.identity().moduleId()
                    + "' requested an undeclared internal contract type"));
        if (!descriptor.privilegeAllowlist().contains(requirement.privilegeAllowlistRef())) {
            throw new OperationalRuntimeException(
                "internal_contract.privilege_denied",
                "Operational module privilege is not allowlisted");
        }
        return internalContracts.require(requirement, contractType);
    }
}
