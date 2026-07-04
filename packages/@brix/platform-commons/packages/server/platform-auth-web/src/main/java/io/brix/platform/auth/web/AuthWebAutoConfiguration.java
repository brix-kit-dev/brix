/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package io.brix.platform.auth.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;

import io.brix.platform.auth.context.SecurityContextHolder;
import io.brix.platform.auth.web.advice.AuthFlowExceptionAdvice;
import io.brix.platform.auth.web.controller.AuthController;
import io.brix.platform.auth.web.controller.OAuth2FederationController;
import io.runtime.sdk.capability.AuthContextCapability;
import io.runtime.sdk.capability.AuthFlowCapability;
import io.runtime.sdk.capability.OAuth2FederationCapability;
import jakarta.servlet.Servlet;

/**
 * Auto-configuration that registers the platform-auth-web REST surface.
 *
 * <h3>Registration rules</h3>
 * <ul>
 *   <li>{@link AuthController} — registered only when {@link AuthFlowCapability}
 *       AND {@link AuthContextCapability} beans are present.</li>
 *   <li>{@link OAuth2FederationController} — registered only when
 *       {@link OAuth2FederationCapability} bean is present (host opted-in to OAuth2).</li>
 *   <li>{@link AuthFlowExceptionAdvice} — always registered when this auto-config activates;
 *       scoped via {@code @RestControllerAdvice(basePackages=...)} so it only catches
 *       exceptions thrown from controllers in this package.</li>
 * </ul>
 *
 * @since 3.2.0
 */
@AutoConfiguration
@ConditionalOnClass({ Servlet.class, org.springframework.web.bind.annotation.RestController.class })
public class AuthWebAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AuthWebAutoConfiguration.class);

    @Bean
    @ConditionalOnBean({ AuthFlowCapability.class, AuthContextCapability.class })
    @ConditionalOnMissingBean(AuthController.class)
    public AuthController authController(AuthFlowCapability authFlow,
                                         AuthContextCapability authContext,
                                         ObjectProvider<SecurityContextHolder> securityContextHolderProvider) {
        log.info("Registering platform-auth-web AuthController on /api/auth");
        return new AuthController(authFlow, authContext, securityContextHolderProvider.getIfAvailable());
    }

    @Bean
    @ConditionalOnBean(OAuth2FederationCapability.class)
    @ConditionalOnMissingBean(OAuth2FederationController.class)
    public OAuth2FederationController oAuth2FederationController(OAuth2FederationCapability federation) {
        log.info("Registering platform-auth-web OAuth2FederationController on /api/v1/oauth2/google/id-token");
        return new OAuth2FederationController(federation);
    }

    @Bean
    @ConditionalOnMissingBean(AuthFlowExceptionAdvice.class)
    public AuthFlowExceptionAdvice authFlowExceptionAdvice() {
        return new AuthFlowExceptionAdvice();
    }
}
