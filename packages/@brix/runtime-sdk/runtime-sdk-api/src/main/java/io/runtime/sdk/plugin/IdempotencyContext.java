/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.plugin;

import io.runtime.sdk.annotation.Since;

/**
 * The key that keeps a handler's work from being repeated (root BRX-RS-MANIFEST-028, MESSAGE-014). Where the runtime
 * records the key, it records which work took it and how that work ended. Only an endpoint's answer is kept:
 * <ul>
 *   <li>a command's key is its sender's business key. The first command that succeeds takes it, and the handler does
 *   not run again for it: a later command with the key is a duplicate when its content is the same and a conflict
 *   when it is not. A command the handler rejects takes no key, so a later command with it runs (standard-link plan
 *   points 7 to 9);</li>
 *   <li>a task's key is its trigger: with {@code task-trigger}, one host claims each trigger and runs it;</li>
 *   <li>an endpoint's key is the caller's {@code Idempotency-Key}. The first call that succeeds takes it, and a later
 *   one gets its answer again; a call that fails takes no key (standard-link plan point 10a).</li>
 * </ul>
 *
 * @param scope what the key is unique within: the scope the sender named, the task id, or the caller and tenant
 * @param key the key: the sender's business key, the trigger id, or the caller's key
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
