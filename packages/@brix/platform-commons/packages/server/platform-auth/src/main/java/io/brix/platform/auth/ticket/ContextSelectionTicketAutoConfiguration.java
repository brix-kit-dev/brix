/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.auth.ticket;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import jakarta.persistence.EntityManager;

/**
 * Auto-configuration for persistent context selection tickets.
 *
 * @since 3.2.2
 */
@AutoConfiguration
@ConditionalOnClass(EntityManager.class)
@EntityScan(basePackageClasses = ContextSelectionTicket.class)
@EnableJpaRepositories(basePackageClasses = ContextSelectionTicketRepository.class)
public class ContextSelectionTicketAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ContextSelectionTicketService.class)
    public ContextSelectionTicketService contextSelectionTicketService(
            ContextSelectionTicketRepository repository) {
        return new ContextSelectionTicketService(repository);
    }
}
