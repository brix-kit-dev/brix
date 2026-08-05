/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.brix.platform.auth.oauth2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.brix.platform.auth.flow.AuthFlowAutoConfiguration;
import io.runtime.sdk.capability.AuthFlowCapability;
import io.runtime.sdk.capability.ConfigStoreCapability;
import io.runtime.sdk.capability.HttpCapability;
import io.runtime.sdk.capability.IdentityAccountCapability;
import io.runtime.sdk.capability.OAuth2FederationCapability;
import io.runtime.sdk.capability.TenantAccessCapability;

/**
 * Auto-configuration that exposes the {@link OAuth2FederationCapability} contract
 * backed by {@link OAuth2FederationCapabilityImpl} (Google).
 *
 * <p>Activates only when {@link HttpCapability}, {@link IdentityAccountCapability},
 * {@link TenantAccessCapability}, and {@link AuthFlowCapability} are all on the context — a missing bean degrades
 * gracefully (no OAuth2 endpoint exposed) rather than failing at startup.</p>
 *
 * @since 3.2.0
 */
@AutoConfiguration(after = AuthFlowAutoConfiguration.class)
// Activate when Jackson (ObjectMapper) is present AND the split identity/tenant
// providers are on the classpath. The string-form references keep platform-auth
// loosely coupled to sibling modules. See
// AuthFlowAutoConfiguration for the rationale on preferring @ConditionalOnClass
// over @ConditionalOnBean for cross-module activation guards.
@ConditionalOnClass(value = ObjectMapper.class,
        name = {
                "io.brix.platform.identity.service.IdentityAccountCapabilityImpl",
                "io.brix.platform.tenant.service.TenantAccessCapabilityImpl"
        })
public class OAuth2FederationAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(OAuth2FederationAutoConfiguration.class);

    /**
     * Registers a JWKS-backed Google ID token verifier under an explicit, namespaced
     * bean name {@code platformAuthGoogleIdTokenVerifier} to avoid colliding with
     * unrelated beans in upstream modules that happen to share Spring's conventional
     * default name {@code googleIdTokenVerifier} (e.g. an enterprise application's
     * own {@code @Component GoogleIdTokenVerifier} of a different concrete type).
     *
     * <p>Type-based {@link ConditionalOnMissingBean} ensures a custom verifier of the
     * same {@link GoogleIdTokenVerifier} type provided by an integrator overrides this
     * default; otherwise the platform default is always available for
     * {@link #oAuth2FederationCapability(GoogleIdTokenVerifier, IdentityAccountCapability,
     * TenantAccessCapability,
     * AuthFlowCapability, ConfigStoreCapability)}.</p>
     */
    @Bean("platformAuthGoogleIdTokenVerifier")
    @ConditionalOnMissingBean(GoogleIdTokenVerifier.class)
    public GoogleIdTokenVerifier googleIdTokenVerifier(HttpCapability httpClient, ObjectMapper objectMapper) {
        log.info("Registering default GoogleIdTokenVerifier (JWKS-backed) as bean 'platformAuthGoogleIdTokenVerifier'");
        return new GoogleIdTokenVerifier(httpClient, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(OAuth2FederationCapability.class)
    public OAuth2FederationCapability oAuth2FederationCapability(
            GoogleIdTokenVerifier googleIdTokenVerifier,
            IdentityAccountCapability identityAccountCapability,
            TenantAccessCapability tenantAccessCapability,
            AuthFlowCapability authFlowCapability,
            ConfigStoreCapability configStore) {
        log.info("Registering default OAuth2FederationCapability (Google)");
        return new OAuth2FederationCapabilityImpl(googleIdTokenVerifier, identityAccountCapability, tenantAccessCapability,
                authFlowCapability, configStore);
    }
}
