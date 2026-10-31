/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.task;

import java.util.Objects;

import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.sdk.plugin.ManagedTask;

/**
 * A declared task of a started module, with the task its factory made.
 *
 * @param moduleId the plugin or platform operational module
 * @param declaration the task's declaration
 * @param task the task
 */
public record TaskProvider(String moduleId, PluginRuntimeDescriptor.ProvidedTask declaration, ManagedTask task) {

    /**
     * Checks the provider.
     */
    public TaskProvider {
        Objects.requireNonNull(moduleId, "moduleId must not be null");
        Objects.requireNonNull(declaration, "declaration must not be null");
        Objects.requireNonNull(task, "task must not be null");
    }

    /**
     * Returns the key the scheduler knows the task by.
     *
     * @return {@code moduleId/handlerId}
     */
    public String key() {
        return moduleId + "/" + declaration.handlerId();
    }
}
