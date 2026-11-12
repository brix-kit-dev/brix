/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.internalcontract;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import io.runtime.orchestrator.capability.DefaultCapabilityRegistry;
import io.runtime.orchestrator.endpoint.ContractWrites;
import io.runtime.orchestrator.operational.OperationalModuleDescriptor;
import io.runtime.orchestrator.operational.OperationalRuntimeException;
import io.runtime.orchestrator.persistence.OwnerBoundaryProxies;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.orchestrator.query.QuerySideEffects;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.capability.registry.CapabilityRegistry;
import io.runtime.sdk.internalcontract.InternalContractProvider;
import io.runtime.sdk.internalcontract.InternalContractProviderBootstrap;
import io.runtime.sdk.internalcontract.InternalContractProviderContext;
import io.runtime.sdk.internalcontract.InternalContractProviderFactory;
import io.runtime.sdk.internalcontract.RuntimeModuleIdentity;

/**
 * Validates provider bindings and installs them in the existing L2B Registry internal namespace.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class InternalContractBinder {

    private final DefaultCapabilityRegistry registry;
    private final CapabilityRegistry ownerCapabilities;
    private final ClassLoader classLoader;
    private final OwnerPersistenceProvider ownerPersistence;
    private final ContractWrites writes;
    private final Map<String, PendingBinding> pendingBindings = new LinkedHashMap<>();
    private boolean activated;

    /**
     * Creates an internal contract binder.
     *
     * @param registry existing L2B capability registry implementation
     * @param ownerCapabilities source of owner-scoped capabilities
     * @param classLoader contract type class loader
     */
    public InternalContractBinder(
            DefaultCapabilityRegistry registry,
            CapabilityRegistry ownerCapabilities,
            ClassLoader classLoader) {
        this(registry, ownerCapabilities, classLoader, OwnerPersistenceProvider.none());
    }

    /**
     * Creates an internal contract binder that runs owner contracts in the owner's unit of work.
     *
     * <p>When the providing data owner has a persistence unit, every registered contract instance
     * is wrapped so that each call suspends the caller's transaction and runs in the owner's own
     * local transaction (root blueprint BRX-RS-HOST-020 and BRX-RS-DATA-028).</p>
     *
     * @param registry existing L2B capability registry implementation
     * @param ownerCapabilities source of owner-scoped capabilities
     * @param classLoader contract type class loader
     * @param ownerPersistence source of owner-scoped persistence capabilities
     */
    public InternalContractBinder(
            DefaultCapabilityRegistry registry,
            CapabilityRegistry ownerCapabilities,
            ClassLoader classLoader,
            OwnerPersistenceProvider ownerPersistence) {
        this(registry, ownerCapabilities, classLoader, ownerPersistence, null);
    }

    /**
     * Creates an internal contract binder whose contracts keep the results of their replayable writes (idempotency design
     * §5.6, D79).
     *
     * @param registry existing L2B capability registry implementation
     * @param ownerCapabilities source of owner-scoped capabilities
     * @param classLoader contract type class loader
     * @param ownerPersistence source of owner-scoped persistence capabilities
     * @param writes keeps those results, or {@code null} on a host that has no replay
     */
    public InternalContractBinder(
            DefaultCapabilityRegistry registry,
            CapabilityRegistry ownerCapabilities,
            ClassLoader classLoader,
            OwnerPersistenceProvider ownerPersistence,
            ContractWrites writes) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.ownerCapabilities = Objects.requireNonNull(ownerCapabilities, "ownerCapabilities must not be null");
        this.classLoader = classLoader != null ? classLoader : InternalContractBinder.class.getClassLoader();
        this.ownerPersistence = Objects.requireNonNull(ownerPersistence, "ownerPersistence must not be null");
        this.writes = writes;
    }

    /**
     * Returns who keeps the results of a contract's writes for an operational endpoint's replay.
     *
     * @param contractId the contract
     * @return its owner, empty when the contract declares no replayable write
     */
    public Optional<String> replayableWritesOwner(String contractId) {
        PendingBinding pending = pendingBindings.get(contractId);
        return pending == null || pending.declaration.replayableWrites().isEmpty()
            ? Optional.empty()
            : Optional.of(pending.ownerIdentity.moduleId());
    }

    /**
     * Validates and binds every contract declared by one provider artifact.
     *
     * @param descriptor provider artifact descriptor
     * @param provider provider SPI instance
     * @param allowedOwnerCapabilities owner capability types declared by the descriptor
     */
    public void bind(
            OperationalModuleDescriptor descriptor,
            InternalContractProvider provider,
            Set<Class<?>> allowedOwnerCapabilities) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        if (activated) {
            throw new IllegalStateException("Internal contract namespace is frozen");
        }
        RecordingBootstrap bootstrap = new RecordingBootstrap();
        provider.configure(bootstrap);

        Map<String, InternalContractDeclaration> declarations = new LinkedHashMap<>();
        for (OperationalModuleDescriptor.ProvidedInternalContract declaration : descriptor.providedContracts()) {
            declarations.put(declaration.contractId(), new InternalContractDeclaration(
                declaration.contractId(),
                declaration.contractType(),
                declaration.contractVersion(),
                declaration.providerId(),
                declaration.owner(),
                Set.of()));
        }
        bindDeclarations(
            descriptor.identity().moduleId(),
            descriptor.identity().owner(),
            new RuntimeModuleIdentity(
                descriptor.identity().moduleId(),
                "platform-operational",
                descriptor.identity().moduleVersion()),
            declarations,
            bootstrap,
            allowedOwnerCapabilities);
    }

    /**
     * Validates and binds every internal contract declared by one plugin artifact.
     *
     * @param descriptor plugin runtime descriptor
     * @param provider provider SPI instance
     * @param allowedOwnerCapabilities owner capability types declared by the descriptor
     */
    public void bindPlugin(
            PluginRuntimeDescriptor descriptor,
            InternalContractProvider provider,
            Set<Class<?>> allowedOwnerCapabilities) {
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        Objects.requireNonNull(provider, "provider must not be null");
        if (activated) {
            throw new IllegalStateException("Internal contract namespace is frozen");
        }
        RecordingBootstrap bootstrap = new RecordingBootstrap();
        provider.configure(bootstrap);

        Map<String, InternalContractDeclaration> declarations = new LinkedHashMap<>();
        for (PluginRuntimeDescriptor.ProvidedInternalContract declaration
                : descriptor.providedInternalContracts().values()) {
            declarations.put(declaration.contractId(), new InternalContractDeclaration(
                declaration.contractId(),
                declaration.contractType(),
                declaration.contractVersion(),
                declaration.providerId(),
                declaration.owner(),
                declaration.replayableWrites()));
        }
        bindDeclarations(
            descriptor.identity().pluginId(),
            descriptor.identity().pluginId(),
            new RuntimeModuleIdentity(
                descriptor.identity().pluginId(),
                "plugin-server",
                descriptor.version()),
            declarations,
            bootstrap,
            allowedOwnerCapabilities);
    }

    /**
     * Activates the fully validated H3 provider set and freezes its isolated namespace.
     */
    public void activateAndFreeze() {
        if (activated) {
            throw new IllegalStateException("Internal contract bindings have already been activated");
        }
        activated = true;
        pendingBindings.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(Map.Entry::getValue)
            .forEach(pending -> register(
                pending.declaration,
                pending.binding,
                pending.context,
                pending.ownerIdentity));
        registry.freezeInternalContracts();
    }

    /**
     * Resolves and version-checks a required internal contract.
     *
     * @param requirement descriptor requirement
     * @param contractType requested Java type
     * @param <C> contract type
     * @return resolved contract
     */
    public <C> C require(
            OperationalModuleDescriptor.RequiredInternalContract requirement,
            Class<C> contractType) {
        DefaultCapabilityRegistry.InternalContractBinding<C> binding = registry
            .getInternalContract(requirement.contractId(), contractType)
            .orElseThrow(() -> new OperationalRuntimeException(
                "internal_contract.provider_missing",
                "Required internal contract provider is missing: " + requirement.contractId()));
        if (!binding.contractType().getName().equals(requirement.contractType())
                || !BrixRange.contains(binding.contractVersion(), requirement.versionRange())) {
            throw new OperationalRuntimeException(
                "internal_contract.version_mismatch",
                "Internal contract type or version mismatch: " + requirement.contractId());
        }
        return binding.instance();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void register(
            InternalContractDeclaration declaration,
            Binding binding,
            ProviderContext context,
            RuntimeModuleIdentity ownerIdentity) {
        Object instance;
        try {
            instance = binding.factory.create(context);
        } catch (RuntimeException e) {
            throw new OperationalRuntimeException(
                "internal_contract.factory_failed",
                "Internal contract factory failed: " + declaration.contractId(),
                e);
        }
        if (instance == null || !binding.contractType.isInstance(instance)) {
            throw new OperationalRuntimeException(
                "internal_contract.factory_type_mismatch",
                "Internal contract factory returned an invalid type: " + declaration.contractId());
        }
        Optional<PersistenceCapability> persistence = ownerPersistence.forOwner(ownerIdentity.moduleId());
        if (persistence.isPresent()) {
            instance = wrap(binding, instance, persistence.get(), ownerIdentity, declaration);
        } else if (!declaration.replayableWrites().isEmpty()) {
            throw new OperationalRuntimeException(
                "internal_contract.owner_persistence_missing",
                "Owner of replayable writes has no persistence unit: " + ownerIdentity.moduleId());
        }
        try {
            registry.registerInternalContract(
                declaration.contractId(),
                binding.contractType,
                declaration.contractVersion(),
                ownerIdentity,
                instance);
        } catch (IllegalStateException e) {
            throw new OperationalRuntimeException(
                "internal_contract.provider_duplicate",
                "Duplicate internal contract provider: " + declaration.contractId(),
                e);
        }
    }

    /** Root BRX-RS-HOST-020: each call runs in the owner's unit of work, and a replayable write keeps its result there. */
    @SuppressWarnings("unchecked")
    private <C> C wrap(Binding<?> binding, Object instance, PersistenceCapability persistence,
            RuntimeModuleIdentity ownerIdentity, InternalContractDeclaration declaration) {
        Class<C> type = (Class<C>) binding.contractType;
        if (declaration.replayableWrites().isEmpty() || writes == null) {
            return OwnerBoundaryProxies.wrap(type, (C) instance, persistence.unitOfWork());
        }
        return OwnerBoundaryProxies.wrap(type, (C) instance, persistence.unitOfWork(), ownerIdentity.moduleId(),
            declaration.replayableWrites(), writes);
    }

    /** Idempotency design D79: a replayable write names one method of the contract, so its result has one type. */
    private static void requireReplayableWrites(InternalContractDeclaration declaration, Class<?> contractType) {
        for (String write : declaration.replayableWrites()) {
            long methods = Arrays.stream(contractType.getMethods())
                .filter(method -> method.getName().equals(write))
                .count();
            if (methods != 1) {
                throw new OperationalRuntimeException(
                    "internal_contract.replayable_write_invalid",
                    "Internal contract " + declaration.contractId() + " declares the replayable write '" + write
                        + "', which is not exactly one of its methods");
            }
        }
    }

    private Class<?> loadType(String typeName) {
        try {
            return Class.forName(typeName, false, classLoader);
        } catch (ClassNotFoundException e) {
            throw new OperationalRuntimeException(
                "internal_contract.type_missing",
                "Internal contract type is unavailable: " + typeName,
                e);
        }
    }

    private void bindDeclarations(
            String artifactId,
            String artifactOwner,
            RuntimeModuleIdentity ownerIdentity,
            Map<String, InternalContractDeclaration> declarations,
            RecordingBootstrap bootstrap,
            Set<Class<?>> allowedOwnerCapabilities) {
        if (!bootstrap.factories.keySet().equals(declarations.keySet())) {
            throw new OperationalRuntimeException(
                "internal_contract.binding_mismatch",
                "Internal contract descriptor and provider bindings differ for " + artifactId);
        }

        ProviderContext context = new ProviderContext(
            ownerIdentity,
            ownerCapabilities,
            ownerPersistence,
            allowedOwnerCapabilities == null ? Set.of() : Set.copyOf(allowedOwnerCapabilities));
        for (Map.Entry<String, InternalContractDeclaration> entry : declarations.entrySet()) {
            InternalContractDeclaration declaration = entry.getValue();
            if (!declaration.owner().equals(artifactOwner)) {
                throw new OperationalRuntimeException(
                    "internal_contract.owner_mismatch",
                    "Internal contract owner does not match its descriptor artifact: "
                        + declaration.contractId());
            }
            Binding<?> binding = bootstrap.factories.get(entry.getKey());
            Class<?> declaredType = loadType(declaration.contractType());
            if (!binding.contractType.equals(declaredType)) {
                throw new OperationalRuntimeException(
                    "internal_contract.type_mismatch",
                    "Internal contract type mismatch for " + declaration.contractId());
            }
            requireReplayableWrites(declaration, declaredType);
            PendingBinding pending = new PendingBinding(declaration, binding, context, ownerIdentity);
            if (pendingBindings.putIfAbsent(declaration.contractId(), pending) != null) {
                throw new OperationalRuntimeException(
                    "internal_contract.provider_duplicate",
                    "Duplicate internal contract provider: " + declaration.contractId());
            }
        }
    }

    private static final class RecordingBootstrap implements InternalContractProviderBootstrap {
        private final Map<String, Binding<?>> factories = new LinkedHashMap<>();

        @Override
        public <C> void bind(
                String contractId,
                Class<C> contractType,
                InternalContractProviderFactory<C> factory) {
            if (contractId == null || contractId.isBlank()) {
                throw new OperationalRuntimeException(
                    "internal_contract.binding_invalid",
                    "Internal contract binding id must not be blank");
            }
            Binding<C> binding = new Binding<>(
                Objects.requireNonNull(contractType, "contractType must not be null"),
                Objects.requireNonNull(factory, "factory must not be null"));
            if (factories.putIfAbsent(contractId, binding) != null) {
                throw new OperationalRuntimeException(
                    "internal_contract.binding_duplicate",
                    "Duplicate internal contract binding: " + contractId);
            }
        }
    }

    private static final class ProviderContext implements InternalContractProviderContext {
        private final RuntimeModuleIdentity ownerIdentity;
        private final CapabilityRegistry ownerCapabilities;
        private final OwnerPersistenceProvider ownerPersistence;
        private final Set<Class<?>> allowedTypes;

        private ProviderContext(
                RuntimeModuleIdentity ownerIdentity,
                CapabilityRegistry ownerCapabilities,
                OwnerPersistenceProvider ownerPersistence,
                Set<Class<?>> allowedTypes) {
            this.ownerIdentity = ownerIdentity;
            this.ownerCapabilities = ownerCapabilities;
            this.ownerPersistence = ownerPersistence;
            this.allowedTypes = allowedTypes;
        }

        @Override
        public RuntimeModuleIdentity ownerIdentity() {
            return ownerIdentity;
        }

        @Override
        public <C> C requireOwnerCapability(Class<C> capabilityType) {
            if (!allowedTypes.contains(capabilityType)) {
                throw new OperationalRuntimeException(
                    "internal_contract.owner_capability_undeclared",
                    "Owner capability is undeclared: " + capabilityType.getName());
            }
            if (capabilityType == PersistenceCapability.class) {
                return capabilityType.cast(ownerPersistence.forOwner(ownerIdentity.moduleId())
                    .orElseThrow(() -> new OperationalRuntimeException(
                        "internal_contract.owner_persistence_missing",
                        "Owner has no persistence unit: " + ownerIdentity.moduleId())));
            }
            if (capabilityType == EventBusCapability.class) {
                return capabilityType.cast(ownerEventBus());
            }
            return ownerCapabilities.getRequired(capabilityType);
        }

        /**
         * The owner's own event bus, the one its {@code PluginContext} gives its handlers (standard-link plan D125;
         * root BRX-RS-HOST-022, DATA-008, MESSAGE-022): CRITICAL and STANDARD events go to the owner's
         * {@code data.outbox} in the contract call's unit of work, so they commit or roll back with its writes. Never
         * the Host's direct bus, which takes no part in that transaction.
         */
        private EventBusCapability ownerEventBus() {
            return QuerySideEffects.guard(EventBusCapability.class, ownerPersistence.eventBusFor(ownerIdentity.moduleId())
                .orElseThrow(() -> new OperationalRuntimeException(
                    "internal_contract.owner_event_bus_missing",
                    "Owner has no event bus of its own (data.outbox and EventBusCapability on a host with owner "
                        + "persistence): " + ownerIdentity.moduleId())));
        }
    }

    private record Binding<C>(Class<C> contractType, InternalContractProviderFactory<C> factory) {
    }

    private record InternalContractDeclaration(
            String contractId,
            String contractType,
            String contractVersion,
            String providerId,
            String owner,
            Set<String> replayableWrites) {
    }

    private record PendingBinding(
            InternalContractDeclaration declaration,
            Binding<?> binding,
            ProviderContext context,
            RuntimeModuleIdentity ownerIdentity) {
    }
}
