/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.dataowner;

/**
 * SQL migration violation produced by the Phase 4 Data Owner linter.
 *
 * @param code stable diagnostic code
 * @param migration migration file name
 * @param ownerId owner running the migration
 * @param tableName table that caused the violation
 * @param message human readable diagnostic
 */
public record SqlMigrationViolation(
        String code,
        String migration,
        String ownerId,
        String tableName,
        String message) {
}
