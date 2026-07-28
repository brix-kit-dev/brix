/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.outbox.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for JDBC command inbox and idempotency stores.
 *
 * @author Brix Platform Authors
 * @since 3.0.10
 */
@ConfigurationProperties(prefix = "brix.command.persistence")
public class CommandPersistenceProperties {

    /**
     * Canonical inbox table owned by the Command Handler Owner.
     */
    private String inboxTableName = "platform_tenant_inbox";

    /**
     * Business idempotency table owned by the Command Handler Owner.
     */
    private String idempotencyTableName = "platform_tenant_command_idempotency";

    public String getInboxTableName() {
        return inboxTableName;
    }

    public void setInboxTableName(String inboxTableName) {
        this.inboxTableName = inboxTableName;
    }

    public String getIdempotencyTableName() {
        return idempotencyTableName;
    }

    public void setIdempotencyTableName(String idempotencyTableName) {
        this.idempotencyTableName = idempotencyTableName;
    }
}
