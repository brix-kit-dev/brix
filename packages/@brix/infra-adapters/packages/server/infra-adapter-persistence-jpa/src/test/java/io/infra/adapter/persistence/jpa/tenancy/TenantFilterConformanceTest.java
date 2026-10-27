/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.infra.adapter.persistence.jpa.tenancy;

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
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import io.infra.adapter.persistence.jpa.tenancy.P0Repositories.DocRepository;
import io.infra.adapter.persistence.jpa.tenancy.P0Repositories.GlobalRepository;
import io.infra.adapter.persistence.jpa.tenancy.P0Repositories.InviteRepository;
import io.infra.adapter.persistence.jpa.tenancy.P0Repositories.NoteRepository;
import io.infra.adapter.persistence.jpa.tenancy.P0Repositories.TitleView;
import io.infra.adapter.persistence.jpa.tenancy.P0WriteGuardIntegrator.P0ScopeViolation;

/**
 * P0 of the tenant isolation design: proves which query and write forms the adapter-registered
 * Hibernate filters and write guard isolate, on H2 and on the reference DCP. Two tenants hold
 * look-alike data, plus rows that reference the other tenant, so a missing filter shows up as
 * an extra row.
 */
class TenantFilterConformanceTest {

    private static final Long T1 = 1L;
    private static final Long T2 = 2L;
    private static final Pattern ALIASED_TENANT_PREDICATE = Pattern.compile("\\b\\w+\\.tenant_id in \\(");
    private static final List<String> REPORT = new ArrayList<>();
    private static String database = "h2";

    private P0Environment environment;
    private DocRepository docs;
    private NoteRepository notes;
    private InviteRepository invites;
    private GlobalRepository globals;
    private String testName;

    @BeforeEach
    void setUp(TestInfo info) {
        environment = new P0Environment();
        database = environment.database;
        docs = environment.repository(DocRepository.class);
        notes = environment.repository(NoteRepository.class);
        invites = environment.repository(InviteRepository.class);
        globals = environment.repository(GlobalRepository.class);
        testName = info.getTestMethod().map(method -> method.getName()).orElse("?");
        environment.unscoped(() -> {
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
            em.persist(new P0Global(1L, "global"));
            return null;
        });
        P0SqlCapture.INSTANCE.clear();
    }

    @AfterEach
    void tearDown() {
        environment.close();
    }

    @AfterAll
    static void writeReport() throws IOException {
        Path report = Path.of("target", "p0-tenancy-" + database + ".txt");
        Files.createDirectories(report.getParent());
        Files.write(report, REPORT, StandardCharsets.UTF_8);
    }

    // ---------------------------------------------------------------- reads in TENANT(1)

    @Test
    void derivedQueryCountAndExistsAreFiltered() {
        inT1(() -> {
            assertEquals(List.of(101L), ids(docs.findByTitle("doc-a")));
            assertEquals(0L, docs.countByTitle("doc-c"));
            assertFalse(docs.existsByTitle("doc-c"));
            assertTrue(docs.existsByTitle("doc-a"));
            return null;
        });
        assertEveryStatementCarriesTheTenantPredicate();
    }

    @Test
    void pageAndItsCountQueryAreFiltered() {
        Page<P0Doc> page = inT1(() -> docs.findByTitleStartingWith("doc", PageRequest.of(0, 1, Sort.by("id"))));
        assertEquals(2L, page.getTotalElements());
        assertEquals(List.of(101L), ids(page.getContent()));
        assertEveryStatementCarriesTheTenantPredicate();
    }

    @Test
    void interfaceProjectionIsFiltered() {
        List<String> titles = inT1(() -> docs.findViewsByTitleStartingWith("doc").stream()
            .map(TitleView::getTitle).sorted().toList());
        assertEquals(List.of("doc-a", "doc-b"), titles);
        assertEveryStatementCarriesTheTenantPredicate();
    }

    @Test
    void jpqlEntityCountAndScalarQueriesAreFiltered() {
        inT1(() -> {
            assertEquals(List.of(101L, 102L), ids(docs.jpqlByTitlePrefix("doc%")));
            assertEquals(2L, docs.jpqlCountAll());
            assertEquals(List.of("doc-a", "doc-b"), docs.jpqlTitles());
            return null;
        });
        assertEveryStatementCarriesTheTenantPredicate();
    }

    @Test
    void specificationAndItsCountAreFiltered() {
        Specification<P0Doc> titled = (root, query, builder) -> builder.like(root.get("title"), "doc%");
        inT1(() -> {
            assertEquals(List.of(101L, 102L), ids(docs.findAll(titled)));
            assertEquals(2L, docs.count(titled));
            return null;
        });
        assertEveryStatementCarriesTheTenantPredicate();
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
        assertEveryStatementCarriesTheTenantPredicate();
    }

    @Test
    void referenceToOtherTenantCannotBeInitialized() {
        RuntimeException failure = assertThrows(RuntimeException.class,
            () -> inT1(() -> docs.getReferenceById(201L).getTitle()));
        record("reference to other tenant -> " + failure.getClass().getSimpleName());
    }

