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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
import io.runtime.sdk.capability.IdGeneratorCapability;
import io.runtime.sdk.security.RequestMetadataMasking;

/**
 * The platform audit owner's write rules and restricted query (super-admin §5, AUDIT-001).
 *
 * <p>Every field is checked against a closed pattern, so free text, credentials, full e-mail
 * addresses and URLs cannot get in. The client address keeps its network prefix only. A refused
 * record fails with a stable code; nothing is swallowed. The runtime calls this inside the owner's
 * own unit of work, after suspending the caller's transaction.</p>
 *
 * <p>Ids come from {@link IdGeneratorCapability} (root BRX-RS-DATA-024). The table's id is a BIGINT,
 * so the Host's generator must produce numeric ids.</p>
 *
 * <p>platform-identity's audit facts arrive as {@code IdentityAuditFactRecorded} events through the
 * owner's inbox (P4 design §4.2, D14, D15) and follow the same rules; the record keeps the time the fact
 * occurred, so a late or replayed delivery still sorts where the fact happened.</p>
 *
 * @since 3.2.0
 */
public final class PlatformAuditService implements PlatformAuditAdministration {

    /**
     * Actions platform-identity records as identity audit facts: authentication and the identity lifecycle
     * (identity-auth BRX-IA-AUDIT-001, super-admin §5). Platform administrators' operations come through
     * the internal contract, and policy uses only from the runtime custodian.
     */
    public static final Set<String> IDENTITY_FACTS = Set.of(
        PlatformAuditActions.SUPER_ADMIN_LOGIN_SUCCESS, PlatformAuditActions.SUPER_ADMIN_LOGIN_FAILED,
        PlatformAuditActions.SUPER_ADMIN_LOGOUT, PlatformAuditActions.SUPER_ADMIN_PASSWORD_CHANGED,
        PlatformAuditActions.BOOTSTRAP_SESSION_OPENED, PlatformAuditActions.BOOTSTRAP_ADMIN_CREATED,
        PlatformAuditActions.BOOTSTRAP_ADMIN_DEACTIVATED,
        PlatformAuditActions.SETUP_TOKEN_ISSUED, PlatformAuditActions.SETUP_TOKEN_USED,
        PlatformAuditActions.SETUP_TOKEN_INVALID,
        PlatformAuditActions.IDENTITY_PASSWORD_SET, PlatformAuditActions.TOTP_BOUND,
        PlatformAuditActions.IDENTITY_ACTIVATED, PlatformAuditActions.IDENTITY_DISABLED,
        PlatformAuditActions.IDENTITY_LOCKED);

    private static final Pattern EXECUTION_IDENTITY = Pattern.compile("[a-z][a-z0-9_.:-]{0,127}");
    private static final Pattern RESOURCE_TYPE = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");
    private static final Pattern RESOURCE_ID = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");
    private static final Pattern STABLE_CODE = Pattern.compile("[a-z][a-z0-9_.-]{0,63}");
    private static final Pattern TRACE_ID = Pattern.compile("[A-Za-z0-9-]{8,64}");
    /** A network prefix as {@link RequestMetadataMasking#maskAddress} writes it. */
    private static final Pattern MASKED_ADDRESS =
        Pattern.compile("(?:\\d{1,3}\\.){3}0/24|[0-9a-f]{1,4}:[0-9a-f]{1,4}:[0-9a-f]{1,4}::/48");

    private final PlatformAuditLogRepository records;
    private final IdGeneratorCapability ids;

    public PlatformAuditService(PlatformAuditLogRepository records, IdGeneratorCapability ids) {
        this.records = Objects.requireNonNull(records, "records must not be null");
        this.ids = Objects.requireNonNull(ids, "ids must not be null");
    }

    @Override
    public PlatformAuditRecordId record(RecordPlatformAuditCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.action() == null || !PlatformAuditActions.OWNER_RECORDED.contains(command.action())) {
            throw new PlatformAuditRejectedException(PlatformAuditRejectedException.ACTION_REFUSED,
                "The platform audit owner does not record action " + command.action());
        }
        return append(command, null);
    }

    /**
     * Appends one identity audit fact delivered from platform-identity's outbox (P4 design §4.2, D14, D15).
     * The runtime calls this in the owner's unit of work, together with the inbox receipt of the event.
     *
     * @param fact typed record fields of the fact; the address arrives as its masked network prefix
     * @param occurredAt server time of the fact, from the event envelope
     * @return the record id
     * @throws PlatformAuditRejectedException when the action is not an identity fact or a field breaks the
     *     owner's rules
     */
    public PlatformAuditRecordId recordIdentityFact(RecordPlatformAuditCommand fact, Instant occurredAt) {
        Objects.requireNonNull(fact, "fact must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (fact.action() == null || !IDENTITY_FACTS.contains(fact.action())) {
            throw new PlatformAuditRejectedException(PlatformAuditRejectedException.ACTION_REFUSED,
                "platform-identity does not record action " + fact.action());
        }
        return append(fact, occurredAt);
    }

    private PlatformAuditRecordId append(RecordPlatformAuditCommand command, Instant occurredAt) {
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
            nextId(),
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
            maskedAddress(command.sourceIp()),
            RequestMetadataMasking.truncateUserAgent(command.userAgent()),
            PlatformAuditWriters.OWNER);
        if (occurredAt != null) {
            row.occurredAt(occurredAt);
        }
        return new PlatformAuditRecordId(records.save(row).getId());
    }

    /** The client address as its network prefix; a prefix the caller already masked is kept as it is. */
    private static String maskedAddress(String address) {
        if (address != null && MASKED_ADDRESS.matcher(address).matches()) {
            return address;
        }
        return RequestMetadataMasking.maskAddress(address);
    }

    private long nextId() {
        String id = ids.generate();
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("The platform audit needs numeric ids; the Host's IdGeneratorCapability"
                + " generated a non-numeric one", e);
        }
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
            if (query.success() != null) {
                predicates.add(builder.equal(root.get("success"), query.success()));
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
