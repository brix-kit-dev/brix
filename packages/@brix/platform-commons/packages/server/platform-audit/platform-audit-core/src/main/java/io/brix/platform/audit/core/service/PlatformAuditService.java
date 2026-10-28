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
package io.brix.platform.audit.core.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import io.brix.platform.audit.api.PlatformAuditActions;
import io.brix.platform.audit.api.PlatformAuditWriters;
import io.brix.platform.audit.core.entity.PlatformAuditLog;
import io.brix.platform.audit.core.repository.PlatformAuditLogRepository;
import io.brix.platform.audit.internal.PlatformAuditAdministration;
import io.brix.platform.audit.internal.PlatformAuditPage;
import io.brix.platform.audit.internal.PlatformAuditQuery;
import io.brix.platform.audit.internal.PlatformAuditRecordId;
import io.brix.platform.audit.internal.PlatformAuditRejectedException;
import io.brix.platform.audit.internal.RecordPlatformAuditCommand;
import io.runtime.sdk.security.RequestMetadataMasking;

/**
 * The platform audit owner's write rules and restricted query (super-admin §5, AUDIT-001).
 *
 * <p>Every field is checked against a closed pattern, so free text, credentials, full e-mail
 * addresses and URLs cannot get in. The client address keeps its network prefix only. A refused
 * record fails with a stable code; nothing is swallowed. The runtime calls this inside the owner's
 * own unit of work, after suspending the caller's transaction.</p>
 *
 * @since 3.2.0
 */
public final class PlatformAuditService implements PlatformAuditAdministration {

    private static final Pattern EXECUTION_IDENTITY = Pattern.compile("[a-z][a-z0-9_.:-]{0,127}");
    private static final Pattern RESOURCE_TYPE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern RESOURCE_ID = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");
    private static final Pattern STABLE_CODE = Pattern.compile("[a-z][a-z0-9_.-]{0,63}");
    private static final Pattern TRACE_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");

    private final PlatformAuditLogRepository records;

    public PlatformAuditService(PlatformAuditLogRepository records) {
        this.records = Objects.requireNonNull(records, "records must not be null");
    }

    @Override
    public PlatformAuditRecordId record(RecordPlatformAuditCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.action() == null || !PlatformAuditActions.OWNER_RECORDED.contains(command.action())) {
            throw new PlatformAuditRejectedException(PlatformAuditRejectedException.ACTION_REFUSED,
                "The platform audit owner does not record action " + command.action());
        }
        required(EXECUTION_IDENTITY, command.executionIdentity(), "executionIdentity");
        required(RESOURCE_TYPE, command.targetType(), "targetType");
        optional(RESOURCE_ID, command.targetId(), "targetId");
        optional(STABLE_CODE, command.reasonCode(), "reasonCode");
        optional(STABLE_CODE, command.errorCode(), "errorCode");
        required(TRACE_ID, command.traceId(), "traceId");
        if (command.affectedTenants().stream().anyMatch(tenant -> tenant == null || tenant <= 0)) {
            throw invalid("affectedTenants must hold tenant ids only");
        }
        PlatformAuditLog row = PlatformAuditLog.ownerRecord(
            command.action(),
            command.actorIdentityId(),
            command.executionIdentity(),
            command.targetType(),
            command.targetId(),
            command.affectedTenants(),
            command.success(),
            command.reasonCode(),
            command.errorCode(),
            command.traceId(),
            RequestMetadataMasking.maskAddress(command.sourceIp()),
            RequestMetadataMasking.truncateUserAgent(command.userAgent()),
            PlatformAuditWriters.OWNER);
        return new PlatformAuditRecordId(records.save(row).getId());
    }

    @Override
    public PlatformAuditPage query(PlatformAuditQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        Specification<PlatformAuditLog> filter = (root, criteria, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (query.action() != null) {
                predicates.add(builder.equal(root.get("action"), query.action()));
            }
            if (query.actorIdentityId() != null) {
                predicates.add(builder.equal(root.get("operatorIdentityId"), query.actorIdentityId()));
            }
            if (query.from() != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), query.from()));
            }
            if (query.to() != null) {
                predicates.add(builder.lessThan(root.get("createdAt"), query.to()));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        Page<PlatformAuditLog> page = records.findAll(filter,
            PageRequest.of(query.page(), query.size(), Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        return new PlatformAuditPage(
            page.getContent().stream().map(PlatformAuditLog::toView).toList(),
            page.getTotalElements(), query.page(), query.size());
    }

    private static void required(Pattern pattern, String value, String field) {
        if (value == null || !pattern.matcher(value).matches()) {
            throw invalid(field + " is missing or malformed");
        }
    }

    private static void optional(Pattern pattern, String value, String field) {
        if (value != null && !pattern.matcher(value).matches()) {
            throw invalid(field + " is malformed");
        }
    }

    private static PlatformAuditRejectedException invalid(String message) {
        return new PlatformAuditRejectedException(PlatformAuditRejectedException.FIELD_INVALID, message);
    }
}
