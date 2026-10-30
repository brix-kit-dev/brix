/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import java.sql.Types;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import jakarta.persistence.EntityManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import io.runtime.manifest.model.PluginManifest;
import io.runtime.orchestrator.outbox.MessageOperationAudit;
import io.runtime.sdk.capability.IdGeneratorCapability;

/**
 * The runtime custodian {@value MessageOperationAudit#CUSTODIAN}: inserts an operator's replay, discard or republish
 * of a message, and the failure of one, into the platform audit table the platform-audit owner registers for it
 * (root BRX-RS-DATA-045, MESSAGE-028; Kafka design D33, §14 K14), in a transaction of its own, as the policy audit
 * writer does.
 *
 * @author Brix Platform Authors
 * @since 3.3.0
 */
final class JdbcMessageOperationAudit implements MessageOperationAudit {

    private static final Logger log = LoggerFactory.getLogger(JdbcMessageOperationAudit.class);
    private static final List<String> ACTIONS = List.of(REPLAYED, DISCARDED, REPUBLISHED);

    private final OwnerDirectory owners;
    private final EntityManagerFactory entityManagerFactory;
    private final Supplier<IdGeneratorCapability> ids;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private volatile String table;
    private volatile String insert;

    JdbcMessageOperationAudit(
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
        this.transactions = new TransactionTemplate(Objects.requireNonNull(transactionManager,
            "transactionManager must not be null"));
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactions.setTimeout(Math.toIntExact(Math.max(1, Objects.requireNonNull(timeout,
            "timeout must not be null").toSeconds())));
        transactions.setName("brix-custodian:" + CUSTODIAN);
    }

    @Override
    public void record(MessageOperation operation) {
        Objects.requireNonNull(operation, "operation must not be null");
        if (!ACTIONS.contains(operation.action())) {
            throw new IllegalArgumentException("Not a message operation: " + operation.action());
        }
        String statement = insert();
        IdGeneratorCapability generator = Objects.requireNonNull(ids.get(), "the Host has no IdGeneratorCapability");
        long id = JdbcPolicyAuditWriter.numericId(generator);
        Map<String, String> context = new LinkedHashMap<>();
        context.put("place", operation.place());
        context.put("operator", operation.operator());
        if (operation.principal() != null) {
            context.put("principal", operation.principal());
        }
        context.put("reason", operation.reason());
        String traceId = MDC.get("traceId");
        transactions.executeWithoutResult(status -> jdbc.update(statement,
            id,
            new SqlParameterValue(Types.BIGINT, null),
            operation.action(),
            "message",
            operation.messageId(),
            "[]",
            JdbcPolicyAuditWriter.json(context),
            operation.errorCode() == null,
            new SqlParameterValue(Types.VARCHAR, operation.errorCode()),
            CUSTODIAN,
            "operator-request",
            traceId == null || traceId.isBlank() ? UUID.randomUUID().toString().replace("-", "") : traceId,
            new SqlParameterValue(Types.VARCHAR, null),
            new SqlParameterValue(Types.VARCHAR, null),
            CUSTODIAN));
        log.info("event=message.operation_audited action={} message_id={} place={} operator={} error_code={}",
            operation.action(), operation.messageId(), operation.place(), operation.operator(), operation.errorCode());
    }

    private String insert() {
        String statement = insert;
        if (statement == null) {
            String json = jdbc.execute((ConnectionCallback<String>) connection -> JdbcPolicyAuditWriter.jsonPlaceholder(
                connection.getMetaData().getDatabaseProductName()));
            statement = "INSERT INTO " + table() + " (" + String.join(", ", JdbcPolicyAuditWriter.COLUMNS) + ") VALUES ("
                + JdbcPolicyAuditWriter.COLUMNS.stream()
                    .map(column -> "affected_tenants".equals(column) || "context".equals(column) ? json : "?")
                    .collect(Collectors.joining(", "))
                + ")";
            insert = statement;
        }
        return statement;
    }

    /** The table the platform-audit owner registers for this custodian, checked as the policy writer checks it. */
    private String table() {
        String resolved = table;
        if (resolved == null) {
            List<OwnerDirectory.CustodialRegistration> found = owners.custodialWrites(CUSTODIAN);
            if (found.size() != 1) {
                throw new IllegalStateException("Exactly one owner must register custodial writes for " + CUSTODIAN
                    + ", found " + found.stream().map(OwnerDirectory.CustodialRegistration::ownerId).toList());
            }
            String ownerId = found.get(0).ownerId();
            PluginManifest.CustodialWrite write = found.get(0).write();
            String where = ownerId + " registers " + CUSTODIAN + " on " + write.getTable();
            List<String> ungranted = JdbcPolicyAuditWriter.COLUMNS.stream()
                .filter(column -> !write.getColumns().contains(column)).toList();
            if (!ungranted.isEmpty() || !List.of("INSERT").equals(write.getOperations())
                    || !write.getActions().containsAll(ACTIONS)) {
                throw new IllegalStateException(where + " without INSERT of the columns " + JdbcPolicyAuditWriter.COLUMNS
                    + " for the actions " + ACTIONS + " (root BRX-RS-DATA-045)");
            }
            if (owners.classLoader().getResource(JdbcPolicyAuditWriter.schemaResource(write.getRecordSchemaRef())) == null) {
                throw new IllegalStateException(where + " with the record schema " + write.getRecordSchemaRef()
                    + ", which does not resolve (root BRX-RS-MANIFEST-031)");
            }
            if (!JdbcPolicyAuditWriter.mapsTable(owners, entityManagerFactory, ownerId, write.getTable())) {
                throw new IllegalStateException(where + ", a table none of its entities maps (root BRX-RS-MANIFEST-031)");
            }
            resolved = write.getTable();
            table = resolved;
        }
        return resolved;
    }
}
