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
package io.brix.platform.audit.core.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.brix.platform.audit.internal.PlatformAuditEntryView;

/**
 * A row of {@code sys_platform_audit_log}, the table the platform audit Data Owner holds
 * (super-admin §1.3a, §5). Rows are append-only: every column is insert-only.
 *
 * <p>Ids come from the owner's sequence, which is also the column default the runtime custodian
 * relies on. The free-text {@code description} and the custodial {@code context} columns are not
 * mapped: the owner writes neither and does not expose them.</p>
 *
 * @since 3.2.0
 */
@Entity(name = "PlatformAuditLog")
@Table(name = "sys_platform_audit_log")
public class PlatformAuditLog {

    @Id
    @SequenceGenerator(name = "sys_platform_audit_log_id", sequenceName = "sys_platform_audit_log_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "sys_platform_audit_log_id")
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "operator_identity_id", updatable = false)
    private Long operatorIdentityId;

    @Column(name = "action", nullable = false, length = 64, updatable = false)
    private String action;

    @Column(name = "resource_type", nullable = false, length = 64, updatable = false)
    private String resourceType;

    @Column(name = "resource_id", length = 128, updatable = false)
    private String resourceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "affected_tenants", nullable = false, updatable = false)
    private String affectedTenants;

    @Column(name = "success", nullable = false, updatable = false)
    private boolean success;

    @Column(name = "error_code", length = 64, updatable = false)
    private String errorCode;

    @Column(name = "execution_identity", length = 128, updatable = false)
    private String executionIdentity;

    @Column(name = "reason_code", length = 64, updatable = false)
    private String reasonCode;

    @Column(name = "trace_id", length = 64, updatable = false)
    private String traceId;

    @Column(name = "source_ip", length = 64, updatable = false)
    private String sourceIp;

    @Column(name = "user_agent", length = 256, updatable = false)
    private String userAgent;

    @Column(name = "writer", nullable = false, length = 64, updatable = false)
    private String writer;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PlatformAuditLog() {
    }

    /**
     * Creates a record the owner writes itself.
     */
    public static PlatformAuditLog ownerRecord(
            String action,
            Long operatorIdentityId,
            String executionIdentity,
            String resourceType,
            String resourceId,
            List<Long> affectedTenants,
            boolean success,
            String reasonCode,
            String errorCode,
            String traceId,
            String sourceIp,
            String userAgent,
            String writer) {
        PlatformAuditLog row = new PlatformAuditLog();
        row.action = Objects.requireNonNull(action, "action must not be null");
        row.operatorIdentityId = operatorIdentityId;
        row.executionIdentity = executionIdentity;
        row.resourceType = Objects.requireNonNull(resourceType, "resourceType must not be null");
        row.resourceId = resourceId;
        row.affectedTenants = affectedTenants.stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]"));
        row.success = success;
        row.reasonCode = reasonCode;
        row.errorCode = errorCode;
        row.traceId = traceId;
        row.sourceIp = sourceIp;
        row.userAgent = userAgent;
        row.writer = Objects.requireNonNull(writer, "writer must not be null");
        return row;
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    /**
     * Maps the row to the contract view; fields older rows never recorded stay empty.
     *
     * @return the view
     */
    public PlatformAuditEntryView toView() {
        return new PlatformAuditEntryView(
            id, action, operatorIdentityId, executionIdentity, resourceType, resourceId,
            tenantIds(affectedTenants), success, reasonCode, errorCode, traceId, sourceIp, userAgent,
            writer, createdAt);
    }

    static List<Long> tenantIds(String json) {
        List<Long> ids = new ArrayList<>();
        if (json == null) {
            return ids;
        }
        String body = json.trim();
        if (body.startsWith("[")) {
            body = body.substring(1);
        }
        if (body.endsWith("]")) {
            body = body.substring(0, body.length() - 1);
        }
        for (String part : body.split(",")) {
            String value = part.trim();
            if (!value.isEmpty()) {
                ids.add(Long.valueOf(value));
            }
        }
        return ids;
    }
}
