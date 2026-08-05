/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.identity.config;

import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;

import io.brix.platform.auth.flow.MfaLoginSupport;
import io.brix.platform.auth.jwt.JwtValidator;
import io.brix.platform.identity.bootstrap.SuperAdminBootstrapProperties;
import io.brix.platform.identity.core.IdGenerator;
import io.brix.platform.identity.core.SnowflakeIdGenerator;
import io.brix.platform.identity.internal.PlatformBootstrapAdministration;
import io.brix.platform.identity.internal.PlatformIdentityAdministration;
import io.brix.platform.identity.repository.BootstrapStateRepository;
import io.brix.platform.identity.repository.IdentityRepository;
import io.brix.platform.identity.repository.PlatformAdminRepository;
import io.brix.platform.identity.repository.SetupTokenRepository;
import io.brix.platform.identity.service.AuditService;
import io.brix.platform.identity.service.BootstrapCompletionListener;
import io.brix.platform.identity.service.IdentityFirstOwnerInviteeSetupCapabilityImpl;
import io.brix.platform.identity.service.IdentityAccountCapabilityImpl;
import io.brix.platform.identity.service.JdbcPlatformIdentityAuditService;
import io.brix.platform.identity.service.PlatformBootstrapAdministrationService;
import io.brix.platform.identity.service.PlatformIdentityAdministrationService;
import io.brix.platform.identity.service.PlatformMfaLoginSupport;
import io.runtime.sdk.capability.FirstOwnerInviteeIdentitySetupCapability;
import io.runtime.sdk.capability.IdentityAccountCapability;
import io.runtime.sdk.capability.JwtIssuerCapability;
import io.runtime.sdk.capability.NotificationCapability;
import io.runtime.sdk.capability.PasswordCapability;
import io.runtime.sdk.capability.SecretEncryptionCapability;
import io.runtime.sdk.capability.TotpCapability;

/**
 * Auto-configuration anchor for the platform-identity source module.
 */
