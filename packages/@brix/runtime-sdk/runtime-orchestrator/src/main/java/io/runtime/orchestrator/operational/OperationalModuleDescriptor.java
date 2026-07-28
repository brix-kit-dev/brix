/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.operational;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Validated Runtime representation of {@code platform-operational.yaml}.
 *
 * @param identity module identity
 * @param runtimeRange supported L2B Runtime range
 * @param providedContracts internal contracts provided by this artifact
 * @param requiredContracts internal contracts consumed by this module
 * @param privilegeAllowlist privilege references approved for this module
 * @param endpoints descriptor endpoint declarations
 * @param tasks descriptor task declarations
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public record OperationalModuleDescriptor(
        OperationalModuleIdentity identity,
        String runtimeRange,
        List<ProvidedInternalContract> providedContracts,
        List<RequiredInternalContract> requiredContracts,
        Set<String> privilegeAllowlist,
        Map<String, EndpointDeclaration> endpoints,
        Map<String, TaskDeclaration> tasks) {

    /**
     * Defensively copies descriptor collections.
     */
    public OperationalModuleDescriptor {
        identity = Objects.requireNonNull(identity, "identity must not be null");
        runtimeRange = requireText(runtimeRange, "runtimeRange");
        providedContracts = List.copyOf(providedContracts == null ? List.of() : providedContracts);
        requiredContracts = List.copyOf(requiredContracts == null ? List.of() : requiredContracts);
        privilegeAllowlist = Set.copyOf(privilegeAllowlist == null ? Set.of() : privilegeAllowlist);
        endpoints = Map.copyOf(endpoints == null ? Map.of() : endpoints);
        tasks = Map.copyOf(tasks == null ? Map.of() : tasks);
        requireUnique(providedContracts, ProvidedInternalContract::contractId, "provided contract");
        requireUnique(requiredContracts, RequiredInternalContract::contractId, "required contract");
        requireUnique(endpoints.values(), EndpointDeclaration::handlerId, "endpoint handler");
        requireUnique(tasks.values(), TaskDeclaration::handlerId, "task handler");
        for (RequiredInternalContract requirement : requiredContracts) {
            if (!privilegeAllowlist.contains(requirement.privilegeAllowlistRef())) {
                throw new IllegalArgumentException("Required contract '" + requirement.contractId()
                    + "' references an unapproved privilege '" + requirement.privilegeAllowlistRef() + "'");
            }
        }
    }

    private static <T> void requireUnique(Iterable<T> values, Function<T, String> key, String kind) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (T value : values) {
            if (!seen.add(key.apply(value))) {
                throw new IllegalArgumentException("Duplicate " + kind + ": " + key.apply(value));
            }
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    /**
     * Returns endpoint declarations keyed by handler identifier.
     *
     * @return immutable handler map
     */
    public Map<String, EndpointDeclaration> endpointsByHandlerId() {
        return endpoints.values().stream().collect(Collectors.toUnmodifiableMap(
            EndpointDeclaration::handlerId,
            Function.identity()));
    }

    /**
     * Returns task declarations keyed by handler identifier.
     *
     * @return immutable handler map
     */
    public Map<String, TaskDeclaration> tasksByHandlerId() {
        return tasks.values().stream().collect(Collectors.toUnmodifiableMap(
            TaskDeclaration::handlerId,
            Function.identity()));
    }

    /**
     * Descriptor declaration for a provided internal contract.
     *
     * @param contractId stable contract identifier
     * @param contractType contract Java type name
     * @param contractVersion exact contract version
     * @param providerId stable provider identifier
     * @param owner data owner identifier
     */
    public record ProvidedInternalContract(
            String contractId,
            String contractType,
            String contractVersion,
            String providerId,
            String owner) {

        /**
         * Validates declaration fields.
         */
        public ProvidedInternalContract {
            contractId = requireText(contractId, "contractId");
            contractType = requireText(contractType, "contractType");
            contractVersion = requireText(contractVersion, "contractVersion");
            providerId = requireText(providerId, "providerId");
            owner = requireText(owner, "owner");
        }
    }

    /**
     * Descriptor declaration for a consumed internal contract.
     *
     * @param contractId stable contract identifier
     * @param contractType contract Java type name
     * @param versionRange Brix Range v1 requirement
     * @param required whether absence blocks startup
     * @param privilegeAllowlistRef approved privilege reference
     */
    public record RequiredInternalContract(
            String contractId,
            String contractType,
            String versionRange,
            boolean required,
            String privilegeAllowlistRef) {

        /**
         * Validates declaration fields.
         */
        public RequiredInternalContract {
            contractId = requireText(contractId, "contractId");
            contractType = requireText(contractType, "contractType");
            versionRange = requireText(versionRange, "versionRange");
            privilegeAllowlistRef = requireText(privilegeAllowlistRef, "privilegeAllowlistRef");
        }
    }

    /**
     * Descriptor declaration for an operational HTTP endpoint.
     *
     * @param endpointId stable endpoint identifier
     * @param method HTTP method
     * @param path normalized absolute path
     * @param handlerId code factory identifier
     * @param accessPolicy access policy reference
     */
    public record EndpointDeclaration(
            String endpointId,
            String method,
            String path,
            String handlerId,
            String accessPolicy) {

        /**
         * Validates declaration fields.
         */
        public EndpointDeclaration {
            endpointId = requireText(endpointId, "endpointId");
            method = requireText(method, "method").toUpperCase(java.util.Locale.ROOT);
            path = requireText(path, "path");
            handlerId = requireText(handlerId, "handlerId");
            accessPolicy = requireText(accessPolicy, "accessPolicy");
            if (!path.startsWith("/api/platform/")) {
                throw new IllegalArgumentException("Operational endpoint must use /api/platform/**: " + path);
            }
        }
    }

    /**
     * Descriptor declaration for an operational managed task.
     *
     * @param taskId stable task identifier
     * @param handlerId code factory identifier
     */
    public record TaskDeclaration(String taskId, String handlerId) {

        /**
         * Validates declaration fields.
         */
        public TaskDeclaration {
            taskId = requireText(taskId, "taskId");
            handlerId = requireText(handlerId, "handlerId");
        }
    }
}
