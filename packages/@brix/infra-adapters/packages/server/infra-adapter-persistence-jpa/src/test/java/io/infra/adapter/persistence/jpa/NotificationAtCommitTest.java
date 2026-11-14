/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import io.brix.platform.notification.NotificationApplicationService;
import io.brix.platform.notification.NotificationMetrics;
import io.brix.platform.notification.delivery.EmailDeliveryAdapter;
import io.brix.platform.notification.delivery.EmailDeliveryException;
import io.brix.platform.notification.delivery.EmailMessage;
import io.brix.platform.notification.template.StrictTemplateRenderer;
import io.brix.platform.notification.template.TemplateRepository;
import io.infra.adapter.persistence.jpa.tenancy.P0Global;
import io.infra.adapter.persistence.jpa.tenancy.P0Repositories.GlobalRepository;
import io.runtime.orchestrator.endpoint.DefaultPluginEndpointDispatcher;
import io.runtime.orchestrator.endpoint.EndpointAccessPolicy;
import io.runtime.orchestrator.endpoint.EndpointContract;
import io.runtime.orchestrator.endpoint.EndpointMetrics;
import io.runtime.orchestrator.endpoint.EndpointPolicies;
import io.runtime.orchestrator.endpoint.EndpointRoute;
import io.runtime.orchestrator.endpoint.EndpointTracing;
import io.runtime.orchestrator.endpoint.OwnerEndpointIdempotency;
import io.runtime.orchestrator.invocation.RuntimeInvocations;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.persistence.OwnerUnitOfWorkEndpointBoundary;
import io.runtime.orchestrator.persistence.TenantScope;
import io.runtime.sdk.capability.NotificationException;
import io.runtime.sdk.capability.NotificationRequest;
import io.runtime.sdk.capability.NotificationTemplateKeys;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.capability.PersistenceCapabilityException;
import io.runtime.sdk.plugin.TypedEndpointHandler;

/**
 * A notification sent in an owner's unit of work goes out at the last moment before the commit, after every write of
 * that unit of work (root BRX-RS-ERROR-017 item 1, RFC-012 §4; standard-link plan queue item 11). The unit of work,
 * the notification service and the endpoint dispatcher are the real ones; at each delivery the test reads what the
 * owner's transaction holds and what another connection sees of it.
 */
class NotificationAtCommitTest {

    private static final String PATH = "/api/test/globals";
    private static final String PERMISSION = "test:globals:create";

    private TenantTestEnvironment environment;
    private GlobalRepository globals;
    private JdbcTemplate jdbc;
    private final List<String> seen = new ArrayList<>();
    private final RecordingAdapter adapter = new RecordingAdapter();
    private NotificationApplicationService notifications;

    @BeforeEach
    void open() {
        environment = new TenantTestEnvironment();
        globals = environment.repository(GlobalRepository.class);
        jdbc = new JdbcTemplate(environment.dataSource);
        // Stands for the rows the runtime writes for the owner by JDBC: the endpoint's answer, the inbox, the outbox
        // (an audit fact is an outbox event).
        jdbc.execute("DROP TABLE IF EXISTS runtime_record");
        jdbc.execute("CREATE TABLE runtime_record (id BIGINT PRIMARY KEY, kind VARCHAR(32) NOT NULL)");
        notifications = new NotificationApplicationService(
            new TemplateRepository(new ObjectMapper(), getClass().getClassLoader()),
            new StrictTemplateRenderer(), adapter, "en-US", NotificationMetrics.NOOP);
    }

    @AfterEach
    void close() {
        jdbc.execute("DROP TABLE IF EXISTS runtime_record");
        environment.close();
    }

    @Test
    void aMessageGoesOutOnceEveryWriteOfTheUnitOfWorkIsInTheDatabaseAndBeforeTheyCommit() {
        environment.write(TenantScope.none(), () -> {
            notifications.send(link("a"));
            // Written after the send: a repository write that waits for the flush, as consuming the bootstrap session
            // does, and a JDBC write, as an audit fact's outbox row is.
            globals.save(new P0Global(10L, "pending"));
            jdbc.update("INSERT INTO runtime_record (id, kind) VALUES (10, 'outbox')");
            return null;
        });

        assertEquals(List.of("a: own p0_global=1 records=[outbox], others p0_global=0 records=[], no call"), seen);
        assertEquals("p0_global=1 records=[outbox]", committed());
    }

