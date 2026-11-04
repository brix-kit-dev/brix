/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * The scope column of the idempotency tables (idempotency design §4.1): a scope the runtime derived, for a command or
 * an endpoint, fits it or is replaced by its SHA-256, which stays unique.
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class IdempotencyScopes {

    /** The width of the {@code idempotency_scope} columns. */
    private static final int COLUMN = 128;

    private IdempotencyScopes() {
    }

    /**
     * Returns a scope as the tables keep it.
     *
     * @param scope the derived scope
     * @return the scope, or its SHA-256 when it is longer than the column
     */
    public static String bounded(String scope) {
        if (scope.length() <= COLUMN) {
            return scope;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(scope.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException absent) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", absent);
        }
    }
}
