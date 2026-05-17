/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.auth.refresh;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import io.runtime.sdk.capability.RefreshTokenCapability;
import jakarta.persistence.EntityManager;

/**
 * Auto-configuration for the DB-backed {@link RefreshTokenCapability} (A2).
 *
 * <p>Self-contained: scans {@link StoredRefreshToken} as a JPA entity and
 * registers {@link RefreshTokenRepository} as a Spring Data JPA repository so
 * hosts only need to declare the {@code platform-auth} dependency without
 * extending their own {@code @EntityScan}/{@code @EnableJpaRepositories}.</p>
 *
 * <p>Hosts that supply their own {@link RefreshTokenCapability} bean (e.g. Redis-backed)
 * will suppress the default factory bean via {@link ConditionalOnMissingBean}.</p>
 *
 * @since 3.2.1
 */
@AutoConfiguration
@ConditionalOnClass(EntityManager.class)
@EntityScan(basePackageClasses = StoredRefreshToken.class)
@EnableJpaRepositories(basePackageClasses = RefreshTokenRepository.class)
public class RefreshTokenAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RefreshTokenCapability.class)
    public RefreshTokenCapability refreshTokenCapability(RefreshTokenRepository repository) {
        return new RefreshTokenCapabilityImpl(repository);
    }
}
