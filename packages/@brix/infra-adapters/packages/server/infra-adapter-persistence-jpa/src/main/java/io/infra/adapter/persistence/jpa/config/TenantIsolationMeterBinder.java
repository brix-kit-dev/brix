/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.infra.adapter.persistence.jpa.config;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.DisposableBean;

import io.infra.adapter.persistence.jpa.TenantIsolationMetrics;

/**
 * Sends the adapter's tenant isolation counts ({@link TenantIsolationMetrics}) to a meter
 * registry. Spring Boot binds it to the Host's registry; without one nothing is counted.
 */
public final class TenantIsolationMeterBinder implements MeterBinder, DisposableBean {

    private final List<TenantIsolationMetrics.Installation> installations = new CopyOnWriteArrayList<>();

    @Override
    public void bindTo(MeterRegistry registry) {
        installations.add(TenantIsolationMetrics.install(new MicrometerRecorder(registry)));
    }

    @Override
    public void destroy() {
        installations.forEach(TenantIsolationMetrics.Installation::close);
        installations.clear();
    }

    private record MicrometerRecorder(MeterRegistry registry) implements TenantIsolationMetrics.Recorder {

        @Override
        public void violation(String owner, String scope, String reason, String code) {
            Counter.builder(TenantIsolationMetrics.VIOLATIONS)
                .description("Data accesses the persistence adapter refused to keep tenants isolated")
                .tag("owner", owner)
                .tag("scope", scope)
                .tag("reason", reason)
                .tag("code", code)
                .register(registry)
                .increment();
        }

        @Override
        public void policyUse(String owner, String policy, String outcome) {
            Counter.builder(TenantIsolationMetrics.POLICY_USES)
                .description("Owner transactions that ran under a registered cross-tenant policy or a platform operation")
                .tag("owner", owner)
                .tag("policy", policy)
                .tag("outcome", outcome)
                .register(registry)
                .increment();
        }

        @Override
        public void policyAuditFailure(String policy, String reason) {
            Counter.builder(TenantIsolationMetrics.POLICY_AUDIT_FAILURES)
                .description("Cross-tenant policy uses that failed because their platform audit record was not written")
                .tag("policy", policy)
                .tag("reason", reason)
                .register(registry)
                .increment();
        }
    }
}
