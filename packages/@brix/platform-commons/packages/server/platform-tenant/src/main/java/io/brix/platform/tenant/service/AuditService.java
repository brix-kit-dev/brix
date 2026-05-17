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

import io.brix.platform.tenant.dto.AuditEvent;
import io.brix.platform.tenant.entity.AuditLog;

/**
 * Service interface for audit logging operations.
 *
 * <p>This service provides the contract for recording audit events in the system.
 * All sensitive operations should be logged through this service for compliance
 * and security monitoring purposes.
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Implementation Layer - Platform Commons Capability Implementation</p>
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li><b>Immutability:</b> Audit logs are append-only, never updated or deleted via application</li>
 *   <li><b>Completeness:</b> Each log contains full context (who, what, when, where)</li>
 *   <li><b>Reliability:</b> Logging should not fail silently; errors are propagated</li>
 *   <li><b>Performance:</b> Synchronous logging is suitable for MVP; async can be added later</li>
 * </ul>
 *
 * <h3>Event Categories</h3>
 * <p>Common event types that should be logged:
 * <ul>
 *   <li><b>AUTHENTICATION:</b> Login, logout, password changes</li>
 *   <li><b>AUTHORIZATION:</b> Permission changes, role assignments</li>
 *   <li><b>DATA_ACCESS:</b> Reading sensitive data</li>
 *   <li><b>DATA_MUTATION:</b> Create, update, delete operations</li>
 *   <li><b>CONFIGURATION:</b> System/tenant configuration changes</li>
 *   <li><b>SECURITY:</b> Security events, policy violations</li>
 * </ul>
 *
 * <h3>MVP Scope Boundaries</h3>
 * <p>The following features are explicitly OUT OF SCOPE for MVP:
 * <ul>
 *   <li>Audit log query API (read-only access deferred)</li>
 *   <li>Async logging via message queue (Kafka, RabbitMQ)</li>
 *   <li>Log retention policy implementation</li>
 *   <li>Export/archive functionality</li>
 *   <li>Real-time alerting on audit events</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * @Service
 * public class TenantOnboardingService {
 *     private final TenantProvisioningService provisioningService;
 *     private final AuditService auditService;
 *
 *     @Transactional
 *     public Tenant createTenant(CreateTenantRequest request, Long operatorId) {
 *         Tenant tenant = provisioningService.createTenant(request);
 *
 *         // Log the tenant creation event
 *         AuditEvent event = AuditEvent.builder()
 *             .createdBy(operatorId)
 *             .action(AuditEvent.ACTION_CREATE)
 *             .resourceType("TENANT")
 *             .resourceId(tenant.getId().toString())
 *             .description("Created tenant: " + tenant.getCode())
 *             .build();
 *
 *         auditService.log(event);
 *         return tenant;
 *     }
 * }
 * }</pre>
 *
 * <h3>Transaction Considerations</h3>
 * <p>The {@link #log} method uses REQUIRES_NEW propagation to ensure audit logs
 * are persisted even if the encompassing transaction rolls back. This is critical
 * for compliance - failed operations should still be recorded.
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see AuditEvent
 * @see AuditLog
 */
public interface AuditService {

    /**
     * Records an audit event to the audit log.
     *
     * <p>This method persists the provided audit event to the biz_audit_log table.
     * The operation is synchronous and uses a separate transaction to ensure
     * the log is not rolled back with the calling transaction.
     *
     * <h4>Required Fields</h4>
     * <ul>
     *   <li>{@code action} - The action performed (e.g., CREATE, UPDATE, DELETE)</li>
     *   <li>{@code resourceType} - Type of resource being acted upon</li>
     * </ul>
     *
     * <h4>Recommended Fields</h4>
     * <ul>
     *   <li>{@code createdBy} - Identity who performed the action (null only for failed logins)</li>
     *   <li>{@code tenantId} - Tenant context (null for system-level events)</li>
     *   <li>{@code description} - Human-readable description</li>
     *   <li>{@code clientIp} - Source IP address</li>
     * </ul>
     *
     * <h4>Transaction Behavior</h4>
     * <p>Uses REQUIRES_NEW propagation:
     * <ul>
     *   <li>Creates a new transaction for the log operation</li>
     *   <li>Audit log persists even if caller's transaction rolls back</li>
     *   <li>Ensures failed operations are recorded for security analysis</li>
     * </ul>
     *
     * <h4>Error Handling</h4>
     * <p>If logging fails (e.g., database error), the exception is propagated to the caller.
     * The calling code should decide whether to proceed without logging or fail the operation.
     *
     * @param event the audit event to log
     * @return the persisted audit log entity with generated ID
     * @throws IllegalArgumentException if event is null or required fields are missing
     * @throws org.springframework.dao.DataAccessException if database operation fails
     */
    AuditLog log(AuditEvent event);
}
