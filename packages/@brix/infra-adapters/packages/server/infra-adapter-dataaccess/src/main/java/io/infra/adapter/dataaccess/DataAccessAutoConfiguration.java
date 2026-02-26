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
package io.infra.adapter.dataaccess;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import io.runtime.sdk.capability.AuthContextCapability;
import io.runtime.sdk.capability.DataAccessCapability;

/**
 * Auto Configuration for Data Access Capability.
 *
 * <p>Provides automatic configuration of {@link DataAccessCapability} implementation
 * with compliance auditing. Requires an {@link AuthContextCapability} bean to be
 * present for authorization checks.</p>
 *
 * <h3>Architecture Layer</h3>
 * <p>This module belongs to Layer 2.5 (Adapter Layer) as defined in the
 * v3.0 Runtime Shell Architecture Blueprint. It implements the capability
 * contract defined in Layer 2 (runtime-sdk-api).</p>
 *
 * <h3>Prerequisites</h3>
 * <p>This auto-configuration requires:</p>
 * <ul>
 *   <li>{@link AuthContextCapability} - For retrieving user's authorized scopes</li>
 * </ul>
 *
 * <h3>Configuration</h3>
 * <pre>
 * brix:
 *   dataaccess:
 *     audit:
 *       enabled: true
 *       queue-capacity: 10000
 * </pre>
 *
 * @author Brix Team
 * @version 3.1.0
 * @since 3.1.0
 * @see DataAccessCapability
 * @see RbacDataAccessCapability
 */
@AutoConfiguration
@EnableConfigurationProperties(DataAccessProperties.class)
public class DataAccessAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DataAccessAutoConfiguration.class);

    /**
     * Holder for the audit processor to ensure proper shutdown.
     */
    private DataAccessAuditProcessor auditProcessor;

    /**
     * Creates the async logging audit processor.
     *
     * <p>This processor writes audit records to structured logs asynchronously,
     * ensuring business operations are not blocked by audit processing.</p>
     *
     * @param properties Configuration properties
     * @return Async logging audit processor
     */
    @Bean
    @ConditionalOnMissingBean(DataAccessAuditProcessor.class)
    public DataAccessAuditProcessor dataAccessAuditProcessor(DataAccessProperties properties) {
        int capacity = properties.getAudit().getQueueCapacity();
        
        log.info("[DataAccess] Creating AsyncLoggingAuditProcessor with capacity={}", capacity);
        
        this.auditProcessor = new AsyncLoggingAuditProcessor(capacity);
        return this.auditProcessor;
    }

    /**
     * Creates the RBAC-based data access capability.
     *
     * <p>This capability provides:</p>
     * <ul>
     *   <li>Scope-based authorization using AuthContext</li>
     *   <li>Async compliance auditing</li>
     *   <li>Multi-tenant data isolation support</li>
     * </ul>
     *
     * <h4>Conditional Activation</h4>
     * <p>This bean is only created when:</p>
     * <ul>
     *   <li>An {@link AuthContextCapability} bean is available</li>
     *   <li>No custom {@link DataAccessCapability} is defined</li>
     * </ul>
     *
     * @param authContext    The authentication context capability
     * @param auditProcessor The audit processor
     * @param properties     Configuration properties
     * @return RBAC data access capability implementation
     */
    @Bean
    @ConditionalOnBean(AuthContextCapability.class)
    @ConditionalOnMissingBean(DataAccessCapability.class)
    public DataAccessCapability rbacDataAccessCapability(
            AuthContextCapability authContext,
            DataAccessAuditProcessor auditProcessor,
            DataAccessProperties properties) {
        
        boolean auditEnabled = properties.getAudit().isEnabled();
        
        log.info("[DataAccess] Creating RbacDataAccessCapability with auditEnabled={}", auditEnabled);
        
        return new RbacDataAccessCapability(authContext, auditProcessor, auditEnabled);
    }

    /**
     * Shutdown hook to flush pending audit records.
     */
    @PreDestroy
    public void shutdown() {
        if (auditProcessor != null) {
            auditProcessor.shutdown();
        }
    }
}
