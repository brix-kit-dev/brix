/*
 * Copyright 2026 Runtime SDK Authors
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
package io.runtime.sdk.error;

import java.util.Map;

/**
 * The platform audit record of a registered cross-tenant policy could not be written, so the use
 * failed (root BRX-RS-DATA-047). The code belongs to the runtime's policy audit, not to a
 * capability, so it has a namespace of its own (BRX-RS-ERROR-012), as {@code query.} and
 * {@code platform.} do.
 *
 * @since 3.3.0
 */
public final class PolicyAuditException extends BrixException {

    private static final long serialVersionUID = 1L;

    /** Stable codes of the runtime policy audit. */
    public enum Code implements ErrorCode {
        /** The record of a policy use was not stored. */
        POLICY_USE_WRITE_FAILED("audit.policy_use_write_failed");

        private final String wireCode;

        Code(String wireCode) {
            this.wireCode = wireCode;
        }

        @Override
        public String wireCode() {
            return wireCode;
        }
    }

    /**
     * Creates the failure.
     *
     * @param safeParameters policy id and a reason from a fixed set; no tenant, key or SQL
     */
    public PolicyAuditException(Map<String, String> safeParameters) {
        this(safeParameters, null);
    }

    /**
     * Creates the failure with its cause.
     *
     * @param safeParameters policy id and a reason from a fixed set; no tenant, key or SQL
     * @param cause what kept the record from being stored
     */
    public PolicyAuditException(Map<String, String> safeParameters, Throwable cause) {
        super(Code.POLICY_USE_WRITE_FAILED, safeParameters, cause);
    }

    /**
     * Returns the stable code.
     *
     * @return {@link Code#POLICY_USE_WRITE_FAILED}
     */
    public Code code() {
        return Code.POLICY_USE_WRITE_FAILED;
    }
}
