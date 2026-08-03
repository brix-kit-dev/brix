/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;

import io.brix.platform.tenant.entity.Identity;
import io.brix.platform.tenant.entity.PlatformAdmin;
import io.brix.platform.tenant.enums.IdentityStatus;
import io.brix.platform.tenant.enums.PlatformAdminRole;
import io.brix.platform.tenant.enums.PlatformAdminStatus;
import io.brix.platform.tenant.internal.PlatformPageRequest;
import io.brix.platform.tenant.repository.IdentityRepository;
import io.brix.platform.tenant.repository.PlatformAdminRepository;
import io.brix.platform.tenant.repository.SetupTokenRepository;

class PlatformIdentityAdministrationReadServiceTest {

    private final SetupTokenRepository setupTokenRepository = mock(SetupTokenRepository.class);
    private final IdentityRepository identityRepository = mock(IdentityRepository.class);
    private final PlatformAdminRepository platformAdminRepository = mock(PlatformAdminRepository.class);
    private final BootstrapCompletionListener bootstrapCompletionListener = mock(BootstrapCompletionListener.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final AuditService auditService = mock(AuditService.class);

    @Test
    void listPlatformAdminsReturnsIdentityOwnerWhitelistView() {
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-07-28T12:00:00Z");
        PlatformAdmin admin = new PlatformAdmin(100L, PlatformAdminRole.PLATFORM_SUPER_ADMIN);
        admin.setId(10L);
        admin.setStatus(PlatformAdminStatus.ACTIVE);
        admin.setMfaEnabled(true);
        admin.setNotes("ops");
        admin.setCreatedAt(createdAt);
        Identity identity = new Identity("admin@example.invalid", "admin");
        identity.setId(100L);
        identity.setStatus(IdentityStatus.ACTIVE);
        identity.setPasswordHash("must-not-leak");
        identity.setMfaSecretEncrypted("must-not-leak");
        when(platformAdminRepository.findPlatformAdminPageByStatusAndTerm(
                eq(PlatformAdminStatus.ACTIVE), eq("admin"), any()))
            .thenReturn(new PageImpl<>(List.of(admin)));
        when(identityRepository.findAllById(List.of(100L))).thenReturn(List.of(identity));

        var page = service().listPlatformAdmins(new PlatformPageRequest(
            0,
            20,
            "createdAt",
            true,
            "ACTIVE",
            "admin"));

        assertEquals(1, page.totalElements());
        assertEquals(10L, page.content().get(0).adminId());
        assertEquals(100L, page.content().get(0).identityId());
        assertEquals("admin@example.invalid", page.content().get(0).email());
        assertEquals("PLATFORM_SUPER_ADMIN", page.content().get(0).role());
        verify(platformAdminRepository).findPlatformAdminPageByStatusAndTerm(
                eq(PlatformAdminStatus.ACTIVE), eq("admin"), any());
    }

    @Test
    void listPlatformAdminsUsesTypeStableQueryWhenSearchTermIsBlank() {
        when(platformAdminRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));

        service().listPlatformAdmins(new PlatformPageRequest(
            0,
            20,
            "createdAt",
            true,
            null,
            " "));

        verify(platformAdminRepository).findAll(any(org.springframework.data.domain.Pageable.class));
    }

    private PlatformIdentityAdministrationService service() {
        return new PlatformIdentityAdministrationService(
            setupTokenRepository,
            identityRepository,
            platformAdminRepository,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            bootstrapCompletionListener,
            eventPublisher,
            auditService,
            Optional.empty());
    }
}
