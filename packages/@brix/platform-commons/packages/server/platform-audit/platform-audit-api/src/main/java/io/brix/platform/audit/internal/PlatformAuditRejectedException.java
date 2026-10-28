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
package io.brix.platform.audit.internal;

import java.util.Objects;

/**
 * The platform audit owner refused a record or a query (super-admin AUDIT-001: never swallowed,
 * never downgraded to a log line). It stays an {@link IllegalArgumentException} so existing
 * callers keep their handling; {@link #code()} is the stable error code.
 *
 * @since 3.2.0
 */
public class PlatformAuditRejectedException extends IllegalArgumentException {

    /** The action is not in the audit action catalog, or the caller may not write it. */
    public static final String ACTION_REFUSED = "platform_audit.action_refused";

    /** A field is missing, too long, or carries content the owner never stores. */
    public static final String FIELD_INVALID = "platform_audit.field_invalid";

    /** The query is out of range. */
    public static final String QUERY_INVALID = "platform_audit.query_invalid";

    private static final long serialVersionUID = 1L;

    private final String code;

    public PlatformAuditRejectedException(String code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    /**
     * Returns the stable error code.
     *
     * @return error code
     */
    public String code() {
        return code;
    }
}
