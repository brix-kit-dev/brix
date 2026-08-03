/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.dataowner;

/**
 * One SQL migration file under a Data Owner migration location.
 *
 * @param name migration file name or relative path
 * @param ownerId owner that executes the migration
 * @param sql migration SQL text
 */
public record SqlMigrationDocument(String name, String ownerId, String sql) {

    public SqlMigrationDocument {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("sql must not be blank");
        }
        name = name.trim();
        ownerId = ownerId.trim();
    }
}
