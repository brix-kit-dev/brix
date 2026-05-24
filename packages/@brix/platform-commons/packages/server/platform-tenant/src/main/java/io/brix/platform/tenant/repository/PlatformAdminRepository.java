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

import io.brix.platform.tenant.entity.PlatformAdmin;
import io.brix.platform.tenant.enums.PlatformAdminRole;
import io.brix.platform.tenant.enums.PlatformAdminStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for PlatformAdmin entity operations.
 *
 * <p>Provides data access methods for the sys_platform_admin table.
 * Platform admins have cross-tenant access for platform management.
 *
 * <h3>Security Note</h3>
 * <p>Access to this repository should be heavily restricted.
 * All operations should be audit logged.
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see PlatformAdmin
 */
@Repository
public interface PlatformAdminRepository extends JpaRepository<PlatformAdmin, Long> {

    /**
     * Finds a platform admin by identity ID.
     *
     * @param identityId the identity ID
     * @return optional containing the platform admin
     */
    Optional<PlatformAdmin> findByIdentityId(Long identityId);

    /**
     * Checks if an identity is a platform admin.
     *
     * @param identityId the identity ID
     * @return true if identity has platform admin record
     */
    boolean existsByIdentityId(Long identityId);

    /**
     * Checks if an identity is an active platform admin.
     *
     * @param identityId the identity ID
     * @return true if identity is an active platform admin
     */
    @Query("SELECT CASE WHEN COUNT(pa) > 0 THEN true ELSE false END FROM PlatformAdmin pa " +
           "WHERE pa.identityId = :identityId AND pa.status = io.brix.platform.tenant.enums.PlatformAdminStatus.ACTIVE")
    boolean isActivePlatformAdmin(@Param("identityId") Long identityId);

    /**
     * Finds all platform admins with a specific role.
     *
     * @param role the platform admin role
     * @return list of platform admins with the role
     */
    List<PlatformAdmin> findByRole(PlatformAdminRole role);

    /**
     * Finds all active platform admins.
     *
     * @return list of active platform admins
     */
    @Query("SELECT pa FROM PlatformAdmin pa "
           + "WHERE pa.status = io.brix.platform.tenant.enums.PlatformAdminStatus.ACTIVE "
           + "ORDER BY pa.role")
    List<PlatformAdmin> findAllActive();

    /**
     * Finds all super admins.
     *
     * @return list of super admin accounts
     */
    @Query("SELECT pa FROM PlatformAdmin pa "
           + "WHERE pa.role = io.brix.platform.tenant.enums.PlatformAdminRole.PLATFORM_SUPER_ADMIN "
           + "AND pa.status = io.brix.platform.tenant.enums.PlatformAdminStatus.ACTIVE")
    List<PlatformAdmin> findActiveSuperAdmins();

    /**
     * Finds platform admins without MFA enabled.
     *
     * <p>Used for security compliance monitoring.
     *
     * @return list of admins without MFA
     */
    @Query("SELECT pa FROM PlatformAdmin pa "
           + "WHERE pa.mfaEnabled = false "
           + "AND pa.status = io.brix.platform.tenant.enums.PlatformAdminStatus.ACTIVE")
    List<PlatformAdmin> findWithoutMfa();

    /**
     * Counts active platform admins by role.
     *
     * @param role the role to count
     * @return count of active admins with the role
     */
    @Query("SELECT COUNT(pa) FROM PlatformAdmin pa "
           + "WHERE pa.role = :role "
           + "AND pa.status = io.brix.platform.tenant.enums.PlatformAdminStatus.ACTIVE")
    long countActiveByRole(@Param("role") PlatformAdminRole role);

    @Query("SELECT COUNT(pa) FROM PlatformAdmin pa JOIN Identity i ON i.id = pa.identityId "
           + "WHERE pa.role = io.brix.platform.tenant.enums.PlatformAdminRole.PLATFORM_SUPER_ADMIN "
           + "AND pa.status = io.brix.platform.tenant.enums.PlatformAdminStatus.ACTIVE "
           + "AND i.status = io.brix.platform.tenant.enums.IdentityStatus.ACTIVE "
           + "AND i.mfaEnabled = true")
    long countCompletedFormalSuperAdmins();

    /**
     * Counts all active platform admins.
     *
     * @return total count of active platform admins
     */
       long countByStatus(PlatformAdminStatus status);

    /**
       * Checks if the identity has PLATFORM_SUPER_ADMIN role.
     *
     * @param identityId the identity ID
     * @return true if identity is a super admin
     */
    @Query("SELECT CASE WHEN COUNT(pa) > 0 THEN true ELSE false END FROM PlatformAdmin pa " +
           "WHERE pa.identityId = :identityId " +
           "AND pa.role = io.brix.platform.tenant.enums.PlatformAdminRole.PLATFORM_SUPER_ADMIN " +
           "AND pa.status = io.brix.platform.tenant.enums.PlatformAdminStatus.ACTIVE")
    boolean isSuperAdmin(@Param("identityId") Long identityId);
}
