/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.orchestrator.persistence;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import io.runtime.orchestrator.bootstrap.RuntimeShellBootstrapAutoConfiguration;
import io.runtime.orchestrator.event.EventDeliveryScopes;
import io.runtime.sdk.capability.FirstOwnerInviteeIdentitySetupCapability;
import io.runtime.sdk.capability.TenantAccessCapability;

/**
 * Runtime side of tenant isolation (root BRX-RS-DATA-040, multi-tenant §6.4): bounds the
 * tenant access capability the identity protocol surface calls, and gives the outbox relay's
 * deliveries the scope of the registered policy a subscription names (P4 design §4.3).
 */
@AutoConfiguration(after = RuntimeShellBootstrapAutoConfiguration.class)
public class TenantIsolationAutoConfiguration {

    /**
     * Wraps the Data Owner's {@link TenantAccessCapability} so every call binds its scope and every
     * policy use is written to the platform audit.
     *
     * @param writer the policy audit writer, resolved when a policy is used
     * @return the post-processor
     */
    @Bean
    public static BeanPostProcessor tenantAccessBoundaryPostProcessor(ObjectProvider<PolicyAuditWriter> writer) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                return bean instanceof TenantAccessCapability capability
                    ? TenantAccessBoundary.wrap(capability, writer::getIfAvailable)
                    : bean;
            }
        };
    }

    /**
     * Binds the registered cross-tenant policies when the outbox relay delivers an event to a
     * subscription that names one, and writes each use to the platform audit.
     *
     * @param identitySetup the identity owner's setup capability, which gives the resend's access key
     * @param writer the policy audit writer
     * @return the delivery scopes
     */
    @Bean
    @ConditionalOnMissingBean(EventDeliveryScopes.class)
    public EventDeliveryScopes policyEventDeliveryScopes(
            ObjectProvider<FirstOwnerInviteeIdentitySetupCapability> identitySetup,
            ObjectProvider<PolicyAuditWriter> writer) {
        return new PolicyEventDeliveryScopes(identitySetup::getIfAvailable, writer::getIfAvailable);
    }

    /**
     * Stops a host that would use the registered cross-tenant policies without a custodial
     * registration covering their platform audit (root BRX-RS-DATA-045, DATA-047): it fails at
     * startup instead of at the first login.
     *
     * @param tenantAccess the tenant access capability, whose presence means the policies are in use
     * @param writer the policy audit writer
     * @return the startup check
     */
    @Bean
    public SmartInitializingSingleton policyAuditReadiness(
            ObjectProvider<TenantAccessCapability> tenantAccess, ObjectProvider<PolicyAuditWriter> writer) {
        return () -> {
            if (tenantAccess.stream().findAny().isEmpty()) {
                return;
            }
            PolicyAuditWriter available = writer.getIfAvailable();
            List<String> uncovered = Arrays.stream(TenantAccessPolicy.values())
                .flatMap(policy -> policy.auditAction().stream())
                .filter(action -> available == null || !available.covers(action))
                .toList();
            if (!uncovered.isEmpty()) {
                throw new IllegalStateException("Cross-tenant policies are in use but no custodial registration of "
                    + PolicyAuditWriter.CUSTODIAN + " covers their platform audit actions " + uncovered
                    + " (root BRX-RS-DATA-045, DATA-047); the host needs the platform audit owner");
            }
        };
    }
}
