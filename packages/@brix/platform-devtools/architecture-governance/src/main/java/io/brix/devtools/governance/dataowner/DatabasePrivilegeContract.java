/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.devtools.governance.dataowner;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Declares the database roles and privileges allowed for one Data Owner.
 *
 * @param ownerId Data Owner id
 * @param writerRole database role used by the Owner writer
 * @param allowedPrivileges privileges allowed for owned tables
 */
public record DatabasePrivilegeContract(
        String ownerId,
        String writerRole,
        Set<String> allowedPrivileges) {

    public DatabasePrivilegeContract {
        if (ownerId == null || ownerId.isBlank()) {
            throw new IllegalArgumentException("ownerId must not be blank");
        }
        if (writerRole == null || writerRole.isBlank()) {
            throw new IllegalArgumentException("writerRole must not be blank");
        }
        if (allowedPrivileges == null || allowedPrivileges.isEmpty()) {
            throw new IllegalArgumentException("allowedPrivileges must not be empty");
        }
        ownerId = ownerId.trim();
        writerRole = writerRole.trim();
        allowedPrivileges = allowedPrivileges.stream()
            .map(value -> value.toUpperCase(Locale.ROOT))
            .collect(Collectors.toUnmodifiableSet());
    }
}
