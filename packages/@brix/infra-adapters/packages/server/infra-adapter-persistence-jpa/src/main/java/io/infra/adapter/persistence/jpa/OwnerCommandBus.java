/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.slf4j.MDC;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import io.runtime.manifest.model.PluginManifest;
import io.runtime.orchestrator.command.CommandMessages;
import io.runtime.orchestrator.command.CommandSender;
import io.runtime.orchestrator.command.SentCommand;
import io.runtime.orchestrator.command.SentCommands;
import io.runtime.sdk.capability.CommandCapability;
import io.runtime.sdk.capability.CommandReceipt;
import io.runtime.sdk.capability.CommandSubmission;
import io.runtime.sdk.capability.CommandSubmitException;
import io.runtime.sdk.command.CommandRequest;

/**
 * An Owner's command capability (root BRX-RS-MESSAGE-010 to MESSAGE-013; Kafka design §14 K22): it writes each
 * command to the Owner's own canonical outbox, as {@code message_kind = COMMAND}, and the relay takes it from there
 * like the Owner's events.
 *
 * <p>Inside the Owner's unit of work the command commits or rolls back with it, and the durable receipt completes
 * after the commit, or exceptionally after a rollback (MESSAGE-012). With no transaction at all the command is
 * written on its own, and the receipt is complete when {@code submit} returns. Inside another Owner's transaction, or
 * a read-only one, it is refused: one transaction writes one Owner's data (DATA-008). The runtime builds the
 * envelope: a new command id, this Owner as the producer, the tenant only when the unit of work reaches it, and the
 * schema version the Owner's manifest sends (the lower bound of its {@code versionRange}).</p>
 *
 * <p>The Owner's typed clients read their commands' status from here too, until the provider has a record of them
 * (standard-link plan point 7).</p>
 *
 * @author Brix Platform Authors
 * @since 3.3.0
 */
final class OwnerCommandBus implements CommandCapability, CommandSender, SentCommands {

    private static final Pattern LOWER_BOUND = Pattern.compile(">=\\s*(\\d+\\.\\d+\\.\\d+)");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String ownerId;
    private final String outbox;
    private final Map<String, PluginManifest.ContractRef> consumed;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private volatile String payloadPlaceholder;

