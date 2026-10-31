/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.error;

import io.runtime.sdk.annotation.Since;

/**
 * What kind of failure an {@link ErrorCode} is, independent of any transport (root BRX-RS-MANIFEST-022; standard-link
 * plan L7.3). The runtime answers each with its protocol's status; over HTTP:
 * {@link #INVALID} 400, {@link #DENIED} 403, {@link #NOT_FOUND} 404, {@link #CONFLICT} 409,
 * {@link #UNAVAILABLE} 503, {@link #DEADLINE_EXCEEDED} 504 and {@link #INTERNAL} 500. A later minor version may add
 * categories.
 *
 * @since 3.3.0
 */
@Since("3.3.0")
public enum ErrorCategory {

    /** The request is not valid: the caller must change it. */
    INVALID,
    /** The caller may not do this. */
    DENIED,
    /** What the request names does not exist, or the caller may not know that it does. */
    NOT_FOUND,
    /** The request conflicts with the current state, such as a duplicate or a state that does not allow it. */
    CONFLICT,
    /** A dependency is not available now; the same request may succeed later. */
    UNAVAILABLE,
    /** The work did not finish before its deadline. */
    DEADLINE_EXCEEDED,
    /** The failure is the platform's or the plugin's own; the caller cannot fix it. */
    INTERNAL
}
