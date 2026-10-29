/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.audit.core.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import io.brix.platform.audit.api.PlatformAuditActions;
import io.brix.platform.audit.api.PlatformAuditWriters;
import io.brix.platform.audit.core.entity.PlatformAuditLog;
import io.brix.platform.audit.core.repository.PlatformAuditLogRepository;
import io.brix.platform.audit.internal.PlatformAuditEntryView;
import io.brix.platform.audit.internal.PlatformAuditPage;
import io.brix.platform.audit.internal.PlatformAuditQuery;
import io.brix.platform.audit.internal.PlatformAuditRejectedException;
import io.brix.platform.audit.internal.RecordPlatformAuditCommand;
import io.runtime.sdk.capability.IdGeneratorCapability;

class PlatformAuditServiceTest {

    private final PlatformAuditLogRepository records = mock(PlatformAuditLogRepository.class);
    private final IdGeneratorCapability ids = mock(IdGeneratorCapability.class);
    private final PlatformAuditService service = new PlatformAuditService(records, ids);

    @Test
    void anOwnerRecordIsStoredWithMaskedClientDataAndTheOwnerAsWriter() {
        when(ids.generate()).thenReturn("374829105938497537");
        when(records.save(any())).thenAnswer(call -> call.getArgument(0));

        long id = service.record(command(PlatformAuditActions.TENANT_CREATED, "TENANT", "910001", "trace-0001-abcd")).value();

        ArgumentCaptor<PlatformAuditLog> saved = ArgumentCaptor.forClass(PlatformAuditLog.class);
        verify(records).save(saved.capture());
        PlatformAuditEntryView view = saved.getValue().toView();
        assertThat(id).isEqualTo(374829105938497537L);
        assertThat(saved.getValue().isNew()).as("an assigned id must still insert").isTrue();
        assertThat(view.action()).isEqualTo(PlatformAuditActions.TENANT_CREATED);
        assertThat(view.sourceIp()).isEqualTo("203.0.113.0/24");
        assertThat(view.userAgent()).hasSize(256);
        assertThat(view.affectedTenants()).containsExactly(910001L);
        assertThat(view.writer()).isEqualTo(PlatformAuditWriters.OWNER);
    }

