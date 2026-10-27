/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.persistence;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

import io.runtime.orchestrator.bootstrap.RuntimeShellBootstrapAutoConfiguration;
import io.runtime.orchestrator.plugin.PluginRuntimeManager;
import io.runtime.sdk.capability.FirstOwnerInviteeIdentitySetupCapability;
import io.runtime.sdk.capability.TenantAccessCapability;

/**
 * Runtime side of tenant isolation (root BRX-RS-DATA-040, multi-tenant §6.4): bounds the
 * tenant access capability the identity protocol surface calls, and delivers the identity
 * owner's setup-completed fact with the scope its subscribers declare.
 */
@AutoConfiguration(after = RuntimeShellBootstrapAutoConfiguration.class)
public class TenantIsolationAutoConfiguration {

    /**
     * Wraps the Data Owner's {@link TenantAccessCapability} so every call binds its scope.
     *
     * @return the post-processor
     */
    @Bean
    public static BeanPostProcessor tenantAccessBoundaryPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                return bean instanceof TenantAccessCapability capability ? TenantAccessBoundary.wrap(capability) : bean;
            }
        };
    }

    @Bean
    @ConditionalOnBean(PluginRuntimeManager.class)
    public InviteeSetupEventDelivery inviteeSetupEventDelivery(
            PluginRuntimeManager plugins,
            ObjectProvider<OwnerPersistenceProvider> ownerPersistence,
            ObjectProvider<FirstOwnerInviteeIdentitySetupCapability> identitySetup) {
        return new InviteeSetupEventDelivery(
            plugins, ownerPersistence.getIfAvailable(OwnerPersistenceProvider::none), identitySetup);
    }
}
