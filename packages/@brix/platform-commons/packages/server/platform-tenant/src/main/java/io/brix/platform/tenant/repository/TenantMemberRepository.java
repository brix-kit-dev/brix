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
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.brix.platform.tenant.entity.TenantMember;
import io.brix.platform.tenant.enums.MemberStatus;
import io.brix.platform.tenant.enums.TenantMemberType;

/**
 * Repository for TenantMember entity operations.
 *
 * <p>Provides data access methods for the sys_tenant_member table.
 * This repository handles the many-to-many relationship between
 * identities and tenants.
 *
 * <h3>Query Patterns</h3>
 * <ul>
 *   <li>By tenant: List all members of a tenant</li>
 *   <li>By identity: List all tenants a user belongs to</li>
 *   <li>By combination: Check specific membership</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see TenantMember
 */
@Repository
public interface TenantMemberRepository extends JpaRepository<TenantMember, Long> {

    /**
     * Finds all members of a specific tenant.
     *
     * @param tenantId the tenant ID
     * @return list of tenant members
     */
    List<TenantMember> findByTenantId(Long tenantId);

    /**
     * Finds all active members of a specific tenant.
     *
     * @param tenantId the tenant ID
     * @return list of active tenant members
     */
    @Query("SELECT tm FROM TenantMember tm WHERE tm.tenantId = :tenantId AND tm.status = 'ACTIVE' ORDER BY tm.memberType")
    List<TenantMember> findActiveByTenantId(@Param("tenantId") Long tenantId);

    /**
     * Finds all tenants that an identity belongs to.
     *
     * @param identityId the identity ID
     * @return list of tenant memberships
     */
    List<TenantMember> findByIdentityId(Long identityId);

    /**
     * Finds all active tenant memberships for an identity.
     *
     * <p>Used to determine which tenants a user can access.
     *
     * @param identityId the identity ID
     * @return list of active memberships
     */
    @Query("SELECT tm FROM TenantMember tm WHERE tm.identityId = :identityId AND tm.status = 'ACTIVE'")
    List<TenantMember> findActiveByIdentityId(@Param("identityId") Long identityId);

    /**
     * Finds a specific membership by tenant and identity.
     *
     * @param tenantId the tenant ID
     * @param identityId the identity ID
     * @return optional containing the membership
     */
    Optional<TenantMember> findByTenantIdAndIdentityId(Long tenantId, Long identityId);

    /**
     * Finds a membership by immutable context ID.
     *
     * @param contextId immutable context ID
     * @return membership if present
     */
    Optional<TenantMember> findByContextId(UUID contextId);

    /**
     * Checks if an identity is a member of a tenant.
     *
     * @param tenantId the tenant ID
     * @param identityId the identity ID
     * @return true if membership exists
     */
    boolean existsByTenantIdAndIdentityId(Long tenantId, Long identityId);

    /**
     * Checks if an identity is an active member of a tenant.
     *
     * @param tenantId the tenant ID
     * @param identityId the identity ID
     * @return true if active membership exists
     */
    @Query("SELECT CASE WHEN COUNT(tm) > 0 THEN true ELSE false END FROM TenantMember tm " +
           "WHERE tm.tenantId = :tenantId AND tm.identityId = :identityId AND tm.status = 'ACTIVE'")
    boolean isActiveMember(@Param("tenantId") Long tenantId, @Param("identityId") Long identityId);

    /**
     * Finds the owner of a tenant.
     *
     * <p>Each tenant should have exactly one owner.
     *
     * @param tenantId the tenant ID
     * @return optional containing the owner membership
     */
    @Query("SELECT tm FROM TenantMember tm WHERE tm.tenantId = :tenantId AND tm.memberType = 'OWNER'")
    Optional<TenantMember> findOwnerByTenantId(@Param("tenantId") Long tenantId);

    /**
     * Checks whether a tenant already has an active OWNER.
     *
     * @param tenantId tenant id
     * @return true when an active OWNER exists
     */
    @Query("SELECT CASE WHEN COUNT(tm) > 0 THEN true ELSE false END FROM TenantMember tm "
            + "WHERE tm.tenantId = :tenantId AND tm.memberType = 'OWNER' AND tm.status = 'ACTIVE'")
    boolean existsActiveOwnerByTenantId(@Param("tenantId") Long tenantId);

    /**
     * Finds all admins of a tenant (OWNER and ADMIN types).
     *
     * @param tenantId the tenant ID
     * @return list of admin memberships
     */
    @Query("SELECT tm FROM TenantMember tm WHERE tm.tenantId = :tenantId AND " +
           "(tm.memberType = 'OWNER' OR tm.memberType = 'ADMIN') AND tm.status = 'ACTIVE'")
    List<TenantMember> findAdminsByTenantId(@Param("tenantId") Long tenantId);

    /**
     * Finds members by tenant and type.
     *
     * @param tenantId the tenant ID
     * @param memberType the member type
     * @return list of matching memberships
     */
    List<TenantMember> findByTenantIdAndMemberType(Long tenantId, TenantMemberType memberType);

    /**
     * Counts members by tenant and status.
     *
     * @param tenantId the tenant ID
     * @param status the member status
     * @return count of matching members
     */
    long countByTenantIdAndStatus(Long tenantId, MemberStatus status);

    /**
     * Counts active members per tenant.
     *
     * @param tenantId the tenant ID
     * @return count of active members
     */
    @Query("SELECT COUNT(tm) FROM TenantMember tm WHERE tm.tenantId = :tenantId AND tm.status = 'ACTIVE'")
    long countActiveMembers(@Param("tenantId") Long tenantId);

       /**
        * Counts non-terminated tenants owned by an identity.
        *
        * @param identityId the identity ID
        * @return count of owned tenants that are not terminated
        */
       @Query("SELECT COUNT(tm) FROM TenantMember tm JOIN Tenant t ON t.id = tm.tenantId " +
                 "WHERE tm.identityId = :identityId AND tm.memberType = 'OWNER' AND t.status <> 'TERMINATED'")
       long countNonTerminatedOwnedTenants(@Param("identityId") Long identityId);
}
