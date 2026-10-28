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

/**
 * Internal contract of the platform audit Data Owner (super-admin §1.3a, §2.1): appends platform
 * audit records and answers the platform console's restricted query.
 *
 * <p>Only platform-operational modules consume it, through {@code OperationalContext}. The runtime
 * suspends the caller's transaction and the owner commits each record in its own local transaction
 * (BRX-SA-OWNER-004). The runtime policy audit writer does not use this contract: it writes the same
 * table as a registered custodian (root BRX-RS-DATA-045, DATA-047).</p>
 *
 * @since 3.2.0
 */
public interface PlatformAuditAdministration {

    /** Stable contract identifier declared in the Runtime descriptor. */
    String CONTRACT_ID = "brix.internal.platform.audit";

    /**
     * Exact contract version. 1.1.0 (Minor, root CAP-019) adds the FIRST_OWNER invitation actions and
     * the result filter of {@link PlatformAuditQuery}.
     */
    String CONTRACT_VERSION = "1.1.0";

    /**
     * Appends one platform audit record written by the owner itself.
     *
     * @param command typed record fields; free text, credentials, full e-mail addresses and URLs are refused
     * @return the record id
     * @throws PlatformAuditRejectedException when the record breaks the owner's rules
     */
    PlatformAuditRecordId record(RecordPlatformAuditCommand command);

    /**
     * Reads platform audit records, newest first, for the platform console.
     *
     * @param query page and filters
     * @return one page of records, including records written before the owner existed
     */
    PlatformAuditPage query(PlatformAuditQuery query);
}
