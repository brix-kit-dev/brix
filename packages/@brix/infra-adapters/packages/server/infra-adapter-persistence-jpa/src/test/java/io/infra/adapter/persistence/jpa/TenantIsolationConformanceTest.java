/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa;

import static io.infra.adapter.persistence.jpa.TenantTestEnvironment.tenants;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.function.Executable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import io.infra.adapter.persistence.jpa.tenancy.P0Doc;
import io.infra.adapter.persistence.jpa.tenancy.P0Folder;
import io.infra.adapter.persistence.jpa.tenancy.P0Global;
import io.infra.adapter.persistence.jpa.tenancy.P0Invite;
import io.infra.adapter.persistence.jpa.tenancy.P0Member;
import io.infra.adapter.persistence.jpa.tenancy.P0Note;
import io.infra.adapter.persistence.jpa.tenancy.P0Projection;
import io.infra.adapter.persistence.jpa.tenancy.P0Repositories.DocRepository;
import io.infra.adapter.persistence.jpa.tenancy.P0Repositories.GlobalRepository;
import io.infra.adapter.persistence.jpa.tenancy.P0Repositories.InviteRepository;
import io.infra.adapter.persistence.jpa.tenancy.P0Repositories.MemberRepository;
import io.infra.adapter.persistence.jpa.tenancy.P0Repositories.NoteRepository;
import io.infra.adapter.persistence.jpa.tenancy.P0Repositories.ProjectionRepository;
import io.infra.adapter.persistence.jpa.tenancy.P0Repositories.TenantCopyingRepository;
import io.infra.adapter.persistence.jpa.tenancy.P0Repositories.TenantMovingRepository;
import io.infra.adapter.persistence.jpa.tenancy.P0Repositories.TenantRegistryRepository;
import io.infra.adapter.persistence.jpa.tenancy.P0Repositories.TitleView;
import io.infra.adapter.persistence.jpa.tenancy.P0Tenant;
import io.runtime.orchestrator.persistence.TenantAccessPolicy;
import io.runtime.orchestrator.persistence.TenantScope;
import io.runtime.sdk.capability.PersistenceCapabilityException;
import io.runtime.sdk.capability.PersistenceCapabilityException.Code;

/**
 * Conformance of the adapter's tenant isolation (root BRX-RS-DATA-040, multi-tenant §6.4) through
 * real owner units of work: the scope the runtime requests decides what each repository call
 * reaches. Two tenants hold look-alike rows, plus rows that reference the other tenant, so a
 * missing filter shows up as an extra row. Runs on H2, and on PostgreSQL 15 when
 * {@code BRIX_P0_POSTGRES_URL} is set.
 */
class TenantIsolationConformanceTest {

    private static final Long T1 = 1L;
    private static final Long T2 = 2L;
    private static final Pattern ALIASED_TENANT_PREDICATE = Pattern.compile("\\b\\w+\\.tenant_id in \\(");
    private static final List<String> REPORT = new ArrayList<>();
    private static String database = "h2";

    private TenantTestEnvironment environment;
    private DocRepository docs;
    private NoteRepository notes;
    private InviteRepository invites;
    private MemberRepository members;
    private GlobalRepository globals;
    private TenantRegistryRepository registry;
    private String testName;

