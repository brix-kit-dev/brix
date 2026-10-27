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

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.spi.SessionImplementor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;

import io.infra.adapter.persistence.jpa.TenantStatementVerifier;

/**
 * Registers the tenant statement verifier on the shared persistence unit (root BRX-RS-DATA-040).
 *
 * <p>It has no switch: shared and production hosts always verify, and a statement it cannot
 * prove safe fails in every environment. The tenant filters and write listeners need no
 * registration here; Hibernate discovers them from this module.</p>
 */
@AutoConfiguration(before = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass(SessionImplementor.class)
public class TenantStatementVerifierAutoConfiguration {

    @Bean
    public HibernatePropertiesCustomizer tenantStatementVerifierCustomizer() {
        TenantStatementVerifier verifier = new TenantStatementVerifier();
        return properties -> {
            Object present = properties.get(AvailableSettings.STATEMENT_INSPECTOR);
            if (present != null && present != verifier) {
                throw new IllegalStateException("A second statement inspector (" + present
                    + ") would replace the tenant statement verifier");
            }
            properties.put(AvailableSettings.STATEMENT_INSPECTOR, verifier);
        };
    }
}
