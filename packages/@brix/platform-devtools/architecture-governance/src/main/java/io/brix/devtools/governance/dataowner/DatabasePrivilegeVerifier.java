/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.dataowner;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifies SQL GRANT statements against Data Owner table boundaries.
 */
public final class DatabasePrivilegeVerifier {

    public static final String CROSS_OWNER_GRANT = "DATA_OWNER_CROSS_GRANT";
    public static final String UNAPPROVED_PRIVILEGE = "DATA_OWNER_UNAPPROVED_PRIVILEGE";

    private static final Pattern GRANT = Pattern.compile(
        "\\bGRANT\\s+(.+?)\\s+ON\\s+(?:TABLE\\s+)?([\\w.\"`]+)\\s+TO\\s+([\\w.\"`-]+)",
        Pattern.CASE_INSENSITIVE);

    private final DataOwnerRegistry registry;

    /**
     * Creates a privilege verifier.
     *
     * @param registry Data Owner registry
     */
    public DatabasePrivilegeVerifier(DataOwnerRegistry registry) {
        this.registry = registry;
    }

    /**
     * Verifies GRANT statements for one owner.
     *
     * @param sql grant SQL
     * @param contract privilege contract
     * @return violations
     */
    public List<SqlMigrationViolation> verify(String sql, DatabasePrivilegeContract contract) {
        registry.requireOwner(contract.ownerId());
        List<SqlMigrationViolation> violations = new ArrayList<>();
        Matcher matcher = GRANT.matcher(sql);
        while (matcher.find()) {
            String table = DataOwnerDefinition.normalizeTable(matcher.group(2));
            registry.ownerOf(table)
                .filter(actualOwner -> !actualOwner.ownerId().equals(contract.ownerId()))
                .ifPresent(actualOwner -> violations.add(new SqlMigrationViolation(
                    CROSS_OWNER_GRANT,
                    "database-privileges",
                    contract.ownerId(),
                    table,
                    "grants " + table + " owned by " + actualOwner.ownerId())));
            for (String privilege : matcher.group(1).split(",")) {
                String normalized = privilege.trim().toUpperCase(Locale.ROOT);
                if (!contract.allowedPrivileges().contains(normalized)) {
                    violations.add(new SqlMigrationViolation(
                        UNAPPROVED_PRIVILEGE,
                        "database-privileges",
                        contract.ownerId(),
                        table,
                        "unapproved privilege " + normalized));
                }
            }
        }
        return List.copyOf(violations);
    }
}
