/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.outbox.config;

import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import io.infra.adapter.outbox.JdbcCommandIdempotencyStore;
import io.infra.adapter.outbox.JdbcCommandInboxStore;
import io.runtime.orchestrator.command.CommandIdempotencyStore;
import io.runtime.orchestrator.command.CommandInboxStore;

/**
 * Auto-configuration for JDBC command inbox and idempotency persistence.
 *
 * @author Brix Platform Authors
 * @since 3.0.10
 */
@AutoConfiguration
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "brix.command.persistence", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CommandPersistenceProperties.class)
public class CommandPersistenceAutoConfiguration {

    /**
     * Creates the command inbox store.
     *
     * @param dataSource owner datasource selected by Host composition
     * @param properties command persistence properties
     * @return command inbox store
     */
    @Bean
    @ConditionalOnMissingBean
    public CommandInboxStore commandInboxStore(
            DataSource dataSource,
            CommandPersistenceProperties properties) {
        return new JdbcCommandInboxStore(new JdbcTemplate(dataSource), properties.getInboxTableName());
    }

    /**
     * Creates the command business idempotency store.
     *
     * @param dataSource owner datasource selected by Host composition
     * @param properties command persistence properties
     * @return command idempotency store
     */
    @Bean
    @ConditionalOnMissingBean
    public CommandIdempotencyStore commandIdempotencyStore(
            DataSource dataSource,
            CommandPersistenceProperties properties) {
        return new JdbcCommandIdempotencyStore(new JdbcTemplate(dataSource), properties.getIdempotencyTableName());
    }
}
