/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import io.runtime.orchestrator.task.TaskRunClaims;

/**
 * Task triggers are claimed once across hosts (root BRX-RS-MANIFEST-025; standard-link plan L6.4, D57), on the
 * throwaway PostgreSQL when {@code BRIX_P0_POSTGRES_URL} names one, on H2 otherwise.
 */
class JdbcTaskRunClaimsTest {

    private static final Instant NOW = Instant.parse("2026-10-31T09:00:00Z");

    private DataSource dataSource;
    private JdbcTemplate jdbc;
    private String storageId;
    private JdbcTaskRunClaims claims;

    @BeforeEach
    void setUp() {
        String postgresUrl = System.getenv("BRIX_P0_POSTGRES_URL");
        dataSource = postgresUrl == null || postgresUrl.isBlank()
            ? new DriverManagerDataSource("jdbc:h2:mem:task_run_" + System.nanoTime()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "")
            : new DriverManagerDataSource(postgresUrl, System.getenv("BRIX_P0_POSTGRES_USER"),
                System.getenv("BRIX_P0_POSTGRES_PASSWORD"));
        jdbc = new JdbcTemplate(dataSource);
        storageId = "claims_" + Long.toHexString(System.nanoTime());
        jdbc.execute("CREATE TABLE " + storageId + "_task_run (task_id VARCHAR(128) NOT NULL,"
            + " trigger_id VARCHAR(64) NOT NULL, scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL,"
            + " claimed_by VARCHAR(128) NOT NULL, claimed_at TIMESTAMP WITH TIME ZONE NOT NULL,"
            + " status VARCHAR(20) NOT NULL, attempts INTEGER NOT NULL, error_code VARCHAR(64),"
            + " finished_at TIMESTAMP WITH TIME ZONE, PRIMARY KEY (task_id, trigger_id))");
        claims = claims(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        jdbc.execute("DROP TABLE IF EXISTS " + storageId + "_task_run");
    }

    @Test
    void theFirstHostToClaimATriggerRunsItAndTheOthersFindItTaken() {
        Instant due = NOW.minusSeconds(1);

        assertThat(claims.claim("sample-orders", "sample-orders.heartbeat", due.toString(), due, "host-a")).isTrue();
        assertThat(claims.claim("sample-orders", "sample-orders.heartbeat", due.toString(), due, "host-b")).isFalse();
        assertThat(claims.claim("sample-orders", "sample-orders.heartbeat", NOW.toString(), NOW, "host-b")).isTrue();
        assertThat(jdbc.queryForList("SELECT claimed_by FROM " + storageId + "_task_run ORDER BY trigger_id",
            String.class)).containsExactly("host-a", "host-b");
    }

    @Test
    void hostsRacingForTheSameTriggersClaimEachOnce() throws Exception {
        int triggers = 20;
        int hosts = 6;
        Map<String, AtomicInteger> won = new ConcurrentHashMap<>();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(hosts);
        try {
            List<Future<?>> racing = new ArrayList<>();
            for (int host = 0; host < hosts; host++) {
                String instance = "host-" + host;
                racing.add(pool.submit(() -> {
                    start.await();
                    for (int i = 0; i < triggers; i++) {
                        Instant due = NOW.plusSeconds(i);
                        if (claims.claim("sample-orders", "sample-orders.heartbeat", due.toString(), due, instance)) {
                            won.computeIfAbsent(due.toString(), key -> new AtomicInteger()).incrementAndGet();
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> host : racing) {
                host.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(won).hasSize(triggers);
        assertThat(won.values()).allSatisfy(count -> assertThat(count.get()).isEqualTo(1));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + storageId + "_task_run", Integer.class))
            .isEqualTo(triggers);
    }

    @Test
    void aFinishedRunRecordsHowItEnded() {
        claims.claim("sample-orders", "sample-orders.heartbeat", NOW.toString(), NOW, "host-a");

        claims.finish("sample-orders", "sample-orders.heartbeat", NOW.toString(), TaskRunClaims.Outcome.FAILED, 3,
            "task.deadline_exceeded");

        Map<String, Object> run = jdbc.queryForMap("SELECT status, attempts, error_code, finished_at FROM " + storageId
            + "_task_run");
        assertThat(run.get("status")).isEqualTo("FAILED");
        assertThat(((Number) run.get("attempts")).intValue()).isEqualTo(3);
        assertThat(run.get("error_code")).isEqualTo("task.deadline_exceeded");
        assertThat(run.get("finished_at")).isNotNull();
    }

    @Test
    void runsThatFinishedOrWereClaimedBeforeTheRetentionGo() {
        OffsetDateTime cutoff = NOW.atOffset(ZoneOffset.UTC);
        insert("finished-long-ago", cutoff.minusDays(40), cutoff.minusDays(40));
        insert("never-finished", cutoff.minusDays(40), null);
        insert("finished-recently", cutoff.minusDays(40), cutoff.plusSeconds(1));
        insert("running", cutoff.plusSeconds(1), null);

        assertThat(claims.purge(storageId + "_task_run", cutoff, 10)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT trigger_id FROM " + storageId + "_task_run ORDER BY trigger_id",
            String.class)).containsExactly("finished-recently", "running");
        assertThatThrownBy(() -> claims.purge("sample_orders_outbox", cutoff, 10))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(claims.tables()).containsExactly(storageId + "_task_run");
    }

    @Test
    void aModuleWithoutAStorageIdHasNoTableToClaimIn() {
        assertThatThrownBy(() -> claims.claim("other", "other.task", NOW.toString(), NOW, "host-a"))
            .isInstanceOf(IllegalStateException.class);
    }

    private JdbcTaskRunClaims claims(Clock clock) {
        return new JdbcTaskRunClaims(module -> "sample-orders".equals(module) ? Optional.of(storageId) : Optional.empty(),
            () -> List.of(storageId), dataSource, clock);
    }

    private void insert(String triggerId, OffsetDateTime claimedAt, OffsetDateTime finishedAt) {
        jdbc.update("INSERT INTO " + storageId + "_task_run (task_id, trigger_id, scheduled_at, claimed_by, claimed_at,"
                + " status, attempts, finished_at) VALUES ('sample-orders.heartbeat', ?, ?, 'host-a', ?, ?, 1, ?)",
            triggerId, Timestamp.from(claimedAt.toInstant()), Timestamp.from(claimedAt.toInstant()),
            finishedAt == null ? "RUNNING" : "SUCCEEDED", finishedAt == null ? null : Timestamp.from(finishedAt.toInstant()));
    }
}
