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
package io.runtime.sdk.capability;

import java.util.Map;

import io.runtime.sdk.annotation.Since;
import io.runtime.sdk.error.BrixException;
import io.runtime.sdk.error.ErrorCode;

/**
 * Persistence Capability Failure
 *
 * <p>Stable error codes for {@link PersistenceCapability} and {@link UnitOfWork} (root blueprint
 * BRX-RS-DATA-042). The persistence adapter translates ORM, JDBC and database failures into these
 * codes so that plugins never depend on vendor exception types or SQL states. Safe parameters never
 * carry entity field values, SQL text or other data that could contain personal information.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
@Since("3.3.0")
public class PersistenceCapabilityException extends BrixException {

    /**
     * Stable persistence error codes.
     */
    public enum Code implements ErrorCode {
        /** A versioned entity was modified concurrently. */
        OPTIMISTIC_LOCK_CONFLICT("persistence.optimistic_lock_conflict"),
        /** A unique constraint rejected the write. */
        UNIQUE_CONSTRAINT_VIOLATION("persistence.unique_constraint_violation"),
        /** A foreign key, check or not-null constraint rejected the write. */
        DATA_INTEGRITY_VIOLATION("persistence.data_integrity_violation"),
        /** A pessimistic lock could not be acquired within the configured bound. */
        LOCK_TIMEOUT("persistence.lock_timeout"),
        /** A statement or the unit of work exceeded its deadline. */
        TIMEOUT("persistence.timeout"),
        /** A referenced entity does not exist. */
        ENTITY_NOT_FOUND("persistence.entity_not_found"),
        /** The database cannot be reached or refused the connection. */
        UNAVAILABLE("persistence.unavailable"),
        /** Repository access happened outside a unit of work of the repository's owner. */
        UNIT_OF_WORK_MISSING("persistence.unit_of_work_missing"),
        /** A write was attempted inside a read-only scope. */
        READ_ONLY_VIOLATION("persistence.read_only_violation"),
        /** The requested repository violates the owner boundary or the persistence API whitelist. */
        REPOSITORY_REJECTED("persistence.repository_rejected"),
        /** Any other persistence failure. */
        FAILURE("persistence.failure");

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
     * Creates a persistence failure.
     *
     * @param errorCode stable error code
     * @param safeParameters parameters approved for logs and error envelopes
     * @param cause underlying failure, kept for diagnostics only
     */
    public PersistenceCapabilityException(Code errorCode, Map<String, String> safeParameters, Throwable cause) {
        super(errorCode, safeParameters, cause);
    }

    /**
     * Creates a persistence failure without an underlying cause.
     *
     * @param errorCode stable error code
     * @param safeParameters parameters approved for logs and error envelopes
     */
    public PersistenceCapabilityException(Code errorCode, Map<String, String> safeParameters) {
        this(errorCode, safeParameters, null);
    }

    /**
     * Returns the typed persistence error code.
     *
     * @return the error code
     */
    public Code code() {
        return (Code) errorCode();
    }
}
