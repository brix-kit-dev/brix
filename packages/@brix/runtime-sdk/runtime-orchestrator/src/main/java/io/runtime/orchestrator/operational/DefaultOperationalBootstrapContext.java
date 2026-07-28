/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.operational;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.ManagedTask;

/**
 * Descriptor-validating operational factory binding context.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
final class DefaultOperationalBootstrapContext implements OperationalBootstrapContext {

    private final OperationalModuleDescriptor descriptor;
    private final Map<String, OperationalHandlerFactory<? extends EndpointHandler<?, ?>>> endpointFactories =
        new LinkedHashMap<>();
    private final Map<String, OperationalHandlerFactory<? extends ManagedTask>> taskFactories =
        new LinkedHashMap<>();

    DefaultOperationalBootstrapContext(OperationalModuleDescriptor descriptor) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor must not be null");
    }

    @Override
    public void bindEndpointHandlerFactory(
            String handlerId,
            OperationalHandlerFactory<? extends EndpointHandler<?, ?>> factory) {
        if (!descriptor.endpointsByHandlerId().containsKey(handlerId)) {
            throw failure("operational.factory.undeclared", "Undeclared endpoint handler factory: " + handlerId);
        }
        putUnique(endpointFactories, handlerId, factory);
    }

    @Override
    public void bindTaskFactory(
            String handlerId,
            OperationalHandlerFactory<? extends ManagedTask> factory) {
        if (!descriptor.tasksByHandlerId().containsKey(handlerId)) {
            throw failure("operational.factory.undeclared", "Undeclared task factory: " + handlerId);
        }
        putUnique(taskFactories, handlerId, factory);
    }

    void validateBidirectionalConsistency() {
        if (!endpointFactories.keySet().equals(descriptor.endpointsByHandlerId().keySet())
                || !taskFactories.keySet().equals(descriptor.tasksByHandlerId().keySet())) {
            throw failure(
                "operational.factory.binding_mismatch",
                "Operational descriptor and code factory bindings differ");
        }
    }

    Map<String, OperationalHandlerFactory<? extends EndpointHandler<?, ?>>> endpointFactories() {
        return Map.copyOf(endpointFactories);
    }

    Map<String, OperationalHandlerFactory<? extends ManagedTask>> taskFactories() {
        return Map.copyOf(taskFactories);
    }

    private <T> void putUnique(Map<String, T> target, String id, T value) {
        if (id == null || id.isBlank()) {
            throw failure("operational.factory.invalid", "Handler id must not be blank");
        }
        Objects.requireNonNull(value, "factory must not be null");
        if (target.putIfAbsent(id, value) != null) {
            throw failure("operational.factory.duplicate", "Duplicate operational factory: " + id);
        }
    }

    private OperationalRuntimeException failure(String code, String message) {
        return new OperationalRuntimeException(
            code,
            "Operational module '" + descriptor.identity().moduleId() + "': " + message);
    }
}
