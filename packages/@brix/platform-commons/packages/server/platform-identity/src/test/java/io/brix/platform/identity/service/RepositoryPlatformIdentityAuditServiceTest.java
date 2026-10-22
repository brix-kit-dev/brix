/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.identity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.brix.platform.identity.core.IdGenerator;
import io.brix.platform.identity.dto.AuditEvent;
import io.brix.platform.identity.entity.PlatformAuditLogEntry;
import io.brix.platform.identity.repository.PlatformAuditLogRepository;

class RepositoryPlatformIdentityAuditServiceTest {

    private final PlatformAuditLogRepository repository = mock(PlatformAuditLogRepository.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);
    private final RepositoryPlatformIdentityAuditService auditService =
        new RepositoryPlatformIdentityAuditService(repository, idGenerator);

    @Test
    void appendsTheEventThroughTheOwnerRepository() {
        when(idGenerator.nextId()).thenReturn(42L);

        auditService.log(AuditEvent.builder()
            .createdBy(7L)
            .action("IDENTITY_ACTIVATED")
            .resourceType("IDENTITY")
            .resourceId("7")
            .description("Identity activated.")
            .success(true)
            .build());

        ArgumentCaptor<PlatformAuditLogEntry> entry = ArgumentCaptor.forClass(PlatformAuditLogEntry.class);
        verify(repository).save(entry.capture());
        assertEquals(42L, entry.getValue().getId());
        assertEquals(7L, entry.getValue().getOperatorIdentityId());
        assertEquals("IDENTITY_ACTIVATED", entry.getValue().getAction());
        assertEquals("IDENTITY", entry.getValue().getResourceType());
        assertEquals("7", entry.getValue().getResourceId());
        assertEquals("[]", entry.getValue().getAffectedTenants());
        assertTrue(entry.getValue().isSuccess());
    }

    @Test
    void rejectsEventsWithoutActionOrResourceType() {
        assertThrows(IllegalArgumentException.class, () -> auditService.log(AuditEvent.builder()
            .resourceType("IDENTITY")
            .build()));
        assertThrows(IllegalArgumentException.class, () -> auditService.log(AuditEvent.builder()
            .action("IDENTITY_ACTIVATED")
            .build()));

        verify(repository, never()).save(any());
    }
}
