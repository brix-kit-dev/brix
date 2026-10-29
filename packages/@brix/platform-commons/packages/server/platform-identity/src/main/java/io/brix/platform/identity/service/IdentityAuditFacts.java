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
package io.brix.platform.identity.service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.slf4j.MDC;

import io.brix.platform.identity.event.IdentityAuditFact;
import io.brix.platform.identity.event.IdentityAuditFactRecordedEvent;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.security.RequestMetadataMasking;

/**
 * Publishes the identity owner's platform audit facts (P4 design §4.2, D14).
 *
 * <p>Each fact is an {@code IdentityAuditFactRecorded} event written to {@code platform_identity_outbox}
 * in the identity unit of work that makes the change, so the fact commits or rolls back with the change.
 * The platform audit owner records it after delivery (D15); platform-identity does not write
 * {@code sys_platform_audit_log}. Publishing outside an identity unit of work fails at once (root
 * BRX-RS-MESSAGE-022).</p>
 *
 * <p>Fields are typed (super-admin §5, BRX-SA-AUDIT-001): no description, e-mail address or token. The
 * client address leaves as its masked network prefix. The trace id is the invocation's, as the trace
 * filter put it in the log context; without one the platform audit owner takes the delivery's trace.</p>
 */
public final class IdentityAuditFacts {

    /** Execution identity of every identity audit fact. */
    public static final String EXECUTION_IDENTITY = "platform-identity";

    /** Stable reason: the platform bootstrap with the configured setup code. */
    public static final String REASON_BOOTSTRAP = "bootstrap";

    /** Stable reason: the first formal platform super administrator completed setup and closed the bootstrap. */
    public static final String REASON_BOOTSTRAP_COMPLETED = "bootstrap-completed";

    /** Stable reason: repeated platform login failures locked the identity. */
    public static final String REASON_LOGIN_FAILURES = "login-failures-exceeded";

    private static final String MDC_TRACE_ID = "traceId";
    private static final Pattern TRACE_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");

    private final EventBusCapability outbox;

    /**
     * Creates the publisher.
     *
     * @param outbox the identity owner's outbox event bus
     */
    public IdentityAuditFacts(EventBusCapability outbox) {
        this.outbox = Objects.requireNonNull(outbox, "outbox must not be null");
    }

    /**
     * The stable reason of a setup step: the setup token's purpose, for example {@code initial-setup}.
     *
     * @param setupPurpose setup token purpose
     * @return the reason code
     */
    public static String setupReason(String setupPurpose) {
        return setupPurpose.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    /**
     * Publishes a successful change in the current identity unit of work.
     *
     * @param action audit action id
     * @param actorIdentityId authenticated actor, or {@code null} for an anonymous bootstrap request
     * @param targetType type of the affected resource
     * @param targetId id of the affected resource, or {@code null}
     * @param reasonCode stable reason
     */
    public void record(String action, Long actorIdentityId, String targetType, String targetId, String reasonCode) {
        record(action, actorIdentityId, targetType, targetId, reasonCode, null);
    }

    /**
     * Publishes a successful change in the current identity unit of work, with the client address.
     *
     * @param action audit action id
     * @param actorIdentityId authenticated actor, or {@code null} for an anonymous bootstrap request
     * @param targetType type of the affected resource
     * @param targetId id of the affected resource, or {@code null}
     * @param reasonCode stable reason
     * @param clientAddress client address of the request; only its masked network prefix is published
     */
    public void record(
            String action,
            Long actorIdentityId,
            String targetType,
            String targetId,
            String reasonCode,
            String clientAddress) {
        String traceId = currentTraceId();
        IdentityAuditFactRecordedEvent event = new IdentityAuditFactRecordedEvent(new IdentityAuditFact(
            action,
            actorIdentityId,
            EXECUTION_IDENTITY,
            targetType,
            targetId,
            List.of(),
            true,
            Objects.requireNonNull(reasonCode, "reasonCode must not be null"),
            null,
            traceId,
            RequestMetadataMasking.maskAddress(clientAddress)));
        if (traceId != null) {
            event.withTraceId(traceId);
        }
        outbox.publishIntegration(event);
    }

    private static String currentTraceId() {
        String traceId = MDC.get(MDC_TRACE_ID);
        return traceId != null && TRACE_ID.matcher(traceId).matches() ? traceId : null;
    }
}
