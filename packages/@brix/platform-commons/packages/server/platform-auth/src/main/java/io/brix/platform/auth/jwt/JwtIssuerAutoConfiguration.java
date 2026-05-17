/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.brix.platform.auth.jwt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.runtime.sdk.capability.JwtIssuerCapability;

/**
 * Auto-configuration that exposes the {@link JwtIssuerCapability} contract
 * backed by {@link JwtIssuerCapabilityImpl}.
 *
 * <p>Activates only when the host opts in via
 * {@code platform.security.jwt.enabled=true} (the default). Hosts that wish
 * to supply their own JWT issuer can do so by defining a {@link JwtIssuerCapability}
 * bean — this auto-config will then back off via {@link ConditionalOnMissingBean}.</p>
 *
 * @since 3.2.0
 */
@AutoConfiguration
@ConditionalOnClass({ ObjectMapper.class })
@ConditionalOnProperty(prefix = "platform.security.jwt", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(JwtProperties.class)
public class JwtIssuerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(JwtIssuerAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(JwtIssuerCapability.class)
    public JwtIssuerCapability jwtIssuerCapability(JwtProperties properties, ObjectMapper objectMapper) {
        log.info("Registering default JwtIssuerCapability (RS256) — privateKeyPath={}",
                properties.getPrivateKeyPath());
        return new JwtIssuerCapabilityImpl(properties, objectMapper);
    }
}