@AutoConfiguration
@EnableJpaRepositories(basePackages = "io.brix.platform.identity.repository")
@EntityScan(basePackages = "io.brix.platform.identity.entity")
@EnableConfigurationProperties(SuperAdminBootstrapProperties.class)
public class PlatformIdentityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(IdGenerator.class)
    public IdGenerator platformIdentityIdGenerator(
            @Value("${brix.identity.worker-id:1}") long workerId) {
        return new SnowflakeIdGenerator(workerId);
    }

    @Bean
    @ConditionalOnMissingBean(AuditService.class)
    public AuditService platformIdentityAuditService(JdbcTemplate jdbcTemplate, IdGenerator idGenerator) {
        return new JdbcPlatformIdentityAuditService(jdbcTemplate, idGenerator);
    }

    @Bean
    @ConditionalOnMissingBean(IdentityAccountCapability.class)
    public IdentityAccountCapability identityAccountCapability(
            IdentityRepository identityRepository,
            PlatformAdminRepository platformAdminRepository,
            ObjectProvider<AuditService> auditServiceProvider) {
        return new IdentityAccountCapabilityImpl(
                identityRepository,
                platformAdminRepository,
                Optional.ofNullable(auditServiceProvider.getIfAvailable()));
    }

    @Bean
    @ConditionalOnMissingBean(FirstOwnerInviteeIdentitySetupCapability.class)
    public FirstOwnerInviteeIdentitySetupCapability firstOwnerInviteeIdentitySetupCapability(
            IdentityRepository identityRepository,
            PlatformAdminRepository platformAdminRepository,
            SetupTokenRepository setupTokenRepository,
            ObjectProvider<NotificationCapability> notificationCapabilityProvider,
            IdGenerator idGenerator,
            @Value("${brix.identity.first-owner-invitee-setup.setup-base-url:}")
                    String setupBaseUrl) {
        return new IdentityFirstOwnerInviteeSetupCapabilityImpl(
                identityRepository,
                platformAdminRepository,
                setupTokenRepository,
                Optional.ofNullable(notificationCapabilityProvider.getIfAvailable()),
                idGenerator,
                setupBaseUrl);
    }

    @Bean
    @ConditionalOnMissingBean(BootstrapCompletionListener.class)
    public BootstrapCompletionListener bootstrapCompletionListener(
            BootstrapStateRepository bootstrapStateRepository,
            IdentityRepository identityRepository,
            PlatformAdminRepository platformAdminRepository,
            AuditService auditService) {
        return new BootstrapCompletionListener(
                bootstrapStateRepository,
                identityRepository,
                platformAdminRepository,
                auditService);
    }

    @Bean
    @ConditionalOnMissingBean(PlatformBootstrapAdministration.class)
    public PlatformBootstrapAdministration platformBootstrapAdministration(
            SuperAdminBootstrapProperties properties,
            BootstrapStateRepository bootstrapStateRepository,
            IdentityRepository identityRepository,
            PlatformAdminRepository platformAdminRepository,
            SetupTokenRepository setupTokenRepository,
            IdGenerator idGenerator,
            ObjectProvider<JwtIssuerCapability> jwtIssuerCapabilityProvider,
            ObjectProvider<NotificationCapability> notificationCapabilityProvider,
            AuditService auditService) {
        return new PlatformBootstrapAdministrationService(
                properties,
                bootstrapStateRepository,
                identityRepository,
                platformAdminRepository,
                setupTokenRepository,
                idGenerator,
                Optional.ofNullable(jwtIssuerCapabilityProvider.getIfAvailable()),
                Optional.ofNullable(notificationCapabilityProvider.getIfAvailable()),
                auditService);
    }

    @Bean
    @ConditionalOnMissingBean(PlatformIdentityAdministration.class)
    public PlatformIdentityAdministration platformIdentityAdministration(
            SetupTokenRepository setupTokenRepository,
            IdentityRepository identityRepository,
            PlatformAdminRepository platformAdminRepository,
            ObjectProvider<PasswordCapability> passwordCapabilityProvider,
            ObjectProvider<TotpCapability> totpCapabilityProvider,
            ObjectProvider<SecretEncryptionCapability> secretEncryptionCapabilityProvider,
            BootstrapCompletionListener bootstrapCompletionListener,
            ApplicationEventPublisher eventPublisher,
            AuditService auditService) {
        return new PlatformIdentityAdministrationService(
                setupTokenRepository,
                identityRepository,
                platformAdminRepository,
                Optional.ofNullable(passwordCapabilityProvider.getIfAvailable()),
                Optional.ofNullable(totpCapabilityProvider.getIfAvailable()),
                Optional.ofNullable(secretEncryptionCapabilityProvider.getIfAvailable()),
                bootstrapCompletionListener,
                eventPublisher,
                auditService);
    }

    @Bean
    @ConditionalOnMissingBean(MfaLoginSupport.class)
    public MfaLoginSupport platformMfaLoginSupport(
            IdentityRepository identityRepository,
            PlatformAdminRepository platformAdminRepository,
            ObjectProvider<JwtValidator> jwtValidatorProvider,
            ObjectProvider<JwtIssuerCapability> jwtIssuerCapabilityProvider,
            ObjectProvider<TotpCapability> totpCapabilityProvider,
            ObjectProvider<SecretEncryptionCapability> secretEncryptionCapabilityProvider) {
        return new PlatformMfaLoginSupport(
                identityRepository,
                platformAdminRepository,
                Optional.ofNullable(jwtValidatorProvider.getIfAvailable()),
                Optional.ofNullable(jwtIssuerCapabilityProvider.getIfAvailable()),
                Optional.ofNullable(totpCapabilityProvider.getIfAvailable()),
                Optional.ofNullable(secretEncryptionCapabilityProvider.getIfAvailable()));
    }
}