    @Test
    void anEndpointsMessageGoesOutAfterItsAnswerIsKeptAndWithinItsCall() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher(invocation -> {
            notifications.send(link("a"));
            globals.save(new P0Global(20L, "pending"));
            return CompletableFuture.completedFuture(Map.of("id", 20));
        });

        post(dispatcher);

        assertEquals(List.of("a: own p0_global=1 records=[answer 201], others p0_global=0 records=[], in its call"),
            seen);
        assertEquals("p0_global=1 records=[answer 201]", committed());
    }

    @Test
    void aWriteAfterTheMessageThatFailsSendsNothingAndFailsAsItWouldWithoutTheMessage() {
        environment.write(TenantScope.none(), () -> globals.save(new P0Global(11L, "first")));
        PersistenceCapabilityException without = assertThrows(PersistenceCapabilityException.class,
            () -> environment.write(TenantScope.none(), () -> {
                environment.sharedEntityManager.persist(new P0Global(11L, "again"));
                return null;
            }));

        PersistenceCapabilityException with = assertThrows(PersistenceCapabilityException.class,
            () -> environment.write(TenantScope.none(), () -> {
                notifications.send(link("a"));
                jdbc.update("INSERT INTO runtime_record (id, kind) VALUES (11, 'outbox')");
                // Its insert waits for the flush; the id is taken.
                environment.sharedEntityManager.persist(new P0Global(11L, "again"));
                return null;
            }));

        assertEquals(PersistenceCapabilityException.Code.UNIQUE_CONSTRAINT_VIOLATION, without.code());
        assertEquals(without.code(), with.code());
        assertEquals(List.of(), seen);
        assertEquals("p0_global=1 records=[]", committed());
    }

    @Test
    void aRefusedMessageRollsBackEveryWriteOfTheUnitOfWorkWithTheNotificationsError() {
        adapter.refuse = true;

        NotificationException failure = assertThrows(NotificationException.class,
            () -> environment.write(TenantScope.none(), () -> {
                notifications.send(link("a"));
                globals.save(new P0Global(12L, "pending"));
                jdbc.update("INSERT INTO runtime_record (id, kind) VALUES (12, 'outbox')");
                return null;
            }));

        assertEquals(NotificationException.Code.DELIVERY_FAILED, failure.errorCode());
        assertEquals(1, seen.size());
        assertEquals("p0_global=0 records=[]", committed());
    }

    @Test
    void anEndpointWhoseMessageIsRefusedFailsWithTheNotificationsErrorAndKeepsNoAnswer() {
        adapter.refuse = true;
        DefaultPluginEndpointDispatcher dispatcher = dispatcher(invocation -> {
            notifications.send(link("a"));
            globals.save(new P0Global(21L, "pending"));
            return CompletableFuture.completedFuture(Map.of("id", 21));
        });

        NotificationException failure = assertThrows(NotificationException.class, () -> post(dispatcher));

        assertEquals(NotificationException.Code.DELIVERY_FAILED, failure.errorCode());
        assertEquals("p0_global=0 records=[]", committed());
    }

    private static NotificationRequest link(String recipient) {
        return new NotificationRequest(null, recipient + "@example.invalid",
            NotificationTemplateKeys.PLATFORM_ADMIN_SETUP_INITIAL, "en-US",
            Map.of("setupUrl", "https://setup.example.invalid/#token=raw-secret"));
    }

    /** What the owner's transaction holds, what another connection sees of it, and whether a call is current. */
    private String database() {
        String own = state(jdbc);
        String others;
        try (Connection other = environment.dataSource.getConnection()) {
            others = state(new JdbcTemplate(new SingleConnectionDataSource(other, true)));
        } catch (SQLException unreachable) {
            throw new IllegalStateException(unreachable);
        }
        return "own " + own + ", others " + others + ", "
            + (RuntimeInvocations.current().isPresent() ? "in its call" : "no call");
    }

    private String committed() {
        return environment.read(TenantScope.none(), () -> state(jdbc));
    }

    private static String state(JdbcTemplate on) {
        return "p0_global=" + on.queryForObject("SELECT COUNT(*) FROM p0_global", Long.class)
            + " records=" + on.queryForList("SELECT kind FROM runtime_record ORDER BY id", String.class);
    }

    private DefaultPluginEndpointDispatcher dispatcher(TypedEndpointHandler<JsonNode, Map<String, Integer>> handler) {
        PersistenceCapability owner = environment.persistence;
        OwnerPersistenceProvider host = new OwnerPersistenceProvider() {
            @Override
            public Optional<PersistenceCapability> forOwner(String ownerModuleId) {
                return TenantTestEnvironment.OWNER.equals(ownerModuleId) ? Optional.of(owner) : Optional.empty();
            }

            @Override
            public <T> T readWrite(String ownerModuleId, Duration timeout, Supplier<T> work) {
                // As the host's OwnerPersistenceUnits does.
                return ((OwnerUnitOfWork) owner.unitOfWork()).requiredWithin(timeout, work);
            }
        };
        DefaultPluginEndpointDispatcher dispatcher = new DefaultPluginEndpointDispatcher(
            EndpointPolicies.Limits.defaults(Duration.ofSeconds(5)), new OwnerUnitOfWorkEndpointBoundary(host),
            JsonMapper.builder().build(), EndpointTracing.NOOP, EndpointMetrics.NOOP, Answers::new);
        EndpointAccessPolicy authenticated = EndpointAccessPolicy.fromDeclaration(
            Map.of("mode", "required"), Map.of("permissions", List.of(PERMISSION)), Map.of("mode", "optional"));
        dispatcher.replaceSnapshot(List.of(new EndpointRoute(TenantTestEnvironment.OWNER, "test.globals.create.v1",
            "POST", PATH, authenticated, handler,
            new EndpointContract("", "", 201, null, new EndpointContract.Replay("test.globals.create",
                Duration.ofHours(24))))));
        return dispatcher;
    }

    private static Object post(DefaultPluginEndpointDispatcher dispatcher) {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-actor-id", List.of("u-1"));
        headers.put("x-auth-permission", List.of(PERMISSION));
        headers.put("Idempotency-Key", List.of("key-1"));
        return dispatcher.invoke("POST", PATH, Map.of(), Map.of(), headers);
    }

    /** Keeps the endpoint's answer by JDBC in the owner's unit of work, as the runtime's store does. */
    private final class Answers implements OwnerEndpointIdempotency {

        @Override
        public Reservation reserve(String ownerId, Key key, String fingerprint, Long tenantId, String traceId,
                Duration retention) {
            jdbc.update("INSERT INTO runtime_record (id, kind) VALUES (100, 'answer reserved')");
            return Reservation.TAKEN;
        }

        @Override
        public void complete(String ownerId, Key key, int status, String body) {
            jdbc.update("UPDATE runtime_record SET kind = ? WHERE id = 100", "answer " + status);
        }

        @Override
        public void keepWriteResult(String ownerId, Key key, String result) {
            throw new UnsupportedOperationException("not an endpoint that writes through a contract");
        }

        @Override
        public Optional<Held> find(String ownerId, Key key) {
            return Optional.empty();
        }
    }

    private final class RecordingAdapter implements EmailDeliveryAdapter {

        private boolean refuse;

        @Override
        public void deliver(EmailMessage message) {
            String recipient = message.recipientEmail().substring(0, message.recipientEmail().indexOf('@'));
            seen.add(recipient + ": " + database());
            if (refuse) {
                throw new EmailDeliveryException(EmailDeliveryException.Reason.REJECTED, "reply 550", null);
            }
        }
    }
}
