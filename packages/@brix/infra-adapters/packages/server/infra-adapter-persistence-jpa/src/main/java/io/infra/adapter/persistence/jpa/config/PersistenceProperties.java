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
package io.infra.adapter.persistence.jpa.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Host Profile settings of the owner persistence adapter ({@code brix.persistence.*}).
 *
 * <p>The values below are the profile defaults; a Host Profile overrides them per environment.</p>
 */
@ConfigurationProperties(prefix = "brix.persistence")
public class PersistenceProperties {

    /**
     * Whether owner persistence units are provided at all.
     */
    private boolean enabled = true;

    /**
     * Deadline of every owner transaction (root blueprint BRX-RS-DATA-011). Statements, including
     * pessimistic lock waits, are cancelled when it expires.
     */
    private Duration transactionTimeout = Duration.ofSeconds(30);

    /**
     * Whether each owner's tables are validated against the migrated schema at startup
     * (root blueprint BRX-RS-DATA-031).
     */
    private boolean schemaValidation = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getTransactionTimeout() {
        return transactionTimeout;
    }

    public void setTransactionTimeout(Duration transactionTimeout) {
        this.transactionTimeout = transactionTimeout;
    }

    public boolean isSchemaValidation() {
        return schemaValidation;
    }

    public void setSchemaValidation(boolean schemaValidation) {
        this.schemaValidation = schemaValidation;
    }
}