    @Test
    void aNonNumericIdGeneratorIsRefused() {
        when(ids.generate()).thenReturn("0b8f1c1e-4b1a-4c33-9d43-5d1f0c8d7f0e");

        assertThatThrownBy(() -> service.record(command(PlatformAuditActions.TENANT_CREATED, "TENANT", "910001", "trace-0001-abcd")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("numeric ids");
        verify(records, never()).save(any());
    }

    @Test
    void policyUsesAndUnknownActionsAreRefused() {
        for (String action : Arrays.asList(PlatformAuditActions.LOGIN_TENANT_DISCOVERY,
                PlatformAuditActions.FIRST_OWNER_INVITEE_SETUP_FANOUT, "SOMETHING_ELSE", null)) {
            assertThatThrownBy(() -> service.record(command(action, "TENANT", "910001", "trace-0001-abcd")))
                .isInstanceOfSatisfying(PlatformAuditRejectedException.class,
                    rejected -> assertThat(rejected.code()).isEqualTo(PlatformAuditRejectedException.ACTION_REFUSED));
        }
        verify(records, never()).save(any());
    }

    @Test
    void contentTheOwnerNeverStoresIsRefused() {
        assertFieldInvalid(command(PlatformAuditActions.TENANT_CREATED, "TENANT", "owner@example.com", "trace-0001-abcd"));
        assertFieldInvalid(command(PlatformAuditActions.TENANT_CREATED, "TENANT", "https://evil.example/x", "trace-0001-abcd"));
        assertFieldInvalid(command(PlatformAuditActions.TENANT_CREATED, "tenant created", "910001", "trace-0001-abcd"));
        assertFieldInvalid(command(PlatformAuditActions.TENANT_CREATED, "TENANT", "910001", null));
        assertFieldInvalid(new RecordPlatformAuditCommand(PlatformAuditActions.TENANT_CREATED, 7L, "platform-admin",
            "TENANT", "910001", Arrays.asList(910001L, -1L), true, null, null, "trace-0001-abcd", null, null));
        assertFieldInvalid(new RecordPlatformAuditCommand(PlatformAuditActions.TENANT_CREATED, 7L, "platform-admin",
            "TENANT", "910001", List.of(), false, "Free text reason", null, "trace-0001-abcd", null, null));
        verify(records, never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void theQueryReturnsNewestFirstAndKeepsOlderRowsReadable() {
        PlatformAuditLog older = PlatformAuditLog.ownerRecord(1035351323601539072L, PlatformAuditActions.IDENTITY_ACTIVATED,
            7L, null, "IDENTITY", "7", List.of(), true, null, null, null, null, null, PlatformAuditWriters.PLATFORM_IDENTITY);
        ReflectionTestUtils.setField(older, "createdAt", Instant.parse("2026-10-27T00:28:33Z"));
        when(records.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of(older)));

        PlatformAuditPage page = service.query(new PlatformAuditQuery(0, 20, null, 7L, null, Instant.now()));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(records).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue()).isEqualTo(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        assertThat(page.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.writer()).isEqualTo(PlatformAuditWriters.PLATFORM_IDENTITY);
            assertThat(entry.traceId()).isNull();
            assertThat(entry.affectedTenants()).isEmpty();
        });
    }

    @Test
    void anInvitationOperationIsAnOwnerRecordedAction() {
        when(ids.generate()).thenReturn("374829105938497538");
        when(records.save(any())).thenAnswer(call -> call.getArgument(0));

        for (String action : List.of(PlatformAuditActions.FIRST_OWNER_INVITATION_CREATED,
                PlatformAuditActions.FIRST_OWNER_INVITATION_RESENT, PlatformAuditActions.FIRST_OWNER_INVITATION_REVOKED)) {
            service.record(command(action, "INVITATION", "1035447706903908352", "trace-0001-abcd"));
        }

        verify(records, org.mockito.Mockito.times(3)).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void theQueryCanKeepOnlyFailedRecords() {
        when(records.findAll(any(Specification.class), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.query(new PlatformAuditQuery(0, 20, null, null, null, null, false));

        ArgumentCaptor<Specification<PlatformAuditLog>> filter = ArgumentCaptor.forClass(Specification.class);
        verify(records).findAll(filter.capture(), any(Pageable.class));
        jakarta.persistence.criteria.Root<PlatformAuditLog> root = mock(jakarta.persistence.criteria.Root.class);
        jakarta.persistence.criteria.CriteriaBuilder builder = mock(jakarta.persistence.criteria.CriteriaBuilder.class);
        jakarta.persistence.criteria.Path<Object> success = mock(jakarta.persistence.criteria.Path.class);
        when(root.get("success")).thenReturn(success);
        filter.getValue().toPredicate(root, mock(jakarta.persistence.criteria.CriteriaQuery.class), builder);
        verify(builder).equal(success, false);
    }

    @Test
    void theQueryRangeIsBounded() {
        assertThatThrownBy(() -> new PlatformAuditQuery(0, 0, null, null, null, null))
            .isInstanceOf(PlatformAuditRejectedException.class);
        assertThatThrownBy(() -> new PlatformAuditQuery(0, PlatformAuditQuery.MAX_SIZE + 1, null, null, null, null))
            .isInstanceOf(PlatformAuditRejectedException.class);
        Instant now = Instant.now();
        assertThatThrownBy(() -> new PlatformAuditQuery(0, 10, null, null, now, now))
            .isInstanceOf(PlatformAuditRejectedException.class);
    }

    @Test
    void policyUsesAreNotOwnerRecordedActions() {
        assertThat(PlatformAuditActions.OWNER_RECORDED).doesNotContainAnyElementsOf(PlatformAuditActions.POLICY_USES);
        assertThat(PlatformAuditActions.ALL).containsAll(PlatformAuditActions.POLICY_USES);
    }

    @Test
    void anIdentityFactIsRecordedByTheOwnerAtTheTimeItOccurred() {
        when(ids.generate()).thenReturn("374829105938497539");
        when(records.save(any())).thenAnswer(call -> call.getArgument(0));
        Instant occurredAt = Instant.parse("2026-10-29T08:00:00Z");

        service.recordIdentityFact(identityFact(PlatformAuditActions.IDENTITY_LOCKED, "203.0.113.0/24"), occurredAt);

        ArgumentCaptor<PlatformAuditLog> saved = ArgumentCaptor.forClass(PlatformAuditLog.class);
        verify(records).save(saved.capture());
        PlatformAuditEntryView view = saved.getValue().toView();
        assertThat(view.writer()).isEqualTo(PlatformAuditWriters.OWNER);
        assertThat(view.executionIdentity()).isEqualTo("platform-identity");
        assertThat(view.reasonCode()).isEqualTo("login-failures-exceeded");
        assertThat(view.createdAt()).as("a late delivery keeps the time of the fact").isEqualTo(occurredAt);
        assertThat(view.sourceIp()).as("the publisher already masked the address").isEqualTo("203.0.113.0/24");
    }

    @Test
    void anIdentityFactWithAFullAddressStillKeepsOnlyItsPrefix() {
        when(ids.generate()).thenReturn("374829105938497540");
        when(records.save(any())).thenAnswer(call -> call.getArgument(0));

        service.recordIdentityFact(identityFact(PlatformAuditActions.IDENTITY_LOCKED, "203.0.113.77"), Instant.now());

        ArgumentCaptor<PlatformAuditLog> saved = ArgumentCaptor.forClass(PlatformAuditLog.class);
        verify(records).save(saved.capture());
        assertThat(saved.getValue().toView().sourceIp()).isEqualTo("203.0.113.0/24");
    }

    @Test
    void onlyIdentityActionsArriveAsIdentityFacts() {
        assertThat(PlatformAuditActions.OWNER_RECORDED).containsAll(PlatformAuditService.IDENTITY_FACTS);
        for (String action : Arrays.asList(PlatformAuditActions.TENANT_CREATED,
                PlatformAuditActions.FIRST_OWNER_INVITATION_CREATED, PlatformAuditActions.SUPER_ADMIN_CREATED,
                PlatformAuditActions.LOGIN_TENANT_DISCOVERY, "SOMETHING_ELSE", null)) {
            assertThatThrownBy(() -> service.recordIdentityFact(identityFact(action, null), Instant.now()))
                .isInstanceOfSatisfying(PlatformAuditRejectedException.class,
                    rejected -> assertThat(rejected.code()).isEqualTo(PlatformAuditRejectedException.ACTION_REFUSED));
        }
        verify(records, never()).save(any());
    }

    @Test
    void anIdentityFactFollowsTheSameFieldRules() {
        RecordPlatformAuditCommand freeText = new RecordPlatformAuditCommand(PlatformAuditActions.IDENTITY_ACTIVATED, 7L,
            "platform-identity", "IDENTITY", "7", List.of(), true, "Identity activated.", null, "trace-0001-abcd", null, null);

        assertThatThrownBy(() -> service.recordIdentityFact(freeText, Instant.now()))
            .isInstanceOfSatisfying(PlatformAuditRejectedException.class,
                rejected -> assertThat(rejected.code()).isEqualTo(PlatformAuditRejectedException.FIELD_INVALID));
        verify(records, never()).save(any());
    }

    private static RecordPlatformAuditCommand identityFact(String action, String sourceIp) {
        return new RecordPlatformAuditCommand(action, 7L, "platform-identity", "IDENTITY", "7", List.of(),
            true, "login-failures-exceeded", null, "4bf92f3577b34da6a3ce929d0e0e4736", sourceIp, null);
    }

    private void assertFieldInvalid(RecordPlatformAuditCommand command) {
        assertThatThrownBy(() -> service.record(command))
            .isInstanceOfSatisfying(PlatformAuditRejectedException.class,
                rejected -> assertThat(rejected.code()).isEqualTo(PlatformAuditRejectedException.FIELD_INVALID));
    }

    private static RecordPlatformAuditCommand command(String action, String targetType, String targetId, String traceId) {
        return new RecordPlatformAuditCommand(action, 7L, "platform-admin", targetType, targetId, List.of(910001L),
            true, "platform-operation", null, traceId, "203.0.113.77", "agent ".repeat(100));
    }
}
