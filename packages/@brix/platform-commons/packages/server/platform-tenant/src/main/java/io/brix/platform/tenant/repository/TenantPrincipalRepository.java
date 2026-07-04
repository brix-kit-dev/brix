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

import io.brix.platform.tenant.entity.TenantPrincipal;
import io.brix.platform.tenant.enums.PrincipalStatus;
import io.brix.platform.tenant.enums.PrincipalType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for TenantPrincipal entity operations.
 *
 * <p>Provides data access methods for the sys_tenant_principal table.
 * This repository handles the C-side (Subject) relationships between
 * identities and tenants in the B2B2C model.
 *
 * <h3>Query Patterns</h3>
 * <ul>
 *   <li>By tenant: List all principals (customers/guests) of a tenant</li>
 *   <li>By identity: List all tenants where identity is a principal</li>
 *   <li>By combination: Check specific principal relationship</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 * @see TenantPrincipal
 */
@Repository
public interface TenantPrincipalRepository extends JpaRepository<TenantPrincipal, Long> {

    /**
     * Finds all principals of a specific tenant.
     *
     * @param tenantId the tenant ID
     * @return list of tenant principals
     */
    List<TenantPrincipal> findByTenantId(Long tenantId);

    /**
     * Finds all active principals of a specific tenant.
     *
     * @param tenantId the tenant ID
     * @return list of active tenant principals
     */
    @Query("SELECT tp FROM TenantPrincipal tp WHERE tp.tenantId = :tenantId AND tp.status = 'ACTIVE' ORDER BY tp.principalType")
    List<TenantPrincipal> findActiveByTenantId(@Param("tenantId") Long tenantId);

    /**
     * Finds all tenants where an identity is a principal.
     *
     * @param identityId the identity ID
     * @return list of principal relationships
     */
    List<TenantPrincipal> findByIdentityId(Long identityId);

    /**
     * Finds all active principal relationships for an identity.
     *
     * <p>Used to determine which tenants a user can access as a Subject.
     * Combined with {@code TenantMemberRepository.findActiveByIdentityId()}
     * to build the complete tenant selector list.
     *
     * @param identityId the identity ID
     * @return list of active principal relationships
     */
    @Query("SELECT tp FROM TenantPrincipal tp WHERE tp.identityId = :identityId AND tp.status = 'ACTIVE'")
    List<TenantPrincipal> findActiveByIdentityId(@Param("identityId") Long identityId);

    /**
     * Finds a specific principal by tenant and identity.
     *
     * @param tenantId the tenant ID
     * @param identityId the identity ID
     * @return optional containing the principal relationship
     */
    Optional<TenantPrincipal> findByTenantIdAndIdentityId(Long tenantId, Long identityId);

    /**
     * Finds a principal by immutable context ID.
     *
     * @param contextId immutable context ID
     * @return principal if present
     */
    Optional<TenantPrincipal> findByContextId(UUID contextId);

    /**
     * Checks if an identity is a principal of a tenant.
     *
     * @param tenantId the tenant ID
     * @param identityId the identity ID
     * @return true if principal relationship exists
     */
    boolean existsByTenantIdAndIdentityId(Long tenantId, Long identityId);

    /**
     * Checks if an identity is an active principal of a tenant.
     *
     * <p>Used during Subject token validation to verify the principal
     * relationship is still valid.
     *
     * @param tenantId the tenant ID
     * @param identityId the identity ID
     * @return true if active principal relationship exists
     */
    @Query("SELECT CASE WHEN COUNT(tp) > 0 THEN true ELSE false END FROM TenantPrincipal tp " +
           "WHERE tp.tenantId = :tenantId AND tp.identityId = :identityId AND tp.status = 'ACTIVE'")
    boolean isActivePrincipal(@Param("tenantId") Long tenantId, @Param("identityId") Long identityId);

    /**
     * Finds principals by tenant and type.
     *
     * @param tenantId the tenant ID
     * @param principalType the principal type
     * @return list of matching principals
     */
    List<TenantPrincipal> findByTenantIdAndPrincipalType(Long tenantId, PrincipalType principalType);

    /**
     * Counts principals by tenant and status.
     *
     * @param tenantId the tenant ID
     * @param status the principal status
     * @return count of matching principals
     */
    long countByTenantIdAndStatus(Long tenantId, PrincipalStatus status);

    /**
     * Counts active principals per tenant.
     *
     * @param tenantId the tenant ID
     * @return count of active principals
     */
    @Query("SELECT COUNT(tp) FROM TenantPrincipal tp WHERE tp.tenantId = :tenantId AND tp.status = 'ACTIVE'")
    long countActivePrincipals(@Param("tenantId") Long tenantId);
}
