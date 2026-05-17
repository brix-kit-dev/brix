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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes Flyway database migrations for each configured business module independently.
 *
 * <p>Each module gets its own Flyway instance with a dedicated schema history table, enabling
 * independent version tracking across plugins. Migration scripts are discovered from the classpath
 * under {@code db/migration/{module}/}, packaged in each plugin's {@code -server} JAR.</p>
 *
 * <h3>Per-Module Isolation</h3>
 * <p>Each module's Flyway instance operates independently:</p>
 * <ul>
 *   <li>History table: {@code flyway_schema_history_{module_name}} (e.g., {@code flyway_schema_history_app_identity})</li>
 *   <li>Script location: {@code classpath:db/migration/{module}/} (e.g., {@code classpath:db/migration/app-identity/})</li>
 *   <li>Version numbers can overlap across modules (V100 in booking != V100 in identity)</li>
 * </ul>
 *
 * <h3>Error Handling</h3>
 * <p>When {@code failOnError} is {@code false} (default), a failed module migration is logged as
 * an error but does not prevent other modules from migrating. When {@code true}, the first failure
 * throws a {@link FlywayModuleMigrationException} that halts application startup.</p>
 *
 * <h3>Thread Safety</h3>
 * <p>This class is thread-safe. Migrations are executed serially during Spring context initialization.
 * The migration results are stored in an unmodifiable list for post-startup inspection.</p>
 *
 * @author Brix Platform Authors
 * @since 3.1.0
 * @see FlywayModuleMigrationAutoConfiguration
 * @see FlywayModuleMigrationProperties
 */
public class FlywayModuleMigrationRunner {

    private static final Logger log = LoggerFactory.getLogger(FlywayModuleMigrationRunner.class);

    private final DataSource dataSource;
    private final FlywayModuleMigrationProperties properties;
    private final List<ModuleMigrationResult> results;

    /**
     * Creates a new migration runner and immediately executes migrations for all configured modules.
     *
     * @param dataSource the shared DataSource for all modules (same database, shared schema)
     * @param properties the migration configuration including module list and Flyway settings
     */
    public FlywayModuleMigrationRunner(DataSource dataSource, FlywayModuleMigrationProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
        this.results = new ArrayList<>();
        runModuleMigrations();
    }

    /**
     * Returns the migration results for all processed modules.
     *
     * @return unmodifiable list of per-module migration results
     */
    public List<ModuleMigrationResult> getResults() {
        return Collections.unmodifiableList(results);
    }

    /**
     * Iterates over configured modules and executes Flyway migration for each.
     *
     * <p>Each module's migration scripts are expected at {@code {locationsPrefix}{moduleName}/}
     * on the classpath, typically provided by the module's {@code -server} JAR. The history table
     * name is derived from the module name with hyphens replaced by underscores.</p>
     */
    private void runModuleMigrations() {
        List<String> modules = properties.getModules();
        if (modules == null || modules.isEmpty()) {
            log.info("[Flyway] No modules configured — skipping multi-module migration.");
            return;
        }

        int successCount = 0;
        int failCount = 0;
        long startTime = System.currentTimeMillis();

        for (String module : modules) {
            ModuleMigrationResult result = migrateModule(module);
            results.add(result);

            if (result.isSuccess()) {
                successCount++;
            } else {
                failCount++;
                if (properties.isFailOnError()) {
                    throw new FlywayModuleMigrationException(
                            "Migration failed for module '" + module + "': " + result.getErrorMessage(),
                            result.getError());
                }
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[Flyway] Multi-module migration completed in {}ms — {} succeeded, {} failed out of {} modules.",
                elapsed, successCount, failCount, modules.size());
    }

    /**
     * Executes Flyway migration for a single business module.
     *
     * @param module the module name (e.g., "app-identity")
     * @return the migration result containing status, applied migration count, and any error
     */
    private ModuleMigrationResult migrateModule(String module) {
        // Build the classpath location for this module's migration scripts
        String location = properties.getLocationsPrefix() + module;

        // Build the history table name: flyway_schema_history_app_identity
        String tableName = properties.getTablePrefix() + module.replace("-", "_");

        long moduleStart = System.currentTimeMillis();

        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations(location)
                    .table(tableName)
                    .baselineOnMigrate(properties.isBaselineOnMigrate())
                    .baselineVersion(properties.getBaselineVersion())
                    .validateOnMigrate(properties.isValidateOnMigrate())
                    .outOfOrder(properties.isOutOfOrder())
                    .load();

            MigrateResult migrateResult = flyway.migrate();
            long elapsed = System.currentTimeMillis() - moduleStart;

            // Retrieve current migration status for logging
            MigrationInfoService infoService = flyway.info();
            MigrationInfo current = infoService.current();
            String currentVersion = current != null ? current.getVersion().toString() : "none";

            log.info("[Flyway] Module '{}' migration completed in {}ms — {} migrations applied, current version: {}, table: {}",
                    module, elapsed, migrateResult.migrationsExecuted, currentVersion, tableName);

            return new ModuleMigrationResult(module, true, migrateResult.migrationsExecuted, currentVersion, null, null);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - moduleStart;
            log.error("[Flyway] Module '{}' migration failed after {}ms: {}", module, elapsed, e.getMessage(), e);

            return new ModuleMigrationResult(module, false, 0, null, e.getMessage(), e);
        }
    }

    /**
     * Immutable result of a single module's Flyway migration.
     *
     * @author Brix Platform Authors
     * @since 3.1.0
     */
    public static class ModuleMigrationResult {
        private final String module;
        private final boolean success;
        private final int migrationsApplied;
        private final String currentVersion;
        private final String errorMessage;
        private final Exception error;

        public ModuleMigrationResult(String module, boolean success, int migrationsApplied,
                                     String currentVersion, String errorMessage, Exception error) {
            this.module = module;
            this.success = success;
            this.migrationsApplied = migrationsApplied;
            this.currentVersion = currentVersion;
            this.errorMessage = errorMessage;
            this.error = error;
        }

        public String getModule() {
            return module;
        }

        public boolean isSuccess() {
            return success;
        }

        public int getMigrationsApplied() {
            return migrationsApplied;
        }

        public String getCurrentVersion() {
            return currentVersion;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public Exception getError() {
            return error;
        }
    }
}
