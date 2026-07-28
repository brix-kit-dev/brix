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

import io.brix.platform.tenant.entity.Tenant;
import io.brix.platform.tenant.enums.TenantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Tenant entity operations.
 *
 * <p>Provides data access methods for the sys_tenant table.
 * This is a system-level repository (no automatic tenant filtering).
 *
 * <h3>Note on Tenant Isolation</h3>
 * <p>The sys_tenant table itself does not have tenant_id as it defines
 * the tenants. Access to this repository should be restricted to
 * platform-level operations.
 *
 * <h3>Usage</h3>
 * <ul>
 *   <li>Platform admin: Full access</li>
 *   <li>Tenant admin: Read access to own tenant only</li>
 *   <li>Regular user: No direct access (go through service layer)</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see Tenant
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    /**
     * Finds a tenant by its unique code.
     *
     * <p>The code is the business identifier for the tenant (e.g., subdomain).
     *
     * @param code the tenant code
     * @return optional containing the tenant, or empty if not found
     */
    Optional<Tenant> findByCode(String code);

    /**
     * Checks if a tenant code already exists.
     *
     * <p>Used for validation during tenant creation.
     *
     * @param code the tenant code to check
     * @return true if the code is already in use
     */
    boolean existsByCode(String code);

    /**
     * Finds all tenants with a specific status.
     *
     * @param status the tenant status to filter by
     * @return list of tenants with the specified status
     */
    List<Tenant> findByStatus(TenantStatus status);

    /**
     * Finds all active tenants.
     *
     * <p>Convenience method for finding operational tenants.
     *
     * @return list of active tenants
     */
    @Query("SELECT t FROM Tenant t WHERE t.status = 'ACTIVE' ORDER BY t.name")
    List<Tenant> findAllActive();

    /**
     * Counts tenants by status.
     *
     * <p>Used for dashboard and monitoring.
     *
     * @param status the status to count
     * @return number of tenants with the specified status
     */
    long countByStatus(TenantStatus status);

    /**
     * Finds tenants with name containing the search term (case-insensitive).
     *
     * @param searchTerm the term to search for
     * @return list of matching tenants
     */
    @Query("SELECT t FROM Tenant t WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :term, '%'))")
    List<Tenant> findByNameContaining(@Param("term") String searchTerm);

    /**
     * Checks if a tenant is active.
     *
     * @param id the tenant ID
     * @return true if tenant exists and is active
     */
    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM Tenant t WHERE t.id = :id AND t.status = 'ACTIVE'")
    boolean isActive(@Param("id") Long id);

    /**
     * Finds tenants by name (case-insensitive, paginated).
     *
     * @param name the search term
     * @param pageable pagination parameters
     * @return page of matching tenants
     */
    Page<Tenant> findByNameContainingIgnoreCase(String name, Pageable pageable);

    /**
     * Finds tenants by status (paginated).
     *
     * @param status the tenant status
     * @param pageable pagination parameters
     * @return page of matching tenants
     */
    Page<Tenant> findByStatus(TenantStatus status, Pageable pageable);

    /**
     * Locks one tenant row for lifecycle changes.
     *
     * @param id tenant id
     * @return tenant when present
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Tenant t WHERE t.id = :id")
    Optional<Tenant> findByIdForUpdate(@Param("id") Long id);
}
