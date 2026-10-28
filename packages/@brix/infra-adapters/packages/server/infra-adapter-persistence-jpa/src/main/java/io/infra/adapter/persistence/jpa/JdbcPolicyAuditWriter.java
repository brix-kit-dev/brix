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
package io.infra.adapter.persistence.jpa;

import java.sql.Types;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.transaction.support.TransactionTemplate;

import io.runtime.manifest.model.PluginManifest;
import io.runtime.orchestrator.persistence.PolicyAuditWriter;
import io.runtime.orchestrator.persistence.PolicyUseRecord;
import io.runtime.sdk.capability.IdGeneratorCapability;
import io.runtime.sdk.error.PolicyAuditException;

/**
 * The runtime policy audit writer (root BRX-RS-DATA-047): inserts the policy-use records the
 * platform audit owner allows {@value PolicyAuditWriter#CUSTODIAN} to write (DATA-045).
 *
 * <p>The registration is checked the first time the writer is asked about it, which the runtime's
 * startup check does in every host that uses the policies: one owner registers it, it grants every
 * column the writer writes, its record schema resolves, an entity of that owner maps the table, and
 * the Host's {@link IdGeneratorCapability} produces numeric ids. A record is one INSERT of a fixed
 * column list into that table, for a registered action. Its id comes from that generator, the same
 * source the owner uses for its own rows (root BRX-RS-DATA-024, platform-audit design §5.2); the
 * creation time comes from the table's default. Each record commits in a local transaction of its
 * own on the shared transaction manager, so the caller's transaction is suspended (DATA-029,
 * HOST-020). The writer never reads the table.</p>
 */
final class JdbcPolicyAuditWriter implements PolicyAuditWriter {

    /** Columns every record writes, in statement order; the registration must grant all of them. */
    static final List<String> COLUMNS = List.of(
        "id", "operator_identity_id", "action", "resource_type", "resource_id", "affected_tenants", "context",
        "success", "error_code", "execution_identity", "reason_code", "trace_id", "source_ip", "user_agent", "writer");

    private static final Set<String> JSON_COLUMNS = Set.of("affected_tenants", "context");
    private static final String SCHEMA_PREFIX = "brix://schemas/";
    private static final Logger audit = LoggerFactory.getLogger("brix.tenant.isolation.audit");

    private static final Registration NONE = new Registration("", "", Set.of(), null);

    private final OwnerDirectory owners;
    private final EntityManagerFactory entityManagerFactory;
    private final Supplier<IdGeneratorCapability> ids;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private volatile Registration registration;
    private volatile String insert;

    JdbcPolicyAuditWriter(
            OwnerDirectory owners,
            EntityManagerFactory entityManagerFactory,
            JpaTransactionManager transactionManager,
            DataSource dataSource,
            Duration timeout,
            Supplier<IdGeneratorCapability> ids) {
        this.owners = Objects.requireNonNull(owners, "owners must not be null");
        this.entityManagerFactory = Objects.requireNonNull(entityManagerFactory, "entityManagerFactory must not be null");
        this.ids = Objects.requireNonNull(ids, "ids must not be null");
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager must not be null"));
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactions.setTimeout(Math.toIntExact(Math.max(1, Objects.requireNonNull(timeout, "timeout must not be null").toSeconds())));
        transactions.setName("brix-custodian:" + CUSTODIAN);
    }

    @Override
    public boolean covers(String action) {
        return action != null && registration().actions().contains(action);
    }

    @Override
    public void record(PolicyUseRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        if (!covers(record.action())) {
            throw failed(record, "not_registered", null);
        }
        try {
            long id = numericId(registration().ids());
            transactions.executeWithoutResult(status -> jdbc.update(insert(), parameters(id, record)));
        } catch (RuntimeException failure) {
            throw failed(record, reasonOf(failure), failure);
        }
    }

    /** The registration, resolved once; {@link #NONE} when no owner registers this custodian. */
    private Registration registration() {
        Registration resolved = registration;
        if (resolved == null) {
            synchronized (this) {
                resolved = registration;
                if (resolved == null) {
                    resolved = resolve(owners, entityManagerFactory, ids);
                    registration = resolved;
                }
            }
        }
        return resolved;
    }

    private static Registration resolve(
            OwnerDirectory owners, EntityManagerFactory entityManagerFactory, Supplier<IdGeneratorCapability> ids) {
        List<OwnerDirectory.CustodialRegistration> found = owners.custodialWrites(CUSTODIAN);
        if (found.isEmpty()) {
            return NONE;
        }
        if (found.size() > 1) {
            throw new IllegalStateException("More than one owner registers custodial writes for " + CUSTODIAN + ": "
                + found.stream().map(OwnerDirectory.CustodialRegistration::ownerId).toList());
        }
        String ownerId = found.get(0).ownerId();
        PluginManifest.CustodialWrite write = found.get(0).write();
        String where = ownerId + " registers " + CUSTODIAN + " on " + write.getTable();
        List<String> ungranted = COLUMNS.stream().filter(column -> !write.getColumns().contains(column)).toList();
        if (!ungranted.isEmpty()) {
            throw new IllegalStateException(where + " without the columns " + ungranted + " (root BRX-RS-DATA-045)");
        }
        if (!List.of("INSERT").equals(write.getOperations())) {
            throw new IllegalStateException(where + " with operations other than INSERT (root BRX-RS-MANIFEST-031)");
        }
        if (owners.classLoader().getResource(schemaResource(write.getRecordSchemaRef())) == null) {
            throw new IllegalStateException(where + " with the record schema " + write.getRecordSchemaRef()
                + ", which does not resolve (root BRX-RS-MANIFEST-031)");
        }
        if (!mapsTable(owners, entityManagerFactory, ownerId, write.getTable())) {
            throw new IllegalStateException(where + ", a table none of its entities maps (root BRX-RS-MANIFEST-031)");
        }
        IdGeneratorCapability generator = ids.get();
        if (generator == null) {
            throw new IllegalStateException(where + ", but the Host has no IdGeneratorCapability for the record ids"
                + " (root BRX-RS-DATA-024, platform-audit design §5.2)");
        }
        try {
            numericId(generator);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(where + ", but the Host's IdGeneratorCapability does not produce the"
                + " numeric ids the table keys on", e);
        }
        return new Registration(ownerId, write.getTable(), Set.copyOf(write.getActions()), generator);
    }

