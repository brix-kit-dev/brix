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

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.brix.platform.tenant.entity.AuditLog;

/**
 * Repository for AuditLog entity operations.
 *
 * <p>Provides data access methods for the biz_audit_log table.
 * Audit logs support both tenant-scoped and system-level queries.
 *
 * <h3>Query Patterns</h3>
 * <ul>
 *   <li>By tenant: All logs for a specific tenant</li>
 *   <li>By user: All actions by a specific identity</li>
 *   <li>By resource: All actions on a specific entity</li>
 *   <li>By time: Logs within a time range</li>
 *   <li>Failed operations: For security monitoring</li>
 * </ul>
 *
 * <h3>Retention Note</h3>
 * <p>Audit logs are append-only in application code.
 * Retention policies should be implemented at the database level
 * using scheduled cleanup or partitioning.
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see AuditLog
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /**
     * Finds audit logs for a tenant with pagination.
     *
     * @param tenantId the tenant ID
     * @param pageable pagination parameters
     * @return page of audit logs
     */
    Page<AuditLog> findByTenantId(Long tenantId, Pageable pageable);

    /**
     * Finds audit logs within a time range for a tenant.
     *
     * @param tenantId the tenant ID
     * @param startTime range start
     * @param endTime range end
     * @param pageable pagination parameters
     * @return page of audit logs
     */
    @Query("SELECT a FROM AuditLog a WHERE a.tenantId = :tenantId AND " +
           "a.createdAt >= :startTime AND a.createdAt <= :endTime ORDER BY a.createdAt DESC")
    Page<AuditLog> findByTenantIdAndTimeRange(
        @Param("tenantId") Long tenantId,
        @Param("startTime") OffsetDateTime startTime,
        @Param("endTime") OffsetDateTime endTime,
        Pageable pageable
    );

    /**
     * Finds audit logs by the user who performed the action.
     *
     * @param createdBy the identity ID
     * @param pageable pagination parameters
     * @return page of audit logs
     */
    Page<AuditLog> findByCreatedBy(Long createdBy, Pageable pageable);

    /**
     * Finds audit logs for a specific resource.
     *
     * @param resourceType the resource type
     * @param resourceId the resource ID
     * @return list of audit logs
     */
    @Query("SELECT a FROM AuditLog a WHERE a.resourceType = :type AND a.resourceId = :id ORDER BY a.createdAt DESC")
    List<AuditLog> findByResource(@Param("type") String resourceType, @Param("id") String resourceId);

    /**
     * Finds audit logs for a specific action type within a tenant.
     *
     * @param tenantId the tenant ID
     * @param action the action type
     * @param pageable pagination parameters
     * @return page of audit logs
     */
    Page<AuditLog> findByTenantIdAndAction(Long tenantId, String action, Pageable pageable);

    /**
     * Finds failed operations for security monitoring.
     *
     * @param tenantId the tenant ID
     * @param startTime range start
     * @return list of failed audit logs
     */
    @Query("SELECT a FROM AuditLog a WHERE a.tenantId = :tenantId AND a.success = false AND " +
           "a.createdAt >= :startTime ORDER BY a.createdAt DESC")
    List<AuditLog> findFailedOperations(@Param("tenantId") Long tenantId, @Param("startTime") OffsetDateTime startTime);

    /**
     * Finds failed login attempts (system-wide).
     *
     * @param startTime range start
     * @return list of failed login attempts
     */
    @Query("SELECT a FROM AuditLog a WHERE a.action = 'LOGIN' AND a.success = false AND " +
           "a.createdAt >= :startTime ORDER BY a.createdAt DESC")
    List<AuditLog> findFailedLoginAttempts(@Param("startTime") OffsetDateTime startTime);

    /**
     * Finds recent audit logs for a tenant.
     *
     * @param tenantId the tenant ID
     * @param limit maximum number of results
     * @return list of recent audit logs
     */
    @Query("SELECT a FROM AuditLog a WHERE a.tenantId = :tenantId ORDER BY a.createdAt DESC")
    List<AuditLog> findRecentByTenantId(@Param("tenantId") Long tenantId, Pageable limit);

    /**
     * Finds audit logs by request correlation ID.
     *
     * @param requestId the request ID
     * @return list of correlated audit logs
     */
    List<AuditLog> findByRequestId(String requestId);

    /**
     * Counts audit logs by action for a tenant.
     *
     * @param tenantId the tenant ID
     * @param action the action type
     * @return count of matching logs
     */
    long countByTenantIdAndAction(Long tenantId, String action);

    /**
     * Counts failed operations for a tenant within a time range.
     *
     * @param tenantId the tenant ID
     * @param startTime range start
     * @return count of failed operations
     */
    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.tenantId = :tenantId AND a.success = false AND " +
           "a.createdAt >= :startTime")
    long countFailedOperations(@Param("tenantId") Long tenantId, @Param("startTime") OffsetDateTime startTime);

    /**
     * Finds system-level audit logs (no tenant context).
     *
     * @param pageable pagination parameters
     * @return page of system audit logs
     */
    @Query("SELECT a FROM AuditLog a WHERE a.tenantId IS NULL ORDER BY a.createdAt DESC")
    Page<AuditLog> findSystemLogs(Pageable pageable);

       /**
        * Finds platform-scoped audit logs with optional filters.
        *
        * @param action optional action code
        * @param createdBy optional operator identity ID
        * @param success optional result flag
        * @param fromTime optional inclusive lower timestamp bound
        * @param toTime optional exclusive upper timestamp bound
        * @param pageable pagination parameters
        * @return page of platform-scoped audit logs
        */
       @Query("SELECT a FROM AuditLog a WHERE a.tenantId IS NULL " +
                 "AND (:action IS NULL OR a.action = :action) " +
                 "AND (:createdBy IS NULL OR a.createdBy = :createdBy) " +
                 "AND (:success IS NULL OR a.success = :success) " +
                 "AND (:fromTime IS NULL OR a.createdAt >= :fromTime) " +
                 "AND (:toTime IS NULL OR a.createdAt < :toTime)")
       Page<AuditLog> findPlatformLogs(
              @Param("action") String action,
              @Param("createdBy") Long createdBy,
              @Param("success") Boolean success,
              @Param("fromTime") OffsetDateTime fromTime,
              @Param("toTime") OffsetDateTime toTime,
              Pageable pageable
       );

    /**
     * Finds audit logs by owner member.
     *
     * @param tenantId the tenant ID
     * @param ownerMemberId the owner member ID
     * @param pageable pagination parameters
     * @return page of audit logs
     */
    Page<AuditLog> findByTenantIdAndOwnerMemberId(Long tenantId, Long ownerMemberId, Pageable pageable);

    /**
     * Finds audit logs by owner organization.
     *
     * @param tenantId the tenant ID
     * @param ownerOrgId the owner organization ID
     * @param pageable pagination parameters
     * @return page of audit logs
     */
    Page<AuditLog> findByTenantIdAndOwnerOrgId(Long tenantId, Long ownerOrgId, Pageable pageable);
}
