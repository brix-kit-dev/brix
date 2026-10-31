/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.identity.config;

import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

import io.brix.platform.identity.bootstrap.SuperAdminBootstrapProperties;
import io.brix.platform.identity.core.IdGenerator;
import io.brix.platform.identity.core.SnowflakeIdGenerator;
import io.brix.platform.identity.internal.PlatformBootstrapAdministration;
import io.brix.platform.identity.internal.PlatformIdentityAdministration;
import io.brix.platform.identity.repository.BootstrapStateRepository;
import io.brix.platform.identity.repository.IdentityRepository;
import io.brix.platform.identity.repository.PlatformAdminRepository;
import io.brix.platform.identity.repository.SetupTokenRepository;
import io.brix.platform.identity.security.PasswordPolicy;
import io.brix.platform.identity.service.BootstrapCompletionListener;
import io.brix.platform.identity.service.IdentityAccountCapabilityImpl;
import io.brix.platform.identity.service.IdentityAuditFacts;
import io.brix.platform.identity.service.IdentityFirstOwnerInviteeSetupCapabilityImpl;
import io.brix.platform.identity.service.PlatformBootstrapAdministrationService;
import io.brix.platform.identity.service.PlatformIdentityAdministrationService;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.FirstOwnerInviteeIdentitySetupCapability;
import io.runtime.sdk.capability.IdentityAccountCapability;
import io.runtime.sdk.capability.JwtIssuerCapability;
import io.runtime.sdk.capability.NotificationCapability;
import io.runtime.sdk.capability.PasswordCapability;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.capability.SecretEncryptionCapability;
import io.runtime.sdk.capability.TotpCapability;

/**
 * Auto-configuration anchor for the platform-identity source module.
 *
 * <p>Repositories come from the identity owner's {@link PersistenceCapability}, never from a
 * framework repository scan (root blueprint BRX-RS-DATA-026). The persistence adapter derives the
 * owner from this module's artifact, so the injected capability can only reach identity data.
 * Transactions are opened by the runtime around internal contract calls and by the owner unit of
 * work in the capabilities other modules call directly; nothing here uses framework transactions.</p>
 *
 * <p>Platform audit facts go to the identity owner's outbox in the same unit of work, through the
 * persistence adapter's owner outbox bus, which is injected only by its qualifier (P4 design §4.2,
 * §10.2). The platform audit owner writes the records.</p>
 */