    @Test
    void lazyToOneIntoOtherTenantDoesNotLoadIt() {
        RuntimeException failure = assertThrows(RuntimeException.class,
            () -> inT1(() -> docs.findById(102L).orElseThrow().getFolder().getName()));
        record("lazy to-one into other tenant -> " + failure.getClass().getSimpleName());
    }

    @Test
    void lazyCollectionIsFiltered() {
        List<Long> noteIds = inT1(() -> ids(docs.findById(101L).orElseThrow().getNotes()));
        assertEquals(List.of(1001L), noteIds);
        assertEveryStatementCarriesTheTenantPredicate();
    }

    @Test
    void explicitJoinFiltersTheJoinedEntity() {
        List<Long> found = inT1(() -> ids(notes.joinedByDocTitlePrefix("doc%")));
        record("explicit join, notes of tenant 1 whose doc is in tenant 1 -> " + found);
        assertEquals(List.of(1001L), found);
    }

    @Test
    void fetchJoinFiltersTheJoinedEntity() {
        List<Long> found = inT1(() -> ids(notes.fetchJoinedDocs()));
        record("fetch join to-one -> " + found);
        assertEquals(List.of(1001L), found);
    }

    @Test
    void subqueryIsFiltered() {
        List<Long> found = inT1(() -> ids(docs.withAnyNote()));
        record("subquery -> " + found);
        assertEquals(List.of(101L), found);
    }

    @Test
    void pessimisticLockQueryIsFiltered() {
        assertEquals(List.of(101L, 102L), inT1(() -> ids(docs.lockByTitlePrefix("doc%"))));
        assertEveryStatementCarriesTheTenantPredicate();
    }

    @Test
    void globalTableIsNotFiltered() {
        assertEquals(1, inT1(() -> globals.findAll().size()));
    }

    @Test
    void boundTenantSetCoversEachOfItsTenants() {
        List<Long> found = environment.inTenants(Set.of(T1, T2), () -> ids(docs.findByTitle("doc-a")));
        assertEquals(List.of(101L, 201L), found);
    }

    // ---------------------------------------------------------------- bulk JPQL

    @Test
    void bulkUpdateIsFilteredOrMustBeForbidden() {
        int changed = inT1(() -> docs.bulkRetitle("renamed"));
        List<String> otherTenant = environment.unscoped(() -> List.of(
            docs.findById(201L).orElseThrow().getTitle(), docs.findById(202L).orElseThrow().getTitle()));
        record("bulk update in tenant 1 changed " + changed + " rows; tenant 2 titles " + otherTenant
            + " sql=" + P0SqlCapture.INSTANCE.statements());
        assertEquals(List.of("doc-a", "doc-c"), otherTenant);
        assertEquals(2, changed);
    }

    @Test
    void bulkDeleteIsFilteredOrMustBeForbidden() {
        int deleted = inT1(() -> docs.bulkDeleteNotes("note-%"));
        long otherTenantLeft = environment.unscoped(() -> notes.findAll().stream()
            .filter(note -> T2.equals(note.getTenantId())).count());
        record("bulk delete in tenant 1 deleted " + deleted + " rows; tenant 2 notes left " + otherTenantLeft
            + " sql=" + P0SqlCapture.INSTANCE.statements());
        assertEquals(3L, otherTenantLeft);
        assertEquals(2, deleted);
    }

    @Test
    void bulkUpdateCanAssignTheTenantSoItMustBeRejectedAtStartup() {
        int moved = inT1(() -> docs.bulkMoveToTenant(T2));
        long nowInTenant2 = environment.unscoped(() -> docs.findAll().stream()
            .filter(doc -> T2.equals(doc.getTenantId())).count());
        record("bulk update assigning tenant_id moved " + moved + " rows; tenant 2 now holds " + nowInTenant2
            + " docs sql=" + P0SqlCapture.INSTANCE.statements().get(0));
        assertEquals(2, moved);
        assertEquals(4L, nowInTenant2);
    }

    @Test
    void bulkInsertSelectWritesAnyTenantSoItMustBeRejectedAtStartup() {
        int copied = inT1(() -> docs.bulkCopyIntoTenant(T2));
        long copiesInTenant2 = environment.unscoped(() -> notes.findAll().stream()
            .filter(note -> note.getId() > 5000L && T2.equals(note.getTenantId())).count());
        record("insert-select from tenant 1 wrote " + copied + " rows; " + copiesInTenant2
            + " landed in tenant 2 sql=" + P0SqlCapture.INSTANCE.statements().get(0));
        assertEquals(2, copied);
        assertEquals(2L, copiesInTenant2);
    }

    // ---------------------------------------------------------------- policy key shapes

    @Test
    void identityKeyFilterReachesThatIdentityInEveryTenant() {
        List<Long> found = environment.keyed(
            P0TenantFilterContributor.IDENTITY_FILTER, P0TenantFilterContributor.IDENTITY_PARAMETER, 7L,
            () -> ids(docs.findAll()));
        assertEquals(List.of(101L, 201L), found);
    }

