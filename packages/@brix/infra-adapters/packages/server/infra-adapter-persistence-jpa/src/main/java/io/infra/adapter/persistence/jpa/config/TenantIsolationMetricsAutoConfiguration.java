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

import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Counts the adapter's tenant isolation in the Host's meter registry (multi-tenant isolation
 * design §4.6). Active when Micrometer is on the class path; Spring Boot binds the binder to the
 * registry the Host configures.
 */
@AutoConfiguration
@ConditionalOnClass(MeterBinder.class)
public class TenantIsolationMetricsAutoConfiguration {

    @Bean
    public TenantIsolationMeterBinder tenantIsolationMeterBinder() {
        return new TenantIsolationMeterBinder();
    }
}
