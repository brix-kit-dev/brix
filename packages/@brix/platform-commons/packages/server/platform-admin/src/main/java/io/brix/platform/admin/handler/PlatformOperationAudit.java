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

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.brix.platform.audit.internal.PlatformAuditAdministration;
import io.brix.platform.audit.internal.RecordPlatformAuditCommand;
import io.brix.platform.tenant.internal.TenantAdministrationException;
import io.runtime.orchestrator.operational.OperationalContext;
import io.runtime.sdk.error.BrixException;
import io.runtime.sdk.plugin.EndpointHandlingException;
import io.runtime.sdk.plugin.EndpointInvocation;

/**
 * Writes platform-admin's own writes to the platform audit once the target owner has returned
 * (super-admin §5, multi-tenant §6.4 row 4 and T-3; platform audit design §14.2 D7).
 *
 * <p>The target owner commits in its own transaction and the record commits in the platform audit
 * owner's; the runtime never lets the two share one (root HOST-020, DATA-029). A failed operation is
 * recorded as a failure with its stable code and its own error is returned. When the record itself
 * cannot be written the request fails with {@value #WRITE_FAILED}, while the owner's change stays; the
 * persistence adapter counts that failure for the platform audit owner. Nothing is swallowed.</p>
 */
final class PlatformOperationAudit {

    /** Stable error of a request whose operation succeeded but could not be recorded. */
    static final String WRITE_FAILED = "PLATFORM_AUDIT_WRITE_FAILED";

    /** Execution identity of every record platform-admin writes. */
    static final String EXECUTION_IDENTITY = "platform-admin";

    /** Stable reason: a platform administrator's targeted operation (multi-tenant §6.4 row 4). */
    static final String REASON = "platform-operation";

    private static final String OPERATION_FAILED = "platform.operation_failed";
    private static final Pattern TRACE_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");
    private static final Logger log = LoggerFactory.getLogger(PlatformOperationAudit.class);

    private final PlatformAuditAdministration audit;

    PlatformOperationAudit(OperationalContext context) {
        this(context.requireInternalContract(PlatformAuditAdministration.class));
    }

    PlatformOperationAudit(PlatformAuditAdministration audit) {
        this.audit = Objects.requireNonNull(audit, "audit must not be null");
    }

    /**
     * What an operation acts on.
     *
     * @param type audit target type, such as {@code TENANT} or {@code INVITATION}
     * @param id target id, or {@code null} while it does not exist yet
     * @param tenantId tenant the operation reaches, or {@code null} while it does not exist yet
     */
    record Target(String type, Object id, Long tenantId) {
    }

    /**
     * Runs one operation and records it.
     *
     * @param invocation the request, for its trace id
     * @param actorId platform administrator who asked for it
     * @param action audit action id
     * @param before target as known before the operation, recorded when it fails
     * @param after target as the result shows it, recorded when it succeeds
     * @param operation the call to the target owner
     * @param <T> result type
     * @return the operation's result
     */
    <T> T run(
            EndpointInvocation<?> invocation,
            Long actorId,
            String action,
            Target before,
            Function<? super T, Target> after,
            Supplier<T> operation) {
        String traceId = traceId(invocation);
        T result;
        try {
            result = operation.get();
        } catch (RuntimeException failure) {
            try {
                audit.record(command(action, actorId, before, false, errorCode(failure), traceId));
            } catch (RuntimeException auditFailure) {
                log.error("event=platform.audit.operation_write_failed action={} outcome=failure trace={}",
                    action, traceId, auditFailure);
                failure.addSuppressed(auditFailure);
            }
            throw failure;
        }
        try {
            audit.record(command(action, actorId, after.apply(result), true, null, traceId));
        } catch (RuntimeException auditFailure) {
            log.error("event=platform.audit.operation_write_failed action={} outcome=success trace={}",
                action, traceId, auditFailure);
            throw new EndpointHandlingException(503, WRITE_FAILED,
                "the operation completed but its platform audit record could not be written");
        }
        return result;
    }

    private static RecordPlatformAuditCommand command(
            String action, Long actorId, Target target, boolean success, String errorCode, String traceId) {
        return new RecordPlatformAuditCommand(
            action,
            actorId,
            EXECUTION_IDENTITY,
            target.type(),
            target.id() == null ? null : String.valueOf(target.id()),
            target.tenantId() == null ? List.of() : List.of(target.tenantId()),
            success,
            REASON,
            errorCode,
            traceId,
            null,
            null);
    }

    /** The request's trace id, or a correlation id made for this record (root ERROR-013). */
    private static String traceId(EndpointInvocation<?> invocation) {
        return invocation.traceId()
            .filter(id -> TRACE_ID.matcher(id).matches())
            .orElseGet(() -> UUID.randomUUID().toString());
    }

    /** The stable code of a failed operation, in the form the audit catalog keeps. */
    static String errorCode(Throwable failure) {
        String code = null;
        if (failure instanceof EndpointHandlingException endpoint) {
            code = endpoint.errorCode();
        } else if (failure instanceof TenantAdministrationException tenant) {
            code = tenant.code();
        } else if (failure instanceof BrixException brix && brix.errorCode() != null) {
            code = brix.errorCode().wireCode();
        }
        if (code == null || code.isBlank()) {
            return OPERATION_FAILED;
        }
        String lower = code.toLowerCase(Locale.ROOT);
        return lower.matches("[a-z][a-z0-9_.-]{0,63}") ? lower : OPERATION_FAILED;
    }
}
