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
package io.brix.platform.tenant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import io.brix.platform.tenant.annotation.CrossTenantAccess;
import io.brix.platform.tenant.core.IdGenerator;
import io.brix.platform.tenant.dto.AuditEvent;
import io.brix.platform.tenant.entity.AuditLog;
import io.brix.platform.tenant.repository.AuditLogRepository;

/**
 * Implementation of {@link AuditService} for recording audit events.
 *
 * <p>This service provides synchronous audit logging by directly persisting
 * audit events to the biz_audit_log table. It is designed for simplicity
 * and reliability in the MVP phase.
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Implementation Layer - Platform Commons Service Implementation</p>
 *
 * <h3>Transaction Strategy</h3>
 * <p>Uses REQUIRES_NEW propagation to ensure audit logs are committed
 * independently of the calling transaction. This is critical for compliance:
 * <ul>
 *   <li>Failed operations are still logged for security analysis</li>
 *   <li>Audit trail is preserved even if business transaction rolls back</li>
 *   <li>No audit data loss due to transaction failures</li>
 * </ul>
 *
 * <h3>ID Generation</h3>
 * <p>Audit log IDs are generated using the Snowflake algorithm, ensuring:
 * <ul>
 *   <li>Global uniqueness across distributed deployments</li>
 *   <li>Time-ordered IDs for efficient querying by time range</li>
 *   <li>No database sequence contention</li>
 * </ul>
 *
 * <h3>Performance Characteristics</h3>
 * <p>This implementation is synchronous:
 * <ul>
 *   <li>Each log operation incurs database write latency</li>
 *   <li>Suitable for MVP with moderate throughput requirements</li>
 *   <li>Can be enhanced with async/batch processing for high-volume scenarios</li>
 * </ul>
 *
 * <h3>Future Enhancements (Post-MVP)</h3>
 * <p>The following improvements are planned for future versions:
 * <ul>
 *   <li>Async logging via message queue (Kafka) for better throughput</li>
 *   <li>Batch writing for high-volume scenarios</li>
 *   <li>Event streaming to SIEM systems</li>
 *   <li>Configurable retention policies</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see AuditService
 * @see AuditLog
 * @see AuditEvent
 */
@Service
public class AuditServiceImpl implements AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditServiceImpl.class);

    private final AuditLogRepository auditLogRepository;
    private final IdGenerator idGenerator;

    /**
     * Constructs a new AuditServiceImpl with required dependencies.
     *
     * @param auditLogRepository repository for audit log persistence
     * @param idGenerator generator for Snowflake IDs
     */
    public AuditServiceImpl(AuditLogRepository auditLogRepository, IdGenerator idGenerator) {
        this.auditLogRepository = auditLogRepository;
        this.idGenerator = idGenerator;
    }

    /**
     * Records an audit event to the database.
     *
     * <h4>Implementation Details</h4>
     * <ol>
     *   <li>Validates that the event and required fields are not null</li>
     *   <li>Generates a Snowflake ID for the audit log entry</li>
     *   <li>Converts the AuditEvent DTO to AuditLog entity</li>
     *   <li>Persists the entity within a new transaction</li>
     *   <li>Returns the persisted entity with generated ID and timestamp</li>
     * </ol>
     *
     * <h4>Transaction Behavior</h4>
     * <p>This method uses REQUIRES_NEW propagation. This means:
     * <ul>
     *   <li>A new transaction is started regardless of existing transaction state</li>
     *   <li>The audit log is committed independently</li>
     *   <li>If the caller's transaction rolls back, the audit log is still persisted</li>
     * </ul>
     *
     * <h4>Validation Rules</h4>
     * <ul>
     *   <li>event: Must not be null</li>
     *   <li>action: Must not be empty</li>
     *   <li>resourceType: Must not be empty</li>
     * </ul>
     *
     * @param event the audit event to log
     * @return the persisted audit log entity
     * @throws IllegalArgumentException if validation fails
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @CrossTenantAccess(reason = "Audit logging is a cross-cutting platform concern: "
            + "events may originate from tenant-scoped business flows (tenantId set) "
            + "or from platform-level flows such as super-admin login (tenantId null). "
            + "Hibernate save() with assigned Snowflake IDs issues a SELECT-then-INSERT "
            + "that the TenantSqlGuardInterceptor must permit on biz_audit_log.",
            approval = "BRIX-ARCH-3.0.9-AUDIT-CROSS-TENANT")
    public AuditLog log(AuditEvent event) {
        // =====================================================================
        // Validation Phase - Ensure required fields are present
        // =====================================================================
        Assert.notNull(event, "AuditEvent cannot be null");
        Assert.hasText(event.getAction(), "Action is required for audit logging");
        Assert.hasText(event.getResourceType(), "ResourceType is required for audit logging");
        AuditSensitiveDataGuard.assertSafe("description", event.getDescription());
        AuditSensitiveDataGuard.assertSafe("context", event.getContext());
        AuditSensitiveDataGuard.assertSafe("errorMessage", event.getErrorMessage());

        log.debug("Recording audit event: action={}, resourceType={}, resourceId={}",
                event.getAction(), event.getResourceType(),
                AuditSensitiveDataGuard.scrubForLog(event.getResourceId()));

        // =====================================================================
        // Entity Construction Phase - Map DTO to entity
        // =====================================================================
        AuditLog auditLog = new AuditLog();
        
        // Generate unique ID using Snowflake algorithm
        auditLog.setId(idGenerator.nextId());

        // Map actor context fields. For pre-authentication audit events
        // (e.g. failed super-admin login where the loginId could not be
        // resolved to an identity) the caller may leave createdBy null;
        // fall back to AuditLog.ANONYMOUS_ACTOR_ID to satisfy the
        // NOT-NULL constraint while preserving an honest record.
        Long createdBy = event.getCreatedBy();
        auditLog.setCreatedBy(createdBy != null ? createdBy : AuditLog.ANONYMOUS_ACTOR_ID);
        auditLog.setTenantId(event.getTenantId());
        auditLog.setOwnerMemberId(event.getOwnerMemberId());
        auditLog.setOwnerOrgId(event.getOwnerOrgId());

        // Map action description fields
        auditLog.setAction(event.getAction());
        auditLog.setResourceType(event.getResourceType());
        auditLog.setResourceId(event.getResourceId());
        auditLog.setDescription(event.getDescription());

        // Map request context fields
        auditLog.setClientIp(event.getClientIp());
        auditLog.setUserAgent(event.getUserAgent());
        auditLog.setRequestId(event.getRequestId());
        auditLog.setContext(event.getContext());

        // Map result fields
        auditLog.setSuccess(event.isSuccess());
        auditLog.setErrorMessage(event.getErrorMessage());
        auditLog.setErrorCode(event.getErrorCode());

        // =====================================================================
        // Persistence Phase - Save to database
        // Note: createdAt is set automatically by @PrePersist callback
        // =====================================================================
        AuditLog savedLog = auditLogRepository.save(auditLog);

        log.debug("Audit event recorded: id={}, action={}, resourceType={}, success={}",
                savedLog.getId(), savedLog.getAction(), savedLog.getResourceType(), savedLog.isSuccess());

        return savedLog;
    }
}
