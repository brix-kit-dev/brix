/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.operational;

/**
 * Minimal module-scoped context for platform operational code.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public interface OperationalContext {

    /**
     * Returns the immutable module identity.
     *
     * @return module identity
     */
    OperationalModuleIdentity moduleIdentity();

    /**
     * Returns the read-only Host Runtime view.
     *
     * @return Runtime view
     */
    RuntimeOperationalView runtimeView();

    /**
     * Returns a descriptor-declared and allowlisted internal contract.
     *
     * @param contractType contract Java type
     * @param <C> contract type
     * @return contract proxy or implementation
     * @throws OperationalRuntimeException when access is undeclared, denied, or unavailable
     */
    <C> C requireInternalContract(Class<C> contractType);
}
