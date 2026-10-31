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
package io.infra.adapter.persistence.jpa.config;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.zaxxer.hikari.HikariDataSource;

import io.infra.adapter.persistence.jpa.OwnerPersistenceUnits;
import io.infra.adapter.persistence.jpa.config.tenant.TenantRecordRepository;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.persistence.TenantScope;
import io.runtime.orchestrator.persistence.TenantScopes;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.capability.PersistenceCapabilityException;

/**
 * The Host's Hibernate takes pooled connections lazily, and a query's provider waits for one no longer than the
 * query's deadline (query design §5.8 items 2 and 3), on Spring Boot's own HikariCP pool.
 */
class LazyConnectionsAutoConfigurationTest {

    private static final TenantScope TENANT = TenantScope.tenant(7L);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            DataSourceAutoConfiguration.class,
            TenantStatementVerifierAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            JpaPersistenceAutoConfiguration.class))
        .withUserConfiguration(JpaPersistenceAutoConfigurationTest.OwnerModuleConfiguration.class)
        .withPropertyValues(
            "spring.datasource.url=jdbc:h2:mem:lazy-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            "spring.jpa.hibernate.ddl-auto=create-drop");

    @Test
    void aUnitOfWorkTakesAPooledConnectionOnlyForItsFirstStatement() {
        runner.run(context -> {
            assertNull(context.getStartupFailure());
            HikariDataSource pool = context.getBean(HikariDataSource.class);
            PersistenceCapability persistence = persistence(context);
            TenantRecordRepository records = persistence.repository(TenantRecordRepository.class);

            int[] active = TenantScopes.within(TENANT, () -> persistence.unitOfWork().required(() -> {
                int beforeStatement = active(pool);
                records.findByLabel("none");
                return new int[] {beforeStatement, active(pool)};
            }));

            assertEquals(0, active[0], "no pooled connection before the first statement");
            assertEquals(1, active[1]);
        });
    }

    @Test
    void withoutLazyConnectionsAUnitOfWorkTakesOneWhenItBegins() {
        runner.withPropertyValues("brix.persistence.lazy-connections=false").run(context -> {
            assertNull(context.getStartupFailure());
            HikariDataSource pool = context.getBean(HikariDataSource.class);
            PersistenceCapability persistence = persistence(context);

            int active = TenantScopes.within(TENANT, () -> persistence.unitOfWork().required(() -> active(pool)));

            assertEquals(1, active);
        });
    }

    @Test
    void aQueryProviderWaitsForAConnectionOnlyUntilTheQueryDeadline() {
        runner.withPropertyValues(
                "spring.datasource.hikari.maximum-pool-size=1",
                "spring.datasource.hikari.connection-timeout=30000")
            .run(context -> {
                assertNull(context.getStartupFailure());
                OwnerPersistenceUnits units = (OwnerPersistenceUnits) context.getBean(OwnerPersistenceProvider.class);
                PersistenceCapability persistence = persistence(context);
                TenantRecordRepository records = persistence.repository(TenantRecordRepository.class);
                CountDownLatch holding = new CountDownLatch(1);
                CountDownLatch release = new CountDownLatch(1);
                Thread holder = new Thread(() -> TenantScopes.within(TENANT, () -> persistence.unitOfWork()
                    .required(() -> {
                        records.findByLabel("none");
                        holding.countDown();
                        awaitQuietly(release);
                        return null;
                    })), "holds-the-only-connection");
                holder.start();
                try {
                    assertTrue(holding.await(30, SECONDS));
                    long started = System.nanoTime();

                    PersistenceCapabilityException failure = assertThrows(PersistenceCapabilityException.class,
                        () -> TenantScopes.within(TENANT, () -> units.readOnly("test-owner", Duration.ofMillis(300),
                            () -> records.findByLabel("none"))));

                    long waited = Duration.ofNanos(System.nanoTime() - started).toMillis();
                    assertEquals(PersistenceCapabilityException.Code.UNAVAILABLE, failure.code());
                    assertTrue(waited < 5_000, "waited " + waited + " ms, not the pool's 30 s");
                    assertTrue(!Thread.currentThread().isInterrupted());
                } finally {
                    release.countDown();
                    holder.join(30_000);
                }
            });
    }

    private static PersistenceCapability persistence(AssertableApplicationContext context) {
        return context.getBean(JpaPersistenceAutoConfigurationTest.OwnerModuleConfiguration.InjectedPersistence.class)
            .persistence();
    }

    private static int active(HikariDataSource pool) {
        return pool.getHikariPoolMXBean().getActiveConnections();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(30, SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