    @BeforeEach
    void setUp(TestInfo info) {
        environment = new TenantTestEnvironment();
        database = environment.database;
        docs = environment.repository(DocRepository.class);
        notes = environment.repository(NoteRepository.class);
        invites = environment.repository(InviteRepository.class);
        members = environment.repository(MemberRepository.class);
        globals = environment.repository(GlobalRepository.class);
        registry = environment.repository(TenantRegistryRepository.class);
        testName = info.getTestMethod().map(method -> method.getName()).orElse("?");
        environment.write(tenants(T1, T2), () -> {
            var em = environment.sharedEntityManager;
            P0Folder f1 = new P0Folder(11L, T1, "folder-1");
            P0Folder f2 = new P0Folder(12L, T2, "folder-2");
            em.persist(f1);
            em.persist(f2);
            P0Doc d1 = new P0Doc(101L, T1, 7L, "doc-a", f1);
            P0Doc d2 = new P0Doc(102L, T1, 8L, "doc-b", f2);
            P0Doc d3 = new P0Doc(201L, T2, 7L, "doc-a", f2);
            P0Doc d4 = new P0Doc(202L, T2, 9L, "doc-c", f2);
            em.persist(d1);
            em.persist(d2);
            em.persist(d3);
            em.persist(d4);
            em.persist(new P0Note(1001L, T1, d1, "note-1"));
            em.persist(new P0Note(1002L, T2, d1, "note-2"));
            em.persist(new P0Note(2001L, T2, d3, "note-3"));
            em.persist(new P0Note(2002L, T1, d3, "note-4"));
            em.persist(new P0Note(2003L, T2, d2, "note-5"));
            em.persist(new P0Invite(301L, T1, "hash-1", "a@example.com"));
            em.persist(new P0Invite(302L, T2, "hash-2", "a@example.com"));
            em.persist(new P0Invite(303L, T2, "hash-3", "b@example.com"));
            em.persist(new P0Member(401L, T1, 7L, "OWNER"));
            em.persist(new P0Member(402L, T2, 7L, "ADMIN"));
            em.persist(new P0Member(403L, T2, 9L, "MEMBER"));
            em.persist(new P0Global(1L, "global"));
            em.persist(new P0Tenant(T1, "tenant-1"));
            em.persist(new P0Tenant(T2, "tenant-2"));
            return null;
        });
        environment.statements.clear();
    }

    @AfterEach
    void tearDown() {
        environment.close();
    }

