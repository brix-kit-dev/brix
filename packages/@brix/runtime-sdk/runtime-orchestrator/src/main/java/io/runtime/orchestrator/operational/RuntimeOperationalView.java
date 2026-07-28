/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.operational;

import java.util.Set;

/**
 * Read-only, non-enumerating operational view of Host Runtime state.
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public interface RuntimeOperationalView {

    /**
     * Returns the immutable Host entry generation.
     *
     * @return entry generation
     */
    long entryGeneration();

    /**
     * Returns whether the Host is currently ready.
     *
     * @return readiness
     */
    boolean ready();

    /**
     * Returns required module identifiers selected by Composition.
     *
     * @return immutable required identifiers
     */
    Set<String> requiredModuleIds();
}