@AutoConfiguration
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
    @ConditionalOnMissingBean(IdentityAuditFacts.class)
    public IdentityAuditFacts platformIdentityAuditFacts(@Qualifier("ownerOutboxEventBus") EventBusCapability outbox) {
        return new IdentityAuditFacts(outbox);
    }

    /** New passwords per identity-auth BRX-IA-CRED-001; the limits belong to the security profile. */
    @Bean
    @ConditionalOnMissingBean(PasswordPolicy.class)
    public PasswordPolicy platformIdentityPasswordPolicy(
            @Value("${brix.identity.password.min-length:" + PasswordPolicy.MIN_LENGTH_WITH_SECOND_FACTOR + "}")
                    int minLength,
            @Value("${brix.identity.password.max-length:" + PasswordPolicy.DEFAULT_MAX_LENGTH + "}")
                    int maxLength) {
        return new PasswordPolicy(minLength, maxLength);
    }

    @Bean
    @ConditionalOnMissingBean(IdentityAccountCapability.class)
    public IdentityAccountCapability identityAccountCapability(
            PersistenceCapability persistence,
            ObjectProvider<IdentityAuditFacts> auditFactsProvider,
            ObjectProvider<TotpCapability> totpCapabilityProvider,
            ObjectProvider<SecretEncryptionCapability> secretEncryptionProvider,
            PasswordPolicy passwordPolicy,
            @Qualifier("ownerOutboxEventBus") EventBusCapability outbox) {
        return new IdentityAccountCapabilityImpl(
                persistence.repository(IdentityRepository.class),
                persistence.repository(PlatformAdminRepository.class),
                Optional.ofNullable(auditFactsProvider.getIfAvailable()),
                persistence.unitOfWork(),
                Optional.ofNullable(totpCapabilityProvider.getIfAvailable()),
                Optional.ofNullable(secretEncryptionProvider.getIfAvailable()),
                new IdentityAccountCapabilityImpl.CredentialSetup(
                        persistence.repository(SetupTokenRepository.class), passwordPolicy, outbox));
    }

    @Bean
    @ConditionalOnMissingBean(FirstOwnerInviteeIdentitySetupCapability.class)
    public FirstOwnerInviteeIdentitySetupCapability firstOwnerInviteeIdentitySetupCapability(
            PersistenceCapability persistence,
            ObjectProvider<NotificationCapability> notificationCapabilityProvider,
            IdGenerator idGenerator,
            @Value("${brix.identity.first-owner-invitee-setup.setup-base-url:}")
                    String setupBaseUrl) {
        return new IdentityFirstOwnerInviteeSetupCapabilityImpl(
                persistence.repository(IdentityRepository.class),
                persistence.repository(PlatformAdminRepository.class),
                persistence.repository(SetupTokenRepository.class),
                Optional.ofNullable(notificationCapabilityProvider.getIfAvailable()),
                idGenerator,
                setupBaseUrl,
                persistence.unitOfWork());
    }

    @Bean
    @ConditionalOnMissingBean(BootstrapCompletionListener.class)
    public BootstrapCompletionListener bootstrapCompletionListener(
            PersistenceCapability persistence,
            IdentityAuditFacts auditFacts) {
        return new BootstrapCompletionListener(
                persistence.repository(BootstrapStateRepository.class),
                persistence.repository(IdentityRepository.class),
                persistence.repository(PlatformAdminRepository.class),
                auditFacts);
    }

    @Bean
    @ConditionalOnMissingBean(PlatformBootstrapAdministration.class)
    public PlatformBootstrapAdministration platformBootstrapAdministration(
            SuperAdminBootstrapProperties properties,
            PersistenceCapability persistence,
            IdGenerator idGenerator,
            ObjectProvider<JwtIssuerCapability> jwtIssuerCapabilityProvider,
            ObjectProvider<NotificationCapability> notificationCapabilityProvider,
            IdentityAuditFacts auditFacts) {
        return new PlatformBootstrapAdministrationService(
                properties,
                persistence.repository(BootstrapStateRepository.class),
                persistence.repository(IdentityRepository.class),
                persistence.repository(PlatformAdminRepository.class),
                persistence.repository(SetupTokenRepository.class),
                idGenerator,
                Optional.ofNullable(jwtIssuerCapabilityProvider.getIfAvailable()),
                Optional.ofNullable(notificationCapabilityProvider.getIfAvailable()),
                auditFacts);
    }

    @Bean
    @ConditionalOnMissingBean(PlatformIdentityAdministration.class)
    public PlatformIdentityAdministration platformIdentityAdministration(
            PersistenceCapability persistence,
            ObjectProvider<PasswordCapability> passwordCapabilityProvider,
            ObjectProvider<TotpCapability> totpCapabilityProvider,
            ObjectProvider<SecretEncryptionCapability> secretEncryptionCapabilityProvider,
            BootstrapCompletionListener bootstrapCompletionListener,
            ApplicationEventPublisher eventPublisher,
            IdentityAuditFacts auditFacts) {
        return new PlatformIdentityAdministrationService(
                persistence.repository(SetupTokenRepository.class),
                persistence.repository(IdentityRepository.class),
                persistence.repository(PlatformAdminRepository.class),
                Optional.ofNullable(passwordCapabilityProvider.getIfAvailable()),
                Optional.ofNullable(totpCapabilityProvider.getIfAvailable()),
                Optional.ofNullable(secretEncryptionCapabilityProvider.getIfAvailable()),
                bootstrapCompletionListener,
                eventPublisher,
                auditFacts);
    }
}
