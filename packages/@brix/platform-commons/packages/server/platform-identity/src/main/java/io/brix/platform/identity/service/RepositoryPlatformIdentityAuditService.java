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
package io.brix.platform.identity.service;

import java.util.Objects;

import io.brix.platform.identity.core.IdGenerator;
import io.brix.platform.identity.dto.AuditEvent;
import io.brix.platform.identity.entity.PlatformAuditLogEntry;
import io.brix.platform.identity.repository.PlatformAuditLogRepository;

/**
 * Writes platform audit entries in the identity owner's current unit of work.
 *
 * <p>An entry commits or rolls back together with the change it records, so the trail never
 * reports a change that was rolled back. The platform offers no independent nested transaction
 * (root blueprint BRX-RS-DATA-029); rejected requests are recorded by the runtime's error boundary
 * and security log instead of a row written by the failing unit of work.</p>
 */
public class RepositoryPlatformIdentityAuditService implements AuditService {

    private final PlatformAuditLogRepository auditLogRepository;
    private final IdGenerator idGenerator;

    /**
     * Creates the audit service.
     *
     * @param auditLogRepository identity-owned audit trail repository
     * @param idGenerator application-side identifier generator
     */
    public RepositoryPlatformIdentityAuditService(
            PlatformAuditLogRepository auditLogRepository,
            IdGenerator idGenerator) {
        this.auditLogRepository = Objects.requireNonNull(auditLogRepository, "auditLogRepository must not be null");
        this.idGenerator = Objects.requireNonNull(idGenerator, "idGenerator must not be null");
    }

    @Override
    public void log(AuditEvent event) {
        Objects.requireNonNull(event, "AuditEvent cannot be null");
        requireText(event.getAction(), "Audit action is required");
        requireText(event.getResourceType(), "Audit resourceType is required");
        auditLogRepository.save(new PlatformAuditLogEntry(
            idGenerator.nextId(),
            event.getCreatedBy(),
            event.getAction(),
            event.getResourceType(),
            event.getResourceId(),
            event.getDescription(),
            event.isSuccess()));
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
