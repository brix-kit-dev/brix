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

import io.brix.platform.tenant.entity.Organization;
import io.brix.platform.tenant.enums.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Organization entity operations.
 *
 * <p>Provides data access methods for the sys_organization table.
 * Organizations are tenant-scoped, so most queries include tenant_id.
 *
 * <h3>Tenant Isolation</h3>
 * <p>Unlike system tables, organizations HAVE tenant_id and all queries
 * should include tenant filtering. This repository provides tenant-aware
 * query methods.
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see Organization
 */
@Repository
public interface OrganizationRepository extends JpaRepository<Organization, Long> {

    /**
     * Finds all organizations for a tenant.
     *
     * @param tenantId the tenant ID
     * @return list of organizations
     */
    List<Organization> findByTenantId(Long tenantId);

    /**
     * Finds active organizations for a tenant, ordered for tree display.
     *
     * @param tenantId the tenant ID
     * @return list of active organizations
     */
    @Query("SELECT o FROM Organization o WHERE o.tenantId = :tenantId AND o.status = 'ACTIVE' " +
           "ORDER BY o.parentId NULLS FIRST, o.sortOrder, o.name")
    List<Organization> findActiveByTenantIdOrdered(@Param("tenantId") Long tenantId);

    /**
     * Finds an organization by tenant and code.
     *
     * @param tenantId the tenant ID
     * @param code the organization code
     * @return optional containing the organization
     */
    Optional<Organization> findByTenantIdAndCode(Long tenantId, String code);

    /**
     * Checks if an organization code exists within a tenant.
     *
     * @param tenantId the tenant ID
     * @param code the code to check
     * @return true if code already exists
     */
    boolean existsByTenantIdAndCode(Long tenantId, String code);

    /**
     * Finds root organizations (no parent) for a tenant.
     *
     * @param tenantId the tenant ID
     * @return list of root organizations
     */
    @Query("SELECT o FROM Organization o WHERE o.tenantId = :tenantId AND o.parentId IS NULL " +
           "ORDER BY o.sortOrder, o.name")
    List<Organization> findRootsByTenantId(@Param("tenantId") Long tenantId);

    /**
     * Finds child organizations of a parent.
     *
     * @param tenantId the tenant ID
     * @param parentId the parent organization ID
     * @return list of child organizations
     */
    @Query("SELECT o FROM Organization o WHERE o.tenantId = :tenantId AND o.parentId = :parentId " +
           "ORDER BY o.sortOrder, o.name")
    List<Organization> findByTenantIdAndParentId(@Param("tenantId") Long tenantId, @Param("parentId") Long parentId);

    /**
     * Checks if an organization has children.
     *
     * @param parentId the parent organization ID
     * @return true if children exist
     */
    @Query("SELECT CASE WHEN COUNT(o) > 0 THEN true ELSE false END FROM Organization o WHERE o.parentId = :parentId")
    boolean hasChildren(@Param("parentId") Long parentId);

    /**
     * Counts organizations by tenant and status.
     *
     * @param tenantId the tenant ID
     * @param status the status
     * @return count of matching organizations
     */
    long countByTenantIdAndStatus(Long tenantId, MemberStatus status);

    /**
     * Finds organizations by type within a tenant.
     *
     * @param tenantId the tenant ID
     * @param orgType the organization type
     * @return list of matching organizations
     */
    List<Organization> findByTenantIdAndOrgType(Long tenantId, String orgType);

    /**
     * Searches organizations by name within a tenant.
     *
     * @param tenantId the tenant ID
     * @param searchTerm the search term
     * @return list of matching organizations
     */
    @Query("SELECT o FROM Organization o WHERE o.tenantId = :tenantId AND " +
           "LOWER(o.name) LIKE LOWER(CONCAT('%', :term, '%'))")
    List<Organization> searchByTenantIdAndName(@Param("tenantId") Long tenantId, @Param("term") String searchTerm);

    /**
     * Gets the maximum sort order for siblings under a parent.
     *
     * <p>Used when adding new organizations to determine sort order.
     *
     * @param tenantId the tenant ID
     * @param parentId the parent ID (null for roots)
     * @return maximum sort order, or 0 if no siblings
     */
    @Query("SELECT COALESCE(MAX(o.sortOrder), 0) FROM Organization o " +
           "WHERE o.tenantId = :tenantId AND (o.parentId = :parentId OR (o.parentId IS NULL AND :parentId IS NULL))")
    int getMaxSortOrder(@Param("tenantId") Long tenantId, @Param("parentId") Long parentId);
}
