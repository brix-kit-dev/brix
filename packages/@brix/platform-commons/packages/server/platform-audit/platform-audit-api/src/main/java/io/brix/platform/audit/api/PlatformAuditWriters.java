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
package io.brix.platform.audit.api;

/**
 * Registered writers of {@code sys_platform_audit_log}, stored in its {@code writer} column.
 *
 * @since 3.2.0
 */
public final class PlatformAuditWriters {

    /** The platform audit owner, through its internal contract. */
    public static final String OWNER = "platform-audit";

    /** The runtime policy audit writer, a registered custodian (root BRX-RS-DATA-047). */
    public static final String RUNTIME_TENANT_POLICY = "runtime.tenant-policy-audit";

    /** platform-identity's own rows during the split (exemption EX-2026-003); the column default. */
    public static final String PLATFORM_IDENTITY = "platform-identity";

    private PlatformAuditWriters() {
    }
}
