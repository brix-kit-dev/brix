/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.task;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The tasks of the host's started modules (standard-link plan L6.3, L6.6): the plugins' and the platform operational
 * modules'.
 */
public interface TaskProviders {

    /**
     * Whether the host's entries are published, so tasks may run (root BRX-RS-BOOT-007).
     *
     * @return readiness
     */
    boolean ready();

    /**
     * Returns the tasks of the modules that are started now.
     *
     * @return the tasks
     */
    List<TaskProvider> tasks();

    /**
     * Returns providers that are ready when all of them are, with all their tasks.
     *
     * @param providers the providers
     * @return the combined providers
     */
    static TaskProviders of(List<TaskProviders> providers) {
        List<TaskProviders> all = List.copyOf(Objects.requireNonNull(providers, "providers must not be null"));
        return new TaskProviders() {
            @Override
            public boolean ready() {
                return all.stream().allMatch(TaskProviders::ready);
            }

            @Override
            public List<TaskProvider> tasks() {
                List<TaskProvider> tasks = new ArrayList<>();
                all.forEach(provider -> tasks.addAll(provider.tasks()));
                return List.copyOf(tasks);
            }
        };
    }
}
