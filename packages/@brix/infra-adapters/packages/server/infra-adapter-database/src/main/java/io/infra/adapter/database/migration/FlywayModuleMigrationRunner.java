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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.output.MigrateResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.runtime.manifest.loader.PluginManifestLoader;
import io.runtime.manifest.model.PluginManifest;
import io.runtime.manifest.validation.PluginManifestValidator;
import io.runtime.orchestrator.outbox.CanonicalMessagingTables;

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
        Map<String, Migration> migrations = new LinkedHashMap<>();
        List<String> modules = properties.getModules();
        if (modules != null) {
            for (String module : modules) {
                migrations.putIfAbsent(location(properties.getLocationsPrefix() + module),
                    new Migration(module, location(properties.getLocationsPrefix() + module),
                        properties.getTablePrefix() + module.replace("-", "_")));
            }
        }
        // Root BRX-RS-DATA-031: every Owner's manifest names its migrations; an Owner the Host does not list
        // still gets its tables, in its own history table, after the listed modules.
        if (properties.isDiscoverFromManifests()) {
            for (Migration declared : declaredMigrations(Thread.currentThread().getContextClassLoader(),
                    properties.getTablePrefix())) {
                migrations.putIfAbsent(declared.location(), declared);
            }
        }
        List<MessagingMigration> messaging = properties.isMessagingTables()
            ? messagingMigrations(Thread.currentThread().getContextClassLoader())
            : List.of();
        if (migrations.isEmpty() && messaging.isEmpty()) {
            log.info("[Flyway] No modules configured — skipping multi-module migration.");
            return;
        }

        int successCount = 0;
        int failCount = 0;
        long startTime = System.currentTimeMillis();

        for (Migration migration : migrations.values()) {
            ModuleMigrationResult result = migrateModule(migration);
            results.add(result);

            if (result.isSuccess()) {
                successCount++;
            } else {
                failCount++;
                if (properties.isFailOnError()) {
                    throw new FlywayModuleMigrationException(
                            "Migration failed for module '" + migration.module() + "': " + result.getErrorMessage(),
                            result.getError());
                }
            }
        }

        // Kafka design §14 K19: the platform creates each Owner's messaging tables after the Owner's own migrations,
        // which may already have created them.
        for (MessagingMigration migration : messagingOn(messaging)) {
            ModuleMigrationResult result = migrateMessaging(migration);
            results.add(result);
            if (result.isSuccess()) {
                successCount++;
            } else {
                failCount++;
                if (properties.isFailOnError()) {
                    throw new FlywayModuleMigrationException("Migration failed for the " + migration.kind().part()
                        + " of '" + migration.pluginId() + "': " + result.getErrorMessage(), result.getError());
                }
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[Flyway] Multi-module migration completed in {}ms — {} succeeded, {} failed out of {} modules.",
                elapsed, successCount, failCount, migrations.size() + messaging.size());
    }

    /**
     * Returns the canonical messaging tables the plugin manifests on the class path need: an outbox for
     * {@code data.outbox}, an inbox for {@code data.inbox}, a command idempotency table and a command outcome table for
     * a provided command (root BRX-RS-MESSAGE-023, MESSAGE-026; Kafka design §14 K19; standard-link plan point 7), a
     * task-run table for a provided task (standard-link plan L6.4). Each has its own schema history table,
     * {@code brix_msg_history_<storageId>_<outbox|inbox|idempotency|command_outcome|task_run>}.
     *
     * @param classLoader where to look for manifests
     * @return the messaging migrations, by plugin id and table
     */
    static List<MessagingMigration> messagingMigrations(ClassLoader classLoader) {
        PluginManifestLoader loader = new PluginManifestLoader();
        Map<String, MessagingMigration> declared = new TreeMap<>();
        try {
            for (URL resource : Collections.list(classLoader.getResources(PluginManifestValidator.ACTIVE_MANIFEST_RESOURCE))) {
                PluginManifest manifest;
                try (InputStream input = resource.openStream()) {
                    manifest = loader.load(input);
                }
                PluginManifest.DataSection data = manifest.getData();
                if (data == null || data.getStorageId() == null || data.getStorageId().isBlank()) {
                    continue;
                }
                String pluginId = manifest.getMetadata().getPluginId();
                List<CanonicalMessagingTables.Kind> kinds = new ArrayList<>();
                if (data.getOutbox() != null && !data.getOutbox().isBlank()) {
                    kinds.add(CanonicalMessagingTables.Kind.OUTBOX);
                }
                if (data.getInbox() != null && !data.getInbox().isBlank()) {
                    kinds.add(CanonicalMessagingTables.Kind.INBOX);
                }
                if (manifest.getCommands() != null && !manifest.getCommands().getProvides().isEmpty()) {
                    kinds.add(CanonicalMessagingTables.Kind.COMMAND_IDEMPOTENCY);
                    kinds.add(CanonicalMessagingTables.Kind.COMMAND_OUTCOME);
                }
                if (manifest.getTasks() != null && !manifest.getTasks().getProvides().isEmpty()) {
                    kinds.add(CanonicalMessagingTables.Kind.TASK_RUN);
                }
                if (manifest.keepsEndpointResults()) {
                    kinds.add(CanonicalMessagingTables.Kind.ENDPOINT_IDEMPOTENCY);
                }
                for (CanonicalMessagingTables.Kind kind : kinds) {
                    MessagingMigration migration = new MessagingMigration(pluginId, data.getStorageId(), kind);
                    declared.put(pluginId + "|" + kind.ordinal(), migration);
                }
            }
        } catch (IOException e) {
            throw new FlywayModuleMigrationException("Failed to read the plugin manifests for their messaging tables", e);
        }
        return new ArrayList<>(declared.values());
    }

    /**
     * One Owner's canonical messaging table, created and evolved by the platform's migrations.
     *
     * @param pluginId the Owner
     * @param storageId its {@code data.storageId}
     * @param kind which table
     */
    record MessagingMigration(String pluginId, String storageId, CanonicalMessagingTables.Kind kind) {

        /** The table, such as {@code shop_outbox}. */
        String table() {
            return kind.tableOf(storageId);
        }

        /** The Flyway location of the table's migrations. */
        String location() {
            return "classpath:" + kind.migrations();
        }

        /** The schema history table of the table's migrations. */
        String history() {
            String suffix = switch (kind) {
                case OUTBOX -> "outbox";
                case INBOX -> "inbox";
                case COMMAND_IDEMPOTENCY -> "idempotency";
                case COMMAND_OUTCOME -> "command_outcome";
                case TASK_RUN -> "task_run";
                // Short enough for PostgreSQL's 63 characters with the longest storage id.
                case ENDPOINT_IDEMPOTENCY -> "endpoint_keys";
            };
            return "brix_msg_history_" + storageId + "_" + suffix;
        }
    }

    /** The messaging migrations, on a database the platform has them for: PostgreSQL, the reference DCP. */
    private List<MessagingMigration> messagingOn(List<MessagingMigration> messaging) {
        if (messaging.isEmpty()) {
            return messaging;
        }
        String product;
        try (Connection connection = dataSource.getConnection()) {
            product = connection.getMetaData().getDatabaseProductName();
        } catch (SQLException e) {
            throw new FlywayModuleMigrationException("Cannot tell which database holds the messaging tables", e);
        }
        if (!"PostgreSQL".equalsIgnoreCase(product)) {
            log.warn("[Flyway] The platform has messaging table migrations for PostgreSQL only, not {}; the Owners {}"
                + " must bring their own, and the runtime refuses an Owner without them",
                product, messaging.stream().map(MessagingMigration::pluginId).distinct().toList());
            return List.of();
        }
        return messaging;
    }

    private ModuleMigrationResult migrateMessaging(MessagingMigration migration) {
        String module = migration.pluginId() + ":" + migration.kind().part();
        long start = System.currentTimeMillis();
        try {
            boolean adopt = !tableExists(migration.history()) && tableExists(migration.table());
            // A new history starts below V1, so V1 creates the table; an Owner's own migrations that already created
            // it at the platform schema v1 start the history at V1 (MessagingTableCheck verifies the table when the
            // Owner starts), and later platform versions migrate it from there.
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations(migration.location())
                    .table(migration.history())
                    .placeholderPrefix("{{")
                    .placeholderSuffix("}}")
                    .placeholders(Map.of("storageId", migration.storageId()))
                    .baselineOnMigrate(true)
                    .baselineVersion(adopt ? "1" : "0")
                    .baselineDescription(adopt ? "adopted from the migrations of " + migration.pluginId()
                        : "platform messaging migrations")
                    .validateOnMigrate(properties.isValidateOnMigrate())
                    .load();
            if (adopt) {
                log.info("[Flyway] Adopting {} of '{}' at the platform schema v1", migration.table(),
                    migration.pluginId());
            }
            MigrateResult migrateResult = flyway.migrate();
            MigrationInfo current = flyway.info().current();
            String currentVersion = current != null ? current.getVersion().toString() : "none";
            log.info("[Flyway] Messaging table {} of '{}' is at version {} ({} migrations applied in {}ms), history {}",
                migration.table(), migration.pluginId(), currentVersion, migrateResult.migrationsExecuted,
                System.currentTimeMillis() - start, migration.history());
            return new ModuleMigrationResult(module, true, migrateResult.migrationsExecuted, currentVersion, null, null);
        } catch (Exception e) {
            log.error("[Flyway] Messaging table {} of '{}' failed to migrate: {}", migration.table(),
                migration.pluginId(), e.getMessage(), e);
            return new ModuleMigrationResult(module, false, 0, null, e.getMessage(), e);
        }
    }

    private boolean tableExists(String table) throws SQLException {
        try (Connection connection = dataSource.getConnection();
                ResultSet tables = connection.getMetaData().getTables(connection.getCatalog(), connection.getSchema(),
                    table, new String[] {"TABLE"})) {
            return tables.next();
        }
    }

    /**
     * Returns the migrations the plugin manifests on the class path declare under {@code data}: the
     * location {@code data.migrationLocation}, the history table {@code <table-prefix><data.storageId>}.
     *
     * @param classLoader where to look for manifests
     * @param tablePrefix prefix of the schema history tables
     * @return the declared migrations, by plugin id
     */
    static List<Migration> declaredMigrations(ClassLoader classLoader, String tablePrefix) {
        PluginManifestLoader loader = new PluginManifestLoader();
        Map<String, Migration> declared = new TreeMap<>();
        try {
            for (URL resource : Collections.list(classLoader.getResources(PluginManifestValidator.ACTIVE_MANIFEST_RESOURCE))) {
                PluginManifest manifest;
                try (InputStream input = resource.openStream()) {
                    manifest = loader.load(input);
                }
                PluginManifest.DataSection data = manifest.getData();
                if (data == null || data.getMigrationLocation() == null || data.getMigrationLocation().isBlank()
                        || data.getStorageId() == null || data.getStorageId().isBlank()) {
                    continue;
                }
                String pluginId = manifest.getMetadata().getPluginId();
                declared.put(pluginId, new Migration(pluginId, location(data.getMigrationLocation()),
                    tablePrefix + data.getStorageId()));
            }
        } catch (IOException e) {
            throw new FlywayModuleMigrationException("Failed to read the plugin manifests for their migrations", e);
        }
        return new ArrayList<>(declared.values());
    }

    private static String location(String location) {
        String trimmed = location.endsWith("/") ? location.substring(0, location.length() - 1) : location;
        return trimmed.startsWith("classpath:") || trimmed.startsWith("filesystem:") ? trimmed : "classpath:" + trimmed;
    }

    /**
     * One module's migrations.
     *
     * @param module module or plugin id, for the logs
     * @param location Flyway location
     * @param table schema history table
     */
    record Migration(String module, String location, String table) {
    }

    private ModuleMigrationResult migrateModule(Migration migration) {
        String module = migration.module();
        String location = migration.location();
        String tableName = migration.table();

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
