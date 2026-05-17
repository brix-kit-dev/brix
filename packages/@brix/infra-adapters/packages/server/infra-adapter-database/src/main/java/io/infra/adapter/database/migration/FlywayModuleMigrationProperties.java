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
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for multi-module Flyway migration support.
 *
 * <p>This class externalizes the module list that was previously hardcoded in the Host layer's
 * {@code FlywayModuleConfig}. By moving configuration to the adapter layer, new plugins can be
 * added without modifying Host code — respecting the Ultra-Thin Host principle (Constraint 6).</p>
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
 *         baseline-on-migrate: true
 *         baseline-version: "0"
 *         validate-on-migrate: true
 *         out-of-order: false
 *         locations-prefix: classpath:db/migration/
 *         table-prefix: flyway_schema_history_
 *         fail-on-error: false
 * }</pre>
 *
 * <h3>Architecture Compliance</h3>
 * <p>Belongs to Layer 2C (infra-adapters). The Host layer only declares this configuration in
 * YAML — no Java code needed for migration orchestration.</p>
 *
 * @author Brix Platform Authors
 * @since 3.1.0
 * @see FlywayModuleMigrationAutoConfiguration
 */
@ConfigurationProperties(prefix = "brix.infra.database.migration")
public class FlywayModuleMigrationProperties {

    /**
     * Whether multi-module Flyway migration is enabled.
     * Defaults to {@code true} when this adapter is on the classpath.
     */
    private boolean enabled = true;

    /**
     * List of business module names. Each module corresponds to a Flyway migration script directory
     * under {@code db/migration/{module}/} on the classpath. Migration scripts from each module's
     * {@code -server} JAR are automatically discovered via classpath scanning.
     *
     * <p>Example: {@code ["app-identity", "app-booking", "app-products"]}</p>
     */
    private List<String> modules = new ArrayList<>();

    /**
     * Whether to automatically baseline existing databases on first migrate.
     * Useful for databases that already have tables created by Hibernate ddl-auto.
     */
    private boolean baselineOnMigrate = true;

    /**
     * The version to tag an existing schema with when baselining.
     */
    private String baselineVersion = "0";

    /**
     * Whether to validate applied migrations against available ones on migrate.
     */
    private boolean validateOnMigrate = true;

    /**
     * Whether to allow out-of-order migrations.
     */
    private boolean outOfOrder = false;

    /**
     * Prefix for the classpath location where migration scripts are stored.
     * Each module's scripts should be under {@code {locationsPrefix}{moduleName}/}.
     */
    private String locationsPrefix = "classpath:db/migration/";

    /**
     * Prefix for Flyway history tables. Each module gets its own history table
     * named {@code {tablePrefix}{module_name}} (hyphens replaced with underscores).
     */
    private String tablePrefix = "flyway_schema_history_";

    /**
     * Whether to fail the application startup if any module migration fails.
     * When {@code false}, failed modules are logged and skipped, allowing other modules to proceed.
     */
    private boolean failOnError = false;

    // ==================== Getters and Setters ====================

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getModules() {
        return modules;
    }

    public void setModules(List<String> modules) {
        this.modules = modules;
    }

    public boolean isBaselineOnMigrate() {
        return baselineOnMigrate;
    }

    public void setBaselineOnMigrate(boolean baselineOnMigrate) {
        this.baselineOnMigrate = baselineOnMigrate;
    }

    public String getBaselineVersion() {
        return baselineVersion;
    }

    public void setBaselineVersion(String baselineVersion) {
        this.baselineVersion = baselineVersion;
    }

    public boolean isValidateOnMigrate() {
        return validateOnMigrate;
    }

    public void setValidateOnMigrate(boolean validateOnMigrate) {
        this.validateOnMigrate = validateOnMigrate;
    }

    public boolean isOutOfOrder() {
        return outOfOrder;
    }

    public void setOutOfOrder(boolean outOfOrder) {
        this.outOfOrder = outOfOrder;
    }

    public String getLocationsPrefix() {
        return locationsPrefix;
    }

    public void setLocationsPrefix(String locationsPrefix) {
        this.locationsPrefix = locationsPrefix;
    }

    public String getTablePrefix() {
        return tablePrefix;
    }

    public void setTablePrefix(String tablePrefix) {
        this.tablePrefix = tablePrefix;
    }

    public boolean isFailOnError() {
        return failOnError;
    }

    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }
}
