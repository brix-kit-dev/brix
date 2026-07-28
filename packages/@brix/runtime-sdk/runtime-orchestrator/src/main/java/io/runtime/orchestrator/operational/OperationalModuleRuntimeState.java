/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.operational;

import java.util.Objects;

import io.runtime.sdk.plugin.BrixHealth;
import io.runtime.sdk.plugin.PluginLifecycleState;

/**
 * Immutable operational module lifecycle and readiness state.
 *
 * @param identity module identity
 * @param lifecycleState lifecycle state
 * @param entriesPublished whether the complete module entry set is published
 * @param ready derived readiness
 * @param health current health
 * @param diagnosticCode stable diagnostic code
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public record OperationalModuleRuntimeState(
        OperationalModuleIdentity identity,
        PluginLifecycleState lifecycleState,
        boolean entriesPublished,
        boolean ready,
        BrixHealth health,
        String diagnosticCode) {

    /**
     * Validates immutable state fields.
     */
    public OperationalModuleRuntimeState {
        Objects.requireNonNull(identity, "identity must not be null");
        Objects.requireNonNull(lifecycleState, "lifecycleState must not be null");
        Objects.requireNonNull(health, "health must not be null");
        diagnosticCode = diagnosticCode == null ? "" : diagnosticCode;
    }
}