    OwnerCommandBus(
            String ownerId,
            String outbox,
            Map<String, PluginManifest.ContractRef> consumed,
            DataSource dataSource,
            ObjectMapper json,
            Clock clock) {
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
        this.outbox = outbox == null ? null : OwnerTenantTables.normalize(outbox);
        this.consumed = Map.copyOf(Objects.requireNonNull(consumed, "consumed must not be null"));
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.json = Objects.requireNonNull(json, "json must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    @Deprecated(since = "3.3.0", forRemoval = true)
    public <C> CommandSubmission submit(CommandRequest<C> request) {
        Objects.requireNonNull(request, "request must not be null");
        PluginManifest.ContractRef declared = consumed.get(request.commandType());
        if (declared == null) {
            throw refused(CommandSubmitException.Code.UNDECLARED_COMMAND, request);
        }
        return write(request, sentVersion(declared), null);
    }

    /**
     * Writes a typed client's command: its version is the sender's generated type's, and it names the user the sender
     * acted for (standard-link plan L4.2).
     */
    @Override
    public CommandSubmission send(CommandRequest<?> request, String schemaVersion, String actorId) {
        Objects.requireNonNull(request, "request must not be null");
        PluginManifest.ContractRef declared = consumed.get(request.commandType());
        if (declared == null) {
            throw refused(CommandSubmitException.Code.UNDECLARED_COMMAND, request);
        }
        return write(request, Objects.requireNonNull(schemaVersion, "schemaVersion must not be null"), actorId);
    }

    /**
     * Finds a command in this Owner's outbox (standard-link plan point 7). Its time is when it was queued, published,
     * or parked or discarded, by its status.
     */
    @Override
    public Optional<SentCommand> sent(String commandId) {
        if (outbox == null || commandId == null || commandId.isBlank()) {
            return Optional.empty();
        }
        List<SentCommand> rows = jdbc.query("SELECT message_type, status, tenant_id, last_error_code, created_at,"
                + " available_at, published_at FROM " + outbox + " WHERE message_id = ? AND message_kind = 'COMMAND'",
            (row, number) -> {
                String status = row.getString(2);
                long tenantId = row.getLong(3);
                Long tenant = row.wasNull() ? null : tenantId;
                Timestamp at = switch (status) {
                    case "PUBLISHED" -> row.getTimestamp(7);
                    case "PARKED", "DISCARDED" -> row.getTimestamp(6);
                    default -> row.getTimestamp(5);
                };
                return new SentCommand(commandId, row.getString(1), status, tenant, row.getString(4),
                    at == null ? null : OffsetDateTime.ofInstant(at.toInstant(), ZoneOffset.UTC));
            },
            commandId);
        return rows.stream().findFirst();
    }

    private CommandSubmission write(CommandRequest<?> request, String schemaVersion, String actorId) {
        if (outbox == null) {
            throw refused(CommandSubmitException.Code.OUTBOX_APPEND_FAILED, request);
        }
        OwnerTransactionScope transaction = OwnerTransactionScope.joinable(ownerId);
        boolean inTransaction = TransactionSynchronizationManager.isActualTransactionActive();
        if (inTransaction && (transaction == null || transaction.readOnly())) {
            // Another Owner's transaction, or a read-only one: the command would be written outside this Owner's.
            throw refused(CommandSubmitException.Code.NO_ACTIVE_TRANSACTION, request);
        }
        Long tenantId = tenantOf(request, transaction);
        String commandId = UUID.randomUUID().toString();
        String payload;
        try {
            payload = CommandMessages.encode(json, request.payload(), request.idempotencyScope(), request.idempotencyKey(),
                actorId);
        } catch (JacksonException invalid) {
            throw new CommandSubmitException(CommandSubmitException.Code.INVALID_COMMAND,
                Map.of("commandType", request.commandType()), invalid);
        }
        Instant now = clock.instant();
        try {
            jdbc.update("INSERT INTO " + outbox + " (message_id, event_id, message_kind, message_type, event_type,"
                    + " schema_version, reliability, producer_plugin_id, scope, tenant_id, partition_key, occurred_at,"
                    + " correlation_id, causation_id, traceparent, tracestate, payload, status, available_at,"
                    + " attempt_count, created_at)"
                    + " VALUES (?, ?, 'COMMAND', ?, ?, ?, 'STANDARD', ?, ?, ?, ?, ?, ?, ?, ?, ?, " + payloadPlaceholder()
                    + ", 'PENDING', ?, 0, ?)",
                commandId,
                commandId,
                request.commandType(),
                request.commandType(),
                schemaVersion,
                ownerId,
                tenantId == null ? "PLATFORM" : "TENANT",
                tenantId,
                request.partitionKey() != null ? request.partitionKey() : request.idempotencyKey(),
                Timestamp.from(now),
                firstText(MDC.get("correlationId"), commandId),
                null,
                traceparent(),
                MDC.get("tracestate"),
                payload,
                Timestamp.from(now),
                Timestamp.from(now));
        } catch (RuntimeException failure) {
            throw new CommandSubmitException(CommandSubmitException.Code.OUTBOX_APPEND_FAILED,
                Map.of("commandType", request.commandType()), failure);
        }
        CommandReceipt receipt = new CommandReceipt(commandId, request.commandType(), now, CommandReceipt.Status.QUEUED);
        CompletableFuture<CommandReceipt> durable = new CompletableFuture<>();
        if (inTransaction) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status == STATUS_COMMITTED) {
                        durable.complete(receipt);
                    } else {
                        durable.completeExceptionally(new CommandSubmitException(
                            CommandSubmitException.Code.TRANSACTION_ROLLED_BACK, Map.of("commandId", commandId), null));
                    }
                }
            });
        } else {
            durable.complete(receipt);
        }
        return new CommandSubmission(commandId, durable);
    }

    private Long tenantOf(CommandRequest<?> request, OwnerTransactionScope transaction) {
        if (request.tenantId() == null) {
            return null;
        }
        long tenantId;
        try {
            tenantId = Long.parseLong(request.tenantId());
        } catch (NumberFormatException invalid) {
            throw refused(CommandSubmitException.Code.INVALID_COMMAND, request);
        }
        if (transaction == null || !transaction.tenancy().tenants().contains(tenantId)) {
            throw refused(CommandSubmitException.Code.TENANT_UNREACHED, request);
        }
        return tenantId;
    }

    /** The version a consumer sends: the lower bound of the range its manifest declares. */
    private static String sentVersion(PluginManifest.ContractRef declared) {
        if (declared.getVersion() != null && !declared.getVersion().isBlank()) {
            return declared.getVersion();
        }
        Matcher lower = LOWER_BOUND.matcher(declared.getVersionRange() == null ? "" : declared.getVersionRange());
        return lower.find() ? lower.group(1) : "1.0.0";
    }

    private CommandSubmitException refused(CommandSubmitException.Code code, CommandRequest<?> request) {
        return new CommandSubmitException(code, Map.of("owner", ownerId, "commandType", request.commandType()), null);
    }

    private String payloadPlaceholder() {
        String placeholder = payloadPlaceholder;
        if (placeholder == null) {
            String product = jdbc.execute((ConnectionCallback<String>) connection ->
                connection.getMetaData().getDatabaseProductName());
            placeholder = product != null && product.toLowerCase(Locale.ROOT).contains("postgresql")
                ? "CAST(? AS JSONB)"
                : "?";
            payloadPlaceholder = placeholder;
        }
        return placeholder;
    }

    private static String traceparent() {
        String current = MDC.get("traceparent");
        if (current != null && current.matches("00-[0-9a-f]{32}-[0-9a-f]{16}-[0-9a-f]{2}")) {
            return current;
        }
        byte[] trace = new byte[16];
        byte[] span = new byte[8];
        RANDOM.nextBytes(trace);
        RANDOM.nextBytes(span);
        trace[0] |= 1;
        span[0] |= 1;
        return "00-" + HexFormat.of().formatHex(trace) + "-" + HexFormat.of().formatHex(span) + "-01";
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
