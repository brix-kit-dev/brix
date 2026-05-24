/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.brix.platform.auth.flow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import io.brix.platform.auth.internal.RbacResolver;
import io.brix.platform.auth.jwt.JwtIssuerAutoConfiguration;
import io.brix.platform.auth.password.PasswordCapabilityAutoConfiguration;
import io.runtime.sdk.capability.AuthFlowCapability;
import io.runtime.sdk.capability.IdentityTenantCapability;
import io.runtime.sdk.capability.JwtIssuerCapability;
import io.runtime.sdk.capability.PasswordCapability;
import io.runtime.sdk.capability.RefreshTokenCapability;

/**
 * Auto-configuration that exposes the {@link AuthFlowCapability} contract
 * backed by {@link AuthFlowCapabilityImpl}.
 *
 * <p>Activates only when the host wires {@link IdentityTenantCapability}
 * (i.e. {@code platform-tenant} is on the classpath / deployed). Plugin-side
 * legacy auth services therefore continue to function as the sole login path
 * in deployments without multi-tenant tables.</p>
 *
 * @since 3.2.0
 */
@AutoConfiguration(after = { JwtIssuerAutoConfiguration.class, PasswordCapabilityAutoConfiguration.class })
@AutoConfigureAfter(name = {
        // Ordering hint only — class names are loose so we don't hard-link the platform-tenant module.
        "io.brix.platform.tenant.config.TenantAutoConfiguration"
})
// Classpath gate: only activate when platform-tenant module is on the classpath
// (its concrete IdentityTenantCapability implementation class is present).
// @ConditionalOnClass is evaluated at config-class registration time and is reliable;
// @ConditionalOnBean is unreliable for sibling auto-configurations because the
// OnBeanCondition may run before sibling @Bean methods have populated the BeanFactory,
// even when @AutoConfigureAfter ordering is honored. The PasswordCapability and
// JwtIssuerCapability dependencies are produced by sibling configurations in this
// same jar and will fail-fast at bean autowiring time if absent — which is the
// desired behavior since they are mandatory for AuthFlow to function.
@ConditionalOnClass(name = "io.brix.platform.tenant.service.IdentityTenantCapabilityImpl")
@EnableConfigurationProperties(PlatformLoginLockoutProperties.class)
public class AuthFlowAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AuthFlowAutoConfiguration.class);

    /**
     * Registers the default {@link AuthFlowCapability} backed by
     * {@link AuthFlowCapabilityImpl}. Required dependencies
     * ({@link IdentityTenantCapability}, {@link PasswordCapability},
     * {@link JwtIssuerCapability}) are injected as constructor parameters and
     * will trigger a fail-fast {@code NoSuchBeanDefinitionException} at
     * autowiring time if absent — preferred over silently skipping the bean.
     */
    @Bean
    @ConditionalOnMissingBean(AuthFlowCapability.class)
    public AuthFlowCapability authFlowCapability(
            IdentityTenantCapability identityTenantCapability,
            PasswordCapability passwordCapability,
            JwtIssuerCapability jwtIssuerCapability,
            ObjectProvider<RbacResolver> rbacResolverProvider,
                        ObjectProvider<RefreshTokenCapability> refreshTokenProvider,
                        ObjectProvider<MfaLoginSupport> mfaLoginSupportProvider,
            PlatformLoginLockoutProperties lockoutProperties) {
        RbacResolver resolver = rbacResolverProvider.getIfAvailable();
        RefreshTokenCapability refreshTokenCapability = refreshTokenProvider.getIfAvailable();
        log.info("Registering default AuthFlowCapability (multi-tenant) " +
                        "rbacResolver={}, refreshTokenCapability={}",
                resolver == null ? "ABSENT" : resolver.getClass().getSimpleName(),
                refreshTokenCapability == null ? "ABSENT (A2 disabled)" : refreshTokenCapability.getClass().getSimpleName());
        return new AuthFlowCapabilityImpl(identityTenantCapability, passwordCapability,
                jwtIssuerCapability, resolver, refreshTokenCapability, mfaLoginSupportProvider::getIfAvailable,
                lockoutProperties);
    }
}
