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
package io.brix.platform.audit.server.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;

import io.brix.platform.audit.core.repository.PlatformAuditLogRepository;
import io.brix.platform.audit.core.service.PlatformAuditService;
import io.brix.platform.audit.internal.PlatformAuditAdministration;
import io.runtime.sdk.capability.IdGeneratorCapability;
import io.runtime.sdk.capability.PersistenceCapability;

/**
 * Assembles the platform audit Data Owner. Its repository comes only from the owner-scoped
 * {@link PersistenceCapability}; the entity lives in the owner's core artifact, which the
 * descriptor lists under {@code modules}. Ids come from the Host's {@link IdGeneratorCapability}.
 *
 * @since 3.2.0
 */
@AutoConfiguration
@EntityScan(basePackages = "io.brix.platform.audit.core.entity")
public class PlatformAuditAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(PlatformAuditAdministration.class)
    public PlatformAuditAdministration platformAuditAdministration(
            PersistenceCapability persistence, IdGeneratorCapability ids) {
        return new PlatformAuditService(persistence.repository(PlatformAuditLogRepository.class), ids);
    }
}