    @Test
    void inviteeKeyFilterReachesThatAddressInEveryTenant() {
        List<Long> found = environment.keyed(
            P0TenantFilterContributor.INVITEE_FILTER, P0TenantFilterContributor.INVITEE_PARAMETER,
            "a@example.com", () -> ids(invites.findAll()));
        assertEquals(List.of(301L, 302L), found);
    }

    @Test
    void secretKeyResolutionThenNarrowingInOneUnitOfWork() {
        List<Long> found = environment.transactions.execute(status -> {
            P0Invite invite = invites.lockByTokenHash("hash-2").orElseThrow();
            environment.session().enableFilter(P0TenantFilterContributor.TENANT_FILTER)
                .setParameterList(P0TenantFilterContributor.TENANT_PARAMETER, Set.of(invite.getTenantId()));
            return ids(docs.findAll());
        });
        assertEquals(List.of(201L, 202L), found);
        record("secret key lookup sql=" + P0SqlCapture.INSTANCE.statements().get(0));
    }

    @Test
    void persistenceContextIsNotFilteredSoScopesMustNotShareIt() {
        String title = environment.transactions.execute(status -> {
            docs.findById(201L).orElseThrow();
            environment.session().enableFilter(P0TenantFilterContributor.TENANT_FILTER)
                .setParameterList(P0TenantFilterContributor.TENANT_PARAMETER, Set.of(T1));
            return docs.findById(201L).map(P0Doc::getTitle).orElse(null);
        });
        record("entity loaded before the filter is still returned by find -> " + title);
        assertEquals("doc-a", title);
    }

    @Test
    void withoutAFilterNothingIsRestricted() {
        assertEquals(4, environment.unscoped(() -> docs.findAll().size()));
    }

    // ---------------------------------------------------------------- writes

    @Test
    void insertIntoOtherTenantIsRejected() {
        RuntimeException failure = assertThrows(RuntimeException.class, () -> inT1(() -> {
            environment.sharedEntityManager.persist(new P0Doc(203L, T2, null, "doc-x", null));
            environment.sharedEntityManager.flush();
            return null;
        }));
        assertTrue(causedBy(failure, P0ScopeViolation.class), failure.toString());
        assertTrue(environment.unscoped(() -> docs.findById(203L).isEmpty()));
    }

    @Test
    void insertIntoBoundTenantIsAccepted() {
        inT1(() -> {
            environment.sharedEntityManager.persist(new P0Doc(103L, T1, null, "doc-x", null));
            return null;
        });
        assertTrue(environment.unscoped(() -> docs.findById(103L).isPresent()));
    }

    @Test
    void movingARowToAnotherTenantIsRejected() {
        RuntimeException failure = assertThrows(RuntimeException.class, () -> inT1(() -> {
            docs.findById(101L).orElseThrow().setTenantId(T2);
            environment.sharedEntityManager.flush();
            return null;
        }));
        assertTrue(causedBy(failure, P0ScopeViolation.class), failure.toString());
        assertEquals(T1, environment.unscoped(() -> docs.findById(101L).orElseThrow().getTenantId()));
    }

    @Test
    void changingANonUpdatableTenantColumnIsNotWritten() {
        String outcome;
        try {
            inT1(() -> {
                notes.findById(1001L).orElseThrow().setTenantId(T2);
                environment.sharedEntityManager.flush();
                return null;
            });
            outcome = "no error";
        } catch (RuntimeException failure) {
            outcome = failure.getClass().getSimpleName();
        }
        record("changing a tenant_id mapped updatable=false -> " + outcome);
        assertEquals(T1, environment.unscoped(() -> notes.findById(1001L).orElseThrow().getTenantId()));
    }

    @Test
    void deleteByIdOfOtherTenantDoesNothing() {
        inT1(() -> {
            docs.deleteById(201L);
            return null;
        });
        assertTrue(environment.unscoped(() -> docs.findById(201L).isPresent()));
    }

    @Test
    void removingAReferenceToOtherTenantFails() {
        RuntimeException failure = assertThrows(RuntimeException.class, () -> inT1(() -> {
            var em = environment.sharedEntityManager;
            em.remove(em.getReference(P0Doc.class, 201L));
            em.flush();
            return null;
        }));
        record("remove reference to other tenant -> " + failure.getClass().getSimpleName());
        assertTrue(environment.unscoped(() -> docs.findById(201L).isPresent()));
    }

    // ---------------------------------------------------------------- helpers

    private <T> T inT1(Supplier<T> work) {
        return environment.inTenants(Set.of(T1), work);
    }

    private void assertEveryStatementCarriesTheTenantPredicate() {
        List<String> statements = P0SqlCapture.INSTANCE.statements();
        assertFalse(statements.isEmpty());
        for (String sql : statements) {
            assertTrue(ALIASED_TENANT_PREDICATE.matcher(sql).find(), sql);
        }
        record("sql " + statements);
    }

    private void record(String line) {
        REPORT.add("[" + database + "] " + testName + ": " + line);
    }

    private static boolean causedBy(Throwable failure, Class<? extends Throwable> type) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
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