    @AfterAll
    static void writeReport() throws IOException {
        Path report = Path.of("target", "tenant-isolation-" + database + ".txt");
        Files.createDirectories(report.getParent());
        Files.write(report, REPORT, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------ reads in TENANT(1)

    @Test
    void derivedQueryCountAndExistsAreFiltered() {
        inT1(() -> {
            assertEquals(List.of(101L), ids(docs.findByTitle("doc-a")));
            assertEquals(0L, docs.countByTitle("doc-c"));
            assertFalse(docs.existsByTitle("doc-c"));
            assertTrue(docs.existsByTitle("doc-a"));
            return null;
        });
        assertTenantPredicateOnEveryStatement();
    }

    @Test
    void pageAndItsCountQueryAreFiltered() {
        Page<P0Doc> page = inT1(() -> docs.findByTitleStartingWith("doc", PageRequest.of(0, 1, Sort.by("id"))));
        assertEquals(2L, page.getTotalElements());
        assertEquals(List.of(101L), ids(page.getContent()));
        assertTenantPredicateOnEveryStatement();
    }

    @Test
    void projectionJpqlAndScalarQueriesAreFiltered() {
        inT1(() -> {
            assertEquals(List.of("doc-a", "doc-b"),
                docs.findViewsByTitleStartingWith("doc").stream().map(TitleView::getTitle).sorted().toList());
            assertEquals(List.of(101L, 102L), ids(docs.jpqlByTitlePrefix("doc%")));
            assertEquals(2L, docs.jpqlCountAll());
            assertEquals(List.of("doc-a", "doc-b"), docs.jpqlTitles());
            return null;
        });
        assertTenantPredicateOnEveryStatement();
    }

    @Test
    void specificationAndItsCountAreFiltered() {
        Specification<P0Doc> titled = (root, query, builder) -> builder.like(root.get("title"), "doc%");
        inT1(() -> {
            assertEquals(List.of(101L, 102L), ids(docs.findAll(titled)));
            assertEquals(2L, docs.count(titled));
            return null;
        });
        assertTenantPredicateOnEveryStatement();
    }

    @Test
    void loadByKeyIsFiltered() {
        inT1(() -> {
            assertTrue(docs.findById(201L).isEmpty());
            assertTrue(docs.findById(101L).isPresent());
            assertEquals(List.of(101L), ids(docs.findAllById(List.of(101L, 201L))));
            assertFalse(docs.existsById(201L));
            return null;
        });
        assertTenantPredicateOnEveryStatement();
    }

    @Test
    void referencesAndLazyToOneIntoOtherTenantsLoadNothing() {
        RuntimeException reference = assertThrows(RuntimeException.class,
            () -> inT1(() -> docs.getReferenceById(201L).getTitle()));
        RuntimeException lazy = assertThrows(RuntimeException.class,
            () -> inT1(() -> docs.findById(102L).orElseThrow().getFolder().getName()));
        record("reference -> " + reference.getClass().getSimpleName() + ", lazy to-one -> "
            + lazy.getClass().getSimpleName());
    }

    @Test
    void lazyCollectionJoinsAndSubqueriesAreFiltered() {
        inT1(() -> {
            assertEquals(List.of(1001L), ids(docs.findById(101L).orElseThrow().getNotes()));
            assertEquals(List.of(1001L), ids(notes.joinedByDocTitlePrefix("doc%")));
            assertEquals(List.of(1001L), ids(notes.fetchJoinedDocs()));
            assertEquals(List.of(101L), ids(docs.withAnyNote()));
            return null;
        });
        assertTenantPredicateOnEveryStatement();
    }

    @Test
    void pessimisticLockQueryIsFiltered() {
        assertEquals(List.of(101L, 102L),
            environment.write(tenants(T1), () -> ids(docs.lockByTitlePrefix("doc%"))));
        assertTenantPredicateOnEveryStatement();
    }

    @Test
    void globalTablesAreNotFiltered() {
        assertEquals(1, inT1(() -> globals.findAll().size()));
        assertEquals(1, environment.read(TenantScope.none(), () -> globals.findAll().size()));
    }

    @Test
    void aTenantSetReachesEachOfItsTenants() {
        assertEquals(List.of(101L, 201L), environment.read(tenants(T1, T2), () -> ids(docs.findByTitle("doc-a"))));
    }

    // ------------------------------------------------------------------ bulk JPQL

    @Test
    void bulkUpdateAndDeleteOnlyReachTheBoundTenant() {
        assertEquals(2, environment.write(tenants(T1), () -> docs.bulkRetitle("renamed")));
        assertEquals(List.of("doc-a", "doc-c"), environment.read(tenants(T2),
            () -> docs.findAll().stream().map(P0Doc::getTitle).sorted().toList()));
        assertEquals(2, environment.write(tenants(T1), () -> docs.bulkDeleteNotes("note-%")));
        assertEquals(3L, environment.read(tenants(T2), () -> notes.count()));
    }

    @Test
    void bulkWritesThatCrossTenantsAreRejectedAtStartup() {
        assertCode(Code.REPOSITORY_REJECTED, () -> environment.repository(TenantMovingRepository.class));
        assertCode(Code.REPOSITORY_REJECTED, () -> environment.repository(TenantCopyingRepository.class));
    }

    // ------------------------------------------------------------------ no tenant

    @Test
    void withoutATenantScopeTenantTablesAreRefused() {
        assertCode(Code.TENANT_SCOPE_REQUIRED,
            () -> environment.read(TenantScope.none(), () -> docs.findAll()));
        assertCode(Code.TENANT_SCOPE_REQUIRED, () -> environment.write(TenantScope.none(), () -> {
            environment.sharedEntityManager.persist(new P0Doc(103L, T1, null, "doc-x", null));
            environment.sharedEntityManager.flush();
            return null;
        }));
    }

    // ------------------------------------------------------------------ writes in TENANT(1)

    @Test
    void writesStayInTheBoundTenant() {
        environment.write(tenants(T1), () -> {
            environment.sharedEntityManager.persist(new P0Doc(103L, T1, null, "doc-x", null));
            return null;
        });
        assertTrue(environment.read(tenants(T1), () -> docs.findById(103L).isPresent()));
        assertCode(Code.TENANT_SCOPE_VIOLATION, () -> environment.write(tenants(T1), () -> {
            environment.sharedEntityManager.persist(new P0Doc(203L, T2, null, "doc-x", null));
            environment.sharedEntityManager.flush();
            return null;
        }));
        assertCode(Code.TENANT_SCOPE_VIOLATION, () -> environment.write(tenants(T1), () -> {
            docs.findById(101L).orElseThrow().setTenantId(T2);
            environment.sharedEntityManager.flush();
            return null;
        }));
        assertEquals(T1, environment.read(tenants(T1, T2), () -> docs.findById(101L).orElseThrow().getTenantId()));
    }

    @Test
    void otherTenantsRowsCannotBeDeleted() {
        environment.write(tenants(T1), () -> {
            docs.deleteById(201L);
            return null;
        });
        assertThrows(RuntimeException.class, () -> environment.write(tenants(T1), () -> {
            var em = environment.sharedEntityManager;
            em.remove(em.getReference(P0Doc.class, 201L));
            em.flush();
            return null;
        }));
        assertTrue(environment.read(tenants(T2), () -> docs.findById(201L).isPresent()));
    }

    @Test
    void aTenantKeyedIdentifierIsTheTenantColumn() {
        ProjectionRepository projections = environment.repository(ProjectionRepository.class);
        environment.write(tenants(T1), () -> projections.save(new P0Projection(T1, "projection-1")));
        environment.write(tenants(T2), () -> projections.save(new P0Projection(T2, "projection-2")));
        environment.statements.clear();

        assertEquals(List.of(T1), environment.read(tenants(T1),
            () -> projections.findAll().stream().map(P0Projection::getTenantId).toList()));
        assertTrue(environment.read(tenants(T1), () -> projections.findById(T2).isEmpty()));
        assertTenantPredicateOnEveryStatement();
        assertCode(Code.TENANT_SCOPE_VIOLATION, () -> environment.write(tenants(T1), () -> {
            environment.sharedEntityManager.persist(new P0Projection(3L, "projection-3"));
            environment.sharedEntityManager.flush();
            return null;
        }));
        environment.write(tenants(T1), () -> {
            projections.findById(T1).orElseThrow().setLabel("projection-1b");
            projections.deleteById(T2);
            return null;
        });
        assertEquals("projection-1b", environment.read(tenants(T1), () -> projections.findById(T1).orElseThrow().getLabel()));
        assertTrue(environment.read(tenants(T2), () -> projections.findById(T2).isPresent()));
    }

    // ------------------------------------------------------------------ registered policies

    @Test
    void loginDiscoveryReadsThatIdentityInEveryTenantAndNothingElse() {
        TenantScope discovery = TenantScope.policy(TenantAccessPolicy.LOGIN_TENANT_DISCOVERY, 7L);
        assertEquals(List.of(401L, 402L), environment.read(discovery, () -> members.findAll().stream()
            .map(P0Member::getId).sorted().toList()));
        assertCode(Code.TENANT_SCOPE_VIOLATION, () -> environment.read(discovery, () -> {
            members.findAll();
            return docs.findAll();
        }));
        assertCode(Code.TENANT_SCOPE_VIOLATION, () -> environment.write(discovery, () -> {
            members.findAll().forEach(member -> member.setMemberType("ADMIN"));
            environment.sharedEntityManager.flush();
            return null;
        }));
    }

    @Test
    void invitationResolutionNeedsTheTokenThenStaysInItsTenant() {
        TenantScope resolution = TenantScope.policy(TenantAccessPolicy.FIRST_OWNER_INVITATION_RESOLUTION, null);
        assertEquals(List.of(201L, 202L), environment.read(resolution, () -> {
            invites.findByTokenHash("hash-2").orElseThrow();
            return ids(docs.findAll());
        }));
        assertTrue(environment.read(resolution, () -> {
            invites.findByTokenHash("hash-2").orElseThrow();
            return invites.findByTokenHash("hash-1").isEmpty();
        }));
        assertCode(Code.TENANT_SCOPE_VIOLATION, () -> environment.read(resolution, () -> invites.findAll()));
        assertCode(Code.TENANT_SCOPE_VIOLATION, () -> environment.read(resolution, () -> docs.findAll()));
    }

    @Test
    void invitationAcceptanceWritesOnlyInTheInvitationsTenant() {
        TenantScope resolution = TenantScope.policy(TenantAccessPolicy.FIRST_OWNER_INVITATION_RESOLUTION, null);
        environment.write(resolution, () -> {
            invites.lockByTokenHash("hash-2").orElseThrow().setStatus("ACCEPTED");
            environment.sharedEntityManager.persist(new P0Doc(204L, T2, null, "doc-x", null));
            return null;
        });
        assertTrue(environment.read(tenants(T2), () -> docs.findById(204L).isPresent()));
        assertCode(Code.TENANT_SCOPE_VIOLATION, () -> environment.write(resolution, () -> {
            invites.lockByTokenHash("hash-3").orElseThrow();
            environment.sharedEntityManager.persist(new P0Doc(104L, T1, null, "doc-x", null));
            environment.sharedEntityManager.flush();
            return null;
        }));
    }

    @Test
    void setupFanoutReachesThatAddressesInvitationsOnly() {
        TenantScope fanout = TenantScope.policy(TenantAccessPolicy.FIRST_OWNER_INVITEE_SETUP_FANOUT, "a@example.com");
        environment.write(fanout, () -> {
            invites.findAll().forEach(invite -> invite.setStatus("SENT"));
            assertEquals(List.of(401L, 402L, 403L), members.findAll().stream().map(P0Member::getId).sorted().toList());
            return null;
        });
        assertEquals(List.of("PENDING", "SENT", "SENT"), environment.read(tenants(T1, T2),
            () -> invites.findAll().stream().map(P0Invite::getStatus).sorted().toList()));
        assertCode(Code.TENANT_SCOPE_VIOLATION, () -> environment.read(fanout, () -> {
            invites.findAll();
            return docs.findAll();
        }));
        assertCode(Code.TENANT_SCOPE_VIOLATION, () -> environment.write(fanout, () -> {
            invites.findAll();
            members.findAll().forEach(member -> member.setMemberType("ADMIN"));
            environment.sharedEntityManager.flush();
            return null;
        }));
    }

    // ------------------------------------------------------------------ platform operations

    @Test
    void aPlatformOperationReachesOnlyTheTenantItCreates() {
        environment.write(TenantScope.platformOperation(), () -> {
            registry.saveAndFlush(new P0Tenant(9L, "tenant-9"));
            docs.save(new P0Doc(901L, 9L, null, "doc-new", null));
            return null;
        });
        assertTrue(environment.read(tenants(9L), () -> docs.findById(901L).isPresent()));
        assertCode(Code.TENANT_SCOPE_REQUIRED,
            () -> environment.read(TenantScope.platformOperation(), () -> docs.findAll()));
        assertCode(Code.TENANT_SCOPE_VIOLATION, () -> environment.write(TenantScope.platformOperation(), () -> {
            registry.saveAndFlush(new P0Tenant(10L, "tenant-10"));
            environment.sharedEntityManager.persist(new P0Doc(902L, T1, null, "doc-x", null));
            environment.sharedEntityManager.flush();
            return null;
        }));
    }

    // ------------------------------------------------------------------ outside owner units

    @Test
    void statementsOutsideOwnerUnitsKeepTheLegacyRules() {
        assertCode(Code.TENANT_SCOPE_VIOLATION, () -> environment.transactions.execute(status ->
            environment.sharedEntityManager.createQuery("select d from P0Doc d", P0Doc.class).getResultList()));
        assertEquals(2, (int) environment.transactions.execute(status -> environment.sharedEntityManager
            .createQuery("select d from P0Doc d where d.tenantId = 1", P0Doc.class).getResultList().size()));
        assertEquals(2, (int) environment.transactions.execute(status -> environment.sharedEntityManager
            .createQuery("select t from P0Tenant t", P0Tenant.class).getResultList().size()));
    }

    // ------------------------------------------------------------------ helpers

    private <T> T inT1(Supplier<T> work) {
        return environment.read(tenants(T1), work);
    }

    private void assertTenantPredicateOnEveryStatement() {
        List<String> statements = environment.statements.passed();
        assertFalse(statements.isEmpty());
        for (String sql : statements) {
            assertTrue(ALIASED_TENANT_PREDICATE.matcher(sql).find(), sql);
        }
        record("sql " + statements);
    }

    private static void assertCode(Code code, Executable executable) {
        Throwable failure = assertThrows(Throwable.class, executable);
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof PersistenceCapabilityException translated && translated.code() == code) {
                return;
            }
        }
        throw new AssertionError("expected " + code + " but got " + failure, failure);
    }

    private void record(String line) {
        REPORT.add("[" + database + "] " + testName + ": " + line);
    }

    private static List<Long> ids(Iterable<?> entities) {
        List<Long> ids = new ArrayList<>();
        for (Object entity : entities) {
            if (entity instanceof P0Doc doc) {
                ids.add(doc.getId());
            } else if (entity instanceof P0Note note) {
                ids.add(note.getId());
            } else if (entity instanceof P0Invite invite) {
                ids.add(invite.getId());
            }
        }
        ids.sort(Comparator.naturalOrder());
        return ids;
    }
}
