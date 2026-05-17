/*
 * Copyright 2026 Brix Platform Authors
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
package io.brix.platform.tenant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.brix.platform.tenant.entity.TenantConfig;

/**
 * Repository for TenantConfig entity operations.
 *
 * <p>Provides data access methods for the sys_tenant_config table.
 * This repository manages plugin-level configuration scoped per tenant.
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Platform Commons — data access for tenant configuration.</p>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see TenantConfig
 */
@Repository
public interface TenantConfigRepository extends JpaRepository<TenantConfig, Long> {

    /**
     * Finds all configuration entries for a given tenant.
     *
     * @param tenantId the tenant ID
     * @return list of all tenant config entries
     */
    List<TenantConfig> findByTenantId(Long tenantId);

    /**
     * Finds all configuration entries for a given tenant and namespace.
     *
     * @param tenantId  the tenant ID
     * @param configNamespace the configuration namespace (e.g. "platform", "reservation")
     * @return list of config entries within the namespace
     */
    List<TenantConfig> findByTenantIdAndConfigNamespace(Long tenantId, String configNamespace);

    /**
     * Finds a specific configuration entry by tenant, namespace, and key.
     *
     * @param tenantId        the tenant ID
     * @param configNamespace the namespace
     * @param configKey       the configuration key
     * @return the config entry if found
     */
    Optional<TenantConfig> findByTenantIdAndConfigNamespaceAndConfigKey(
            Long tenantId, String configNamespace, String configKey);

    /**
     * Deletes a specific configuration entry by tenant, namespace, and key.
     *
     * @param tenantId        the tenant ID
     * @param configNamespace the namespace
     * @param configKey       the configuration key
     */
    void deleteByTenantIdAndConfigNamespaceAndConfigKey(
            Long tenantId, String configNamespace, String configKey);

    /**
     * Deletes all configuration entries for a given tenant and namespace.
     *
     * @param tenantId        the tenant ID
     * @param configNamespace the namespace
     */
    void deleteByTenantIdAndConfigNamespace(Long tenantId, String configNamespace);
}
