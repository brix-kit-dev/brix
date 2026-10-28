/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.infra.adapter.persistence.jpa.config.TenantIsolationMeterBinder;
import io.infra.adapter.persistence.jpa.config.TenantIsolationMetricsAutoConfiguration;

/**
 * The Host's meter registry receives the adapter's tenant isolation counts while its context runs.
 */
class TenantIsolationMetricsAutoConfigurationTest {

    private static final String VIOLATION = "persistence.tenant_scope_violation";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            MetricsAutoConfiguration.class,
            CompositeMeterRegistryAutoConfiguration.class,
            SimpleMetricsExportAutoConfiguration.class,
            TenantIsolationMetricsAutoConfiguration.class));

    @Test
    void theHostsMeterRegistryReceivesTheCounts() {
        runner.run(context -> {
            MeterRegistry registry = context.getBean(MeterRegistry.class);

            TenantIsolationMetrics.violation(TenantTestEnvironment.OWNER, "tenant", "statement", VIOLATION);
            TenantIsolationMetrics.policyUse(TenantTestEnvironment.OWNER, "login-tenant-discovery", true);

            assertThat(registry.get(TenantIsolationMetrics.VIOLATIONS)
                .tags("owner", TenantTestEnvironment.OWNER, "scope", "tenant", "reason", "statement", "code", VIOLATION)
                .counter().count()).isEqualTo(1.0);
            assertThat(registry.get(TenantIsolationMetrics.POLICY_USES)
                .tags("policy", "login-tenant-discovery", "outcome", "success")
                .counter().count()).isEqualTo(1.0);
        });
    }

    @Test
    void aClosedContextReceivesNoMoreCounts() {
        AtomicReference<MeterRegistry> closed = new AtomicReference<>();
        runner.run(context -> closed.set(context.getBean(MeterRegistry.class)));

        TenantIsolationMetrics.violation(TenantTestEnvironment.OWNER, "tenant", "statement", VIOLATION);

        assertThat(closed.get().find(TenantIsolationMetrics.VIOLATIONS).counters()).isEmpty();
    }

    @Test
    void withoutMicrometerNothingIsConfigured() {
        runner.withClassLoader(new FilteredClassLoader(MeterBinder.class))
            .run(context -> assertThat(context).doesNotHaveBean(TenantIsolationMeterBinder.class));
    }
}
