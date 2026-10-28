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
package io.brix.platform.admin.handler;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.stream.Collectors;

import io.brix.platform.admin.dto.PlatformAuditLogDto;
import io.brix.platform.admin.dto.PlatformPageDto;
import io.brix.platform.audit.internal.PlatformAuditAdministration;
import io.brix.platform.audit.internal.PlatformAuditEntryView;
import io.brix.platform.audit.internal.PlatformAuditPage;
import io.brix.platform.audit.internal.PlatformAuditQuery;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointHandlingException;
import io.runtime.sdk.plugin.EndpointInvocation;

/**
 * The platform console's audit query (super-admin §6: {@code GET /api/platform/audit-logs},
 * {@code platform:audit:read}).
 *
 * <p>The handler turns the console's parameters into the platform audit owner's restricted query and
 * maps the page back to the fields the console shows. Records are always newest first, so
 * {@code sort} is not interpreted; the owner cannot filter by tenant, so {@code tenantId} is refused.
 * The actor is shown by identity id only (platform audit design §14.2 D8).</p>
 */
public final class ListPlatformAuditLogsHandler
        implements EndpointHandler<EndpointInvocation<Void>, PlatformPageDto<PlatformAuditLogDto>> {

    /** Stable error of a query the owner cannot answer. */
    static final String QUERY_INVALID = "PLATFORM_AUDIT_QUERY_INVALID";

    private static final int DEFAULT_SIZE = 20;

    private final PlatformAuditAdministration audit;

    public ListPlatformAuditLogsHandler(OperationalContext context) {
        this(context.requireInternalContract(PlatformAuditAdministration.class));
    }

    ListPlatformAuditLogsHandler(PlatformAuditAdministration audit) {
        this.audit = audit;
    }

    @Override
    public PlatformPageDto<PlatformAuditLogDto> handle(EndpointInvocation<Void> invocation) {
        PlatformOperationalInvocationSupport.requirePlatformActorId(invocation);
        PlatformAuditPage page = audit.query(query(invocation));
        int totalPages = (int) Math.ceil((double) page.totalElements() / page.size());
        return new PlatformPageDto<>(
            page.entries().stream().map(ListPlatformAuditLogsHandler::toDto).toList(),
            page.page(),
            page.size(),
            page.totalElements(),
            totalPages,
            page.page() == 0,
            page.page() >= totalPages - 1);
    }

    private static PlatformAuditQuery query(EndpointInvocation<?> invocation) {
        if (PlatformReadQuerySupport.firstQuery(invocation, "tenantId", null) != null) {
            throw invalid("the platform audit cannot be filtered by tenant");
        }
        try {
            return new PlatformAuditQuery(
                PlatformReadQuerySupport.intQuery(invocation, "page", 0),
                Math.min(PlatformAuditQuery.MAX_SIZE, PlatformReadQuerySupport.intQuery(invocation, "size", DEFAULT_SIZE)),
                PlatformReadQuerySupport.firstQuery(invocation, "action", null),
                actor(PlatformReadQuerySupport.firstQuery(invocation, "actorIdentityId", null)),
                instant(PlatformReadQuerySupport.firstQuery(invocation, "fromTime", null)),
                instant(PlatformReadQuerySupport.firstQuery(invocation, "toTime", null)),
                result(PlatformReadQuerySupport.firstQuery(invocation, "result", null)));
        } catch (IllegalArgumentException rejected) {
            // Includes PlatformAuditRejectedException, the owner's refusal of a query it cannot answer.
            throw invalid(rejected.getMessage());
        }
    }

    private static Long actor(String value) {
        return value == null ? null : Long.valueOf(value);
    }

    private static Boolean result(String value) {
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "SUCCESS" -> true;
            case "FAILURE" -> false;
            default -> throw new IllegalArgumentException("result must be SUCCESS or FAILURE");
        };
    }

    private static Instant instant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException notAnInstant) {
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (DateTimeParseException invalid) {
                throw new IllegalArgumentException("times must be ISO-8601 with an offset", invalid);
            }
        }
    }

    private static EndpointHandlingException invalid(String message) {
        return new EndpointHandlingException(400, QUERY_INVALID, message);
    }

    static PlatformAuditLogDto toDto(PlatformAuditEntryView entry) {
        return new PlatformAuditLogDto(
            entry.id(),
            entry.actorIdentityId(),
            null,
            entry.action(),
            entry.targetType(),
            entry.targetId(),
            entry.affectedTenants().isEmpty()
                ? null
                : entry.affectedTenants().stream().map(String::valueOf).collect(Collectors.joining(",")),
            entry.sourceIp(),
            entry.userAgent(),
            entry.success() ? "SUCCESS" : "FAILURE",
            entry.reasonCode(),
            entry.traceId(),
            entry.createdAt() == null ? null : entry.createdAt().atOffset(ZoneOffset.UTC),
            entry.errorCode(),
            entry.executionIdentity(),
            entry.writer());
    }
}