    private static long numericId(IdGeneratorCapability generator) {
        return Long.parseLong(generator.generate());
    }

    /** Resource that holds a record schema: {@code brix://schemas/a/b/1.0.0} is {@code META-INF/brix/schemas/a/b/1.0.0.json}. */
    static String schemaResource(String schemaRef) {
        if (schemaRef == null || !schemaRef.startsWith(SCHEMA_PREFIX)) {
            return "";
        }
        return "META-INF/brix/schemas/" + schemaRef.substring(SCHEMA_PREFIX.length()) + ".json";
    }

    private static boolean mapsTable(OwnerDirectory owners, EntityManagerFactory entityManagerFactory, String ownerId, String table) {
        return entityManagerFactory.getMetamodel().getEntities().stream()
            .map(EntityType::getJavaType)
            .filter(Objects::nonNull)
            .filter(type -> type.getAnnotation(Table.class) != null && table.equalsIgnoreCase(type.getAnnotation(Table.class).name()))
            .anyMatch(type -> owners.ownerOf(type).filter(owner -> owner.ownerId().equals(ownerId)).isPresent());
    }

    private String insert() {
        String statement = insert;
        if (statement == null) {
            String json = jdbc.execute((ConnectionCallback<String>) connection -> jsonPlaceholder(
                connection.getMetaData().getDatabaseProductName()));
            statement = "INSERT INTO " + registration().table() + " (" + String.join(", ", COLUMNS) + ") VALUES ("
                + COLUMNS.stream().map(column -> JSON_COLUMNS.contains(column) ? json : "?").collect(Collectors.joining(", "))
                + ")";
            insert = statement;
        }
        return statement;
    }

    private static String jsonPlaceholder(String databaseProduct) {
        return switch (databaseProduct) {
            case "PostgreSQL" -> "CAST(? AS JSONB)";
            case "H2" -> "? FORMAT JSON";
            default -> throw new IllegalStateException("The policy audit writer has no JSON binding for " + databaseProduct);
        };
    }

    private static Object[] parameters(long id, PolicyUseRecord record) {
        return new Object[] {
            id,
            record.actorIdentityId(),
            record.action(),
            record.resourceType(),
            new SqlParameterValue(Types.VARCHAR, null),
            record.affectedTenants().stream().map(String::valueOf).collect(Collectors.joining(",", "[", "]")),
            json(record.context()),
            record.success(),
            new SqlParameterValue(Types.VARCHAR, record.errorCode()),
            record.executionIdentity(),
            record.reasonCode(),
            record.traceId(),
            new SqlParameterValue(Types.VARCHAR, record.sourceAddress()),
            new SqlParameterValue(Types.VARCHAR, record.userAgent()),
            CUSTODIAN,
        };
    }

    private static String json(Map<String, String> context) {
        return context.entrySet().stream()
            .map(entry -> quote(entry.getKey()) + ":" + quote(entry.getValue()))
            .collect(Collectors.joining(",", "{", "}"));
    }

    private static String quote(String value) {
        StringBuilder quoted = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                default -> {
                    if (c < 0x20) {
                        quoted.append(String.format("\\u%04x", (int) c));
                    } else {
                        quoted.append(c);
                    }
                }
            }
        }
        return quoted.append('"').toString();
    }

    private static String reasonOf(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof DataIntegrityViolationException) {
                return "integrity";
            }
            if (cause instanceof QueryTimeoutException || cause instanceof TransactionTimedOutException) {
                return "timeout";
            }
            if (cause instanceof DataAccessResourceFailureException || cause instanceof CannotCreateTransactionException) {
                return "unavailable";
            }
        }
        return "failure";
    }

    private PolicyAuditException failed(PolicyUseRecord record, String reason, Throwable cause) {
        TenantIsolationMetrics.policyAuditFailure(record.policy().id(), reason);
        audit.error("event=tenant.policy.audit_failed policy={} reason={} trace={}",
            record.policy().id(), reason, record.traceId());
        Map<String, String> parameters = Map.of("policy", record.policy().id(), "reason", reason);
        return new PolicyAuditException(parameters, cause);
    }

    /** The custodial registration this writer works under, with the id generator its records use. */
    private record Registration(String ownerId, String table, Set<String> actions, IdGeneratorCapability ids) {
    }
}
