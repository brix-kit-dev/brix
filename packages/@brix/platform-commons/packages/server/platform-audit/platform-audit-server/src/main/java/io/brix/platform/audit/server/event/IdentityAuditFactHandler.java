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
package io.brix.platform.audit.server.event;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import io.brix.platform.audit.core.service.PlatformAuditService;
import io.brix.platform.audit.internal.RecordPlatformAuditCommand;
import io.runtime.sdk.event.EventEnvelope;
import io.runtime.sdk.plugin.EventHandler;
import io.runtime.sdk.plugin.EventInvocation;

/**
 * Records platform-identity's audit facts (P4 design §4.2, D14, D15).
 *
 * <p>Bound for the manifest subscription {@code identity-audit-fact}. The runtime records
 * {@code (handlerId, eventId)} in {@code platform_audit_inbox} and calls this handler in the same
 * platform-audit unit of work, so a redelivered fact never becomes a second row. The row is written by
 * the owner ({@code writer = platform-audit}) and keeps the time the fact occurred. A fact the owner
 * refuses fails the delivery; the relay retries it and then parks it, where it is counted and alerted.</p>
 */
public final class IdentityAuditFactHandler implements EventHandler<IdentityAuditFactPayload> {

    private final PlatformAuditService audit;

    public IdentityAuditFactHandler(PlatformAuditService audit) {
        this.audit = Objects.requireNonNull(audit, "audit must not be null");
    }

    @Override
    public CompletionStage<Void> handle(EventInvocation<IdentityAuditFactPayload> invocation) {
        EventEnvelope<IdentityAuditFactPayload> event = invocation.event();
        IdentityAuditFactPayload fact = event.payload();
        audit.recordIdentityFact(new RecordPlatformAuditCommand(
            fact.action(),
            fact.actorIdentityId(),
            fact.executionIdentity(),
            fact.targetType(),
            fact.targetId(),
            fact.affectedTenants(),
            fact.success(),
            fact.reasonCode(),
            fact.errorCode(),
            fact.traceId() != null ? fact.traceId() : traceIdOf(invocation.trace().traceparent()),
            fact.sourceIp(),
            null), event.occurredAt());
        return CompletableFuture.completedFuture(null);
    }

    /**
     * The trace id of a W3C {@code traceparent} ({@code 00-<trace-id>-<parent-id>-<flags>}): the trace the
     * runtime delivers the event in, which continues the publisher's when the publisher had one.
     */
    static String traceIdOf(String traceparent) {
        String[] parts = traceparent.split("-");
        return parts.length == 4 ? parts[1] : null;
    }
}
