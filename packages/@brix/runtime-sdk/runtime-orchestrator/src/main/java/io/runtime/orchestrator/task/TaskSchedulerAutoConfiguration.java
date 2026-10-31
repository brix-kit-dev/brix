/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.task;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import io.runtime.orchestrator.bootstrap.RuntimeShellBootstrapAutoConfiguration;
import io.runtime.orchestrator.operational.OperationalModuleRuntimeManager;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.plugin.PluginRuntimeManager;

/**
 * The runtime's task scheduler (standard-link plan L6.3): the plugins' and the platform operational modules' tasks,
 * claimed in the owners' task-run tables when owner persistence is assembled. {@code brix.tasks.enabled=false} turns
 * it off.
 */
@AutoConfiguration(after = RuntimeShellBootstrapAutoConfiguration.class)
@EnableConfigurationProperties(TaskProperties.class)
public class TaskSchedulerAutoConfiguration {

    /**
     * Creates the scheduler.
     *
     * @param properties {@code brix.tasks.*}
     * @param plugins the plugin runtime, when the host runs plugins
     * @param operational the platform operational modules, when the host runs them
     * @param owners the owners' persistence
     * @param claims the owners' task-run tables
     * @param registry the meter registry, when Micrometer is assembled
     * @return the scheduler
     */
    @Bean
    @ConditionalOnMissingBean
    public RuntimeTaskScheduler runtimeTaskScheduler(
            TaskProperties properties,
            ObjectProvider<PluginRuntimeManager> plugins,
            ObjectProvider<OperationalModuleRuntimeManager> operational,
            ObjectProvider<OwnerPersistenceProvider> owners,
            ObjectProvider<TaskRunClaims> claims,
            ObjectProvider<MeterRegistry> registry) {
        return new RuntimeTaskScheduler(properties, () -> {
            List<TaskProviders> providers = new ArrayList<>();
            plugins.ifAvailable(runtime -> providers.add(runtime.taskProviders()));
            operational.ifAvailable(runtime -> providers.add(runtime.taskProviders()));
            return providers.isEmpty() ? null : TaskProviders.of(providers);
        }, owners.getIfAvailable(OwnerPersistenceProvider::none), () -> claims.getIfAvailable(TaskRunClaims::none),
            new MicrometerTaskMetrics(registry::getIfAvailable), Clock.systemUTC());
    }
}
