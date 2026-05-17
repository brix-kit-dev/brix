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
package io.infra.adapter.database.migration;

import java.util.List;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for multi-module Flyway database migration.
 *
 * <p>This configuration replaces the previous {@code FlywayModuleConfig} that was located in the
 * Host layer (host-shell-standalone). According to the Ultra-Thin Host principle (Constraint 6),
 * all capability implementations must reside in Layer 2C (infra-adapters / platform-commons).
 * The Host layer should only contain a Spring Boot main class, dependency declarations (pom.xml),
 * and YAML configuration — no business logic, no service classes, no bean definitions.</p>
 *
 * <h3>Migration Strategy</h3>
 * <p>Each business module (plugin) maintains its own Flyway migration scripts under
 * {@code db/migration/{module}/} in its {@code -server} JAR. This auto-configuration creates
 * independent Flyway instances per module, each with its own history table, enabling:</p>
 * <ul>
 *   <li>Same version numbers across modules (e.g., V100__init.sql in both booking and identity)</li>
 *   <li>Independent rollback and migration status per module</li>
 *   <li>New modules added purely through YAML configuration — no Host code changes</li>
 * </ul>
 *
 * <h3>Activation</h3>
 * <p>This auto-configuration activates when:</p>
 * <ol>
 *   <li>Flyway is on the classpath ({@code org.flywaydb.core.Flyway})</li>
 *   <li>Property {@code brix.infra.database.migration.enabled} is {@code true} (default)</li>
 *   <li>At least one module is listed in {@code brix.infra.database.migration.modules}</li>
 * </ol>
 *
 * <h3>Configuration Example</h3>
 * <pre>{@code
 * brix:
 *   infra:
 *     database:
 *       migration:
 *         enabled: true
 *         modules:
 *           - app-identity
 *           - app-booking
 *           - app-products
 *           - app-partners
 *           - app-messenger
 *           - app-carousel
 *           - app-case
 *           - app-contracts
 *           - app-storage
 *           - app-intake
 *           - app-compliance
 * }</pre>
 *
 * @author Brix Platform Authors
 * @since 3.1.0
 * @see FlywayModuleMigrationProperties
 * @see FlywayModuleMigrationRunner
 */
@AutoConfiguration
@ConditionalOnClass(Flyway.class)
@ConditionalOnProperty(name = "brix.infra.database.migration.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FlywayModuleMigrationProperties.class)
public class FlywayModuleMigrationAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(FlywayModuleMigrationAutoConfiguration.class);

    /**
     * Creates the {@link FlywayModuleMigrationRunner} bean that executes per-module Flyway migrations
     * at application startup.
     *
     * <p>The runner reads the module list from configuration properties, removing the previously
     * hardcoded module list from the Host layer. This aligns with the architecture principle that
     * Host is a pure configuration-driven assembly shell.</p>
     *
     * @param dataSource the shared data source provided by {@link io.infra.adapter.database.config.DatabaseAutoConfiguration}
     *                   or Spring Boot's auto-configured DataSource
     * @param properties the migration configuration properties
     * @return the migration runner that executes migrations for all configured modules
     */
    @Bean
    public FlywayModuleMigrationRunner flywayModuleMigrationRunner(
            DataSource dataSource,
            FlywayModuleMigrationProperties properties) {

        List<String> modules = properties.getModules();

        if (modules == null || modules.isEmpty()) {
            log.info("[Flyway] No modules configured for migration. " +
                    "Set 'brix.infra.database.migration.modules' to enable per-module migration.");
            return new FlywayModuleMigrationRunner(dataSource, properties);
        }

        log.info("[Flyway] Multi-module migration enabled for {} modules: {}", modules.size(), modules);
        return new FlywayModuleMigrationRunner(dataSource, properties);
    }

    /**
     * Ensures Spring Boot's auto-configured {@code entityManagerFactory} bean depends on
     * {@code flywayModuleMigrationRunner}, so all per-module Flyway migrations complete before
     * Hibernate initializes.
     *
     * <p>This is the canonical Spring Boot pattern (mirrors {@code FlywayDependsOnPostProcessor}
     * inside {@code FlywayAutoConfiguration}) for guaranteeing migration-before-Hibernate ordering.
     * Without this, Hibernate's {@code hbm2ddl} (even in {@code validate} mode) and JPA repository
     * initialization can race with Flyway and observe an incomplete schema.</p>
     *
     * @return a {@link BeanFactoryPostProcessor} that registers the dependency
     */
    @Bean
    public static BeanFactoryPostProcessor flywayModuleMigrationEntityManagerFactoryDependsOnPostProcessor() {
        return new EntityManagerFactoryDependsOnPostProcessor("flywayModuleMigrationRunner") {
        };
    }
}
