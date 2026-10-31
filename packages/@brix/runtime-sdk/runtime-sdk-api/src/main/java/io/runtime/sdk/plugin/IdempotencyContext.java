/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.plugin;

import io.runtime.sdk.annotation.Since;

/**
 * The business key a handler's work is deduplicated by (root BRX-RS-MANIFEST-028, MESSAGE-014): the runtime keeps
 * the result of the first execution under it and does not run the handler again for the same key.
 *
 * @param scope what the key is unique within, as the sender named it
 * @param key the key
 * @since 3.3.0
 */
@Since("3.3.0")
public record IdempotencyContext(String scope, String key) {

    /**
     * Creates the context; the runtime builds it.
     */
    public IdempotencyContext {
        if (scope == null || scope.isBlank()) {
            throw new IllegalArgumentException("scope must not be blank");
        }
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
    }
}
