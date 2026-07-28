/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.outbox.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for the JDBC canonical outbox store.
 *
 * @author Brix Platform Authors
 * @since 3.0.10
 */
@ConfigurationProperties(prefix = "brix.outbox.relay.persistence")
public class OutboxRelayPersistenceProperties {

    /**
     * Canonical outbox table owned by the Data Owner and delegated to Relay.
     */
    private String tableName = "platform_tenant_outbox";

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }
}
