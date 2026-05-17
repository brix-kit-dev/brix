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
package io.infra.adapter.database.migration;

/**
 * Exception thrown when a module's Flyway migration fails and {@code failOnError} is enabled.
 *
 * <p>This exception wraps the underlying Flyway exception and includes the module name
 * in the message for diagnostic purposes. When this exception propagates, it prevents
 * application startup — ensuring that database schema inconsistencies are caught early.</p>
 *
 * @author Brix Platform Authors
 * @since 3.1.0
 * @see FlywayModuleMigrationRunner
 */
public class FlywayModuleMigrationException extends RuntimeException {

    /**
     * Constructs a new migration exception with the specified detail message and cause.
     *
     * @param message the detail message including the module name and error description
     * @param cause   the underlying exception from the Flyway migration
     */
    public FlywayModuleMigrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
