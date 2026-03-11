/*
 * Copyright 2026 Brix Platform Authors
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
package io.brix.platform.starter.flyway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import io.brix.platform.starter.config.FlywayExtProperties;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flyway Version Conflict Checker
 * 
 * <p>Checks for Flyway migration script version conflicts during application startup.</p>
 * 
 * <p>Detection Rules:</p>
 * <ul>
 *   <li>Version numbers cannot be duplicated</li>
 *   <li>Version numbers must conform to standard format</li>
 *   <li>Logs warnings when conflicts are detected</li>
 * </ul>
 * 
 * <p>Standard Version Format:</p>
 * <pre>
 * V{plugin_prefix}_{version_number}__{description}.sql
 * 
 * Correct examples:
 * - V001_001__init_schema.sql
 * - V001_002__add_column.sql
 * 
 * Incorrect examples:
 * - V1__init_schema.sql  (not using plugin prefix)
 * - V001_001_init.sql    (only one underscore before description)
 * </pre>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 * @see FlywayExtProperties
 */
public class FlywayConflictChecker {
    
    private static final Logger log = LoggerFactory.getLogger(FlywayConflictChecker.class);
    
    /**
     * Standard version pattern: V{plugin_prefix}_{version_number}__{description}.sql
     * 
     * <p>Example: V001_001__init_schema.sql</p>
     */
    private static final Pattern VERSIONED_PATTERN = Pattern.compile(
        "V(\\d+)_(\\d+)__(.+)\\.sql", Pattern.CASE_INSENSITIVE);
    
    /**
     * Simple version pattern: V{version_number}__{description}.sql
     * 
     * <p>Example: V1__init_schema.sql</p>
     */
    private static final Pattern SIMPLE_PATTERN = Pattern.compile(
        "V(\\d+)__(.+)\\.sql", Pattern.CASE_INSENSITIVE);
    
    /**
     * Flyway extension configuration
     */
    private final FlywayExtProperties flywayProperties;
    
    /**
     * Resource resolver
     */
    private final PathMatchingResourcePatternResolver resolver;
    
    /**
     * Constructor
     * 
     * @param flywayProperties Flyway extension configuration
     */
    public FlywayConflictChecker(FlywayExtProperties flywayProperties) {
        this.flywayProperties = flywayProperties;
        this.resolver = new PathMatchingResourcePatternResolver();
    }
    
    /**
     * Execute conflict detection
     * 
     * @return Detection result
     */
    public ConflictCheckResult check() {
        if (!flywayProperties.isConflictCheckEnabled()) {
            log.debug("[FlywayConflictChecker] Conflict detection is disabled");
            return ConflictCheckResult.disabled();
        }
        
        log.info("[FlywayConflictChecker] Starting Flyway version conflict detection...");
        
        try {
            List<String> migrations = scanMigrations();
            return analyzeConflicts(migrations);
        } catch (IOException e) {
            log.error("[FlywayConflictChecker] Failed to scan migration scripts: {}", e.getMessage());
            return ConflictCheckResult.error(e.getMessage());
        }
    }
    
    /**
     * Scan migration scripts
     * 
     * @return List of migration script filenames
     */
    private List<String> scanMigrations() throws IOException {
        String location = flywayProperties.getLocations();
        if (location == null || location.isEmpty()) {
            location = "classpath:db/migration";
        }
        
        // Convert to resource pattern
        String pattern = location.replace("classpath:", "classpath:") + "/*.sql";
        
        Resource[] resources = resolver.getResources(pattern);
        List<String> migrations = new ArrayList<>();
        
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename != null && filename.startsWith("V")) {
                migrations.add(filename);
            }
        }
        
        log.debug("[FlywayConflictChecker] Scanned {} migration scripts", migrations.size());
        return migrations;
    }
    
    /**
     * Analyze version conflicts
     * 
     * @param migrations List of migration scripts
     * @return Detection result
     */
    private ConflictCheckResult analyzeConflicts(List<String> migrations) {
        Map<String, List<String>> versionToFiles = new HashMap<>();
        List<String> nonCompliantFiles = new ArrayList<>();
        
        for (String migration : migrations) {
            Matcher versionedMatcher = VERSIONED_PATTERN.matcher(migration);
            Matcher simpleMatcher = SIMPLE_PATTERN.matcher(migration);
            
            if (versionedMatcher.matches()) {
                // Standard format: extract full version number
                String version = versionedMatcher.group(1) + "_" + versionedMatcher.group(2);
                versionToFiles.computeIfAbsent(version, k -> new ArrayList<>()).add(migration);
            } else if (simpleMatcher.matches()) {
                // Simple format: mark as non-compliant
                String version = simpleMatcher.group(1);
                versionToFiles.computeIfAbsent(version, k -> new ArrayList<>()).add(migration);
                nonCompliantFiles.add(migration);
            }
        }
        
        // Check conflicts
        List<String> conflicts = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : versionToFiles.entrySet()) {
            if (entry.getValue().size() > 1) {
                conflicts.add(String.format("Version %s: %s", 
                    entry.getKey(), String.join(", ", entry.getValue())));
            }
        }
        
        // Output warnings
        if (!conflicts.isEmpty()) {
            log.warn("[FlywayConflictChecker] Version conflict detected");
            for (String conflict : conflicts) {
                log.warn("[FlywayConflictChecker]   - {}", conflict);
            }
        }
        
        if (!nonCompliantFiles.isEmpty()) {
            log.warn("[FlywayConflictChecker] The following files do not use standard version format (should use V{plugin_prefix}_{version}__{description}.sql):");
            for (String file : nonCompliantFiles) {
                log.warn("[FlywayConflictChecker]   - {}", file);
            }
        }
        
        if (conflicts.isEmpty() && nonCompliantFiles.isEmpty()) {
            log.info("[FlywayConflictChecker] No version conflicts detected, all scripts are compliant");
        }
        
        return new ConflictCheckResult(
            conflicts.isEmpty() && nonCompliantFiles.isEmpty(),
            conflicts,
            nonCompliantFiles,
            null
        );
    }
    
    /**
     * Conflict detection result
     */
    public record ConflictCheckResult(
        boolean passed,
        List<String> conflicts,
        List<String> nonCompliantFiles,
        String error
    ) {
        public static ConflictCheckResult disabled() {
            return new ConflictCheckResult(true, List.of(), List.of(), null);
        }
        
        public static ConflictCheckResult error(String error) {
            return new ConflictCheckResult(false, List.of(), List.of(), error);
        }
        
        public boolean hasConflicts() {
            return conflicts != null && !conflicts.isEmpty();
        }
        
        public boolean hasNonCompliantFiles() {
            return nonCompliantFiles != null && !nonCompliantFiles.isEmpty();
        }
    }
}
