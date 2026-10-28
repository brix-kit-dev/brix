/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import static io.infra.adapter.persistence.jpa.TenantTestEnvironment.tenants;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.infra.adapter.persistence.jpa.config.TenantIsolationMeterBinder;
import io.infra.adapter.persistence.jpa.tenancy.P0Doc;
import io.infra.adapter.persistence.jpa.tenancy.P0Member;
import io.infra.adapter.persistence.jpa.tenancy.P0Repositories.DocRepository;
import io.infra.adapter.persistence.jpa.tenancy.P0Repositories.MemberRepository;
import io.infra.adapter.persistence.jpa.tenancy.P0Repositories.TenantRegistryRepository;
import io.infra.adapter.persistence.jpa.tenancy.P0Tenant;
import io.runtime.orchestrator.persistence.TenantAccessPolicy;
import io.runtime.orchestrator.persistence.TenantScope;

/**
 * Each refused access is counted once, by the check that refused it, and each owner transaction
 * under a cross-tenant policy is counted with its outcome.
 */
class TenantIsolationMetricsTest {

    private static final Long T1 = 1L;
    private static final Long T2 = 2L;
    private static final String REQUIRED = "persistence.tenant_scope_required";
    private static final String VIOLATION = "persistence.tenant_scope_violation";

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final TenantIsolationMeterBinder binder = new TenantIsolationMeterBinder();
    private TenantTestEnvironment environment;
    private DocRepository docs;
    private MemberRepository members;
    private TenantRegistryRepository tenantRegistry;

    @BeforeEach
    void setUp() {
        environment = new TenantTestEnvironment();
        docs = environment.repository(DocRepository.class);
        members = environment.repository(MemberRepository.class);
        tenantRegistry = environment.repository(TenantRegistryRepository.class);
        environment.write(tenants(T1, T2), () -> {
            var em = environment.sharedEntityManager;
            em.persist(new P0Tenant(T1, "tenant-1"));
            em.persist(new P0Tenant(T2, "tenant-2"));
            em.persist(new P0Doc(101L, T1, 7L, "doc-a", null));
            em.persist(new P0Member(401L, T1, 7L, "OWNER"));
            em.persist(new P0Member(402L, T2, 7L, "ADMIN"));
            return null;
        });
        binder.bindTo(registry);
    }

    @AfterEach
    void tearDown() {
        binder.destroy();
        environment.close();
    }

    @Test
    void aStatementWithoutATenantScopeIsCountedOnce() {
        assertThrows(RuntimeException.class, () -> environment.read(TenantScope.none(), () -> docs.findAll()));

        assertEquals(1.0, violations("statement", "none", REQUIRED));
        assertEquals(1.0, total(TenantIsolationMetrics.VIOLATIONS));
    }

    @Test
    void anEntityWrittenIntoAnotherTenantIsCounted() {
        assertThrows(RuntimeException.class, () -> environment.write(tenants(T1), () -> {
            environment.sharedEntityManager.persist(new P0Doc(201L, T2, null, "doc-x", null));
            environment.sharedEntityManager.flush();
            return null;
        }));

        assertEquals(1.0, violations("entity-write", "tenant", VIOLATION));
        assertEquals(1.0, total(TenantIsolationMetrics.VIOLATIONS));
    }

    @Test
    void aSecondTenantCreatedInOnePlatformOperationIsCounted() {
        assertThrows(RuntimeException.class, () -> environment.write(TenantScope.platformOperation(), () -> {
            tenantRegistry.saveAndFlush(new P0Tenant(9L, "tenant-9"));
            tenantRegistry.saveAndFlush(new P0Tenant(10L, "tenant-10"));
            return null;
        }));

        assertEquals(1.0, violations("tenant-binding", "platform-operation", VIOLATION));
        assertEquals(1.0, policyUses("platform-operation", "failure"));
    }

    @Test
    void aStatementOutsideOwnerUnitsIsCountedAsLegacy() {
        assertThrows(RuntimeException.class, () -> environment.transactions.execute(status ->
            environment.sharedEntityManager.createQuery("select d from P0Doc d", P0Doc.class).getResultList()));

        assertEquals(1.0, registry.get(TenantIsolationMetrics.VIOLATIONS)
            .tags("owner", "legacy", "scope", "none", "reason", "legacy-statement", "code", VIOLATION)
            .counter().count());
        assertEquals(1.0, total(TenantIsolationMetrics.VIOLATIONS));
    }

    @Test
    void policyUsesAreCountedWithTheirOutcome() {
        TenantScope discovery = TenantScope.policy(TenantAccessPolicy.LOGIN_TENANT_DISCOVERY, 7L);

        environment.read(discovery, () -> members.findAll());
        assertThrows(RuntimeException.class, () -> environment.read(discovery, () -> {
            members.findAll();
            return docs.findAll();
        }));
        environment.read(tenants(T1), () -> docs.findAll());

        assertEquals(1.0, policyUses(TenantAccessPolicy.LOGIN_TENANT_DISCOVERY.id(), "success"));
        assertEquals(1.0, policyUses(TenantAccessPolicy.LOGIN_TENANT_DISCOVERY.id(), "failure"));
        assertEquals(2.0, total(TenantIsolationMetrics.POLICY_USES));
    }

    @Test
    void anOwnerTransactionTheDatabaseRejectsIsCountedByOwnerAndCode() {
        assertThrows(RuntimeException.class, () -> environment.write(tenants(T1), () -> {
            environment.sharedEntityManager.persist(new P0Doc(101L, T1, 7L, "duplicate id", null));
            return null;
        }));

        assertEquals(1.0, registry.get(TenantIsolationMetrics.OWNER_FAILURES)
            .tags("owner", TenantTestEnvironment.OWNER, "code", "persistence.unique_constraint_violation")
            .counter().count());
    }

    @Test
    void aFailureTheOwnerRaisesItselfIsNotAnOwnerFailure() {
        assertThrows(IllegalStateException.class, () -> environment.write(tenants(T1), () -> {
            throw new IllegalStateException("the owner refuses");
        }));

        assertEquals(0.0, total(TenantIsolationMetrics.OWNER_FAILURES));
    }

    @Test
    void nothingIsCountedOnceTheBinderIsDestroyed() {
        binder.destroy();

        assertThrows(RuntimeException.class, () -> environment.read(TenantScope.none(), () -> docs.findAll()));

        assertEquals(0.0, total(TenantIsolationMetrics.VIOLATIONS));
    }

    private double violations(String reason, String scope, String code) {
        return registry.get(TenantIsolationMetrics.VIOLATIONS)
            .tags("owner", TenantTestEnvironment.OWNER, "scope", scope, "reason", reason, "code", code)
            .counter().count();
    }

    private double policyUses(String policy, String outcome) {
        return registry.get(TenantIsolationMetrics.POLICY_USES)
            .tags("owner", TenantTestEnvironment.OWNER, "policy", policy, "outcome", outcome)
            .counter().count();
    }

    private double total(String name) {
        return registry.find(name).counters().stream().mapToDouble(Counter::count).sum();
    }
}
