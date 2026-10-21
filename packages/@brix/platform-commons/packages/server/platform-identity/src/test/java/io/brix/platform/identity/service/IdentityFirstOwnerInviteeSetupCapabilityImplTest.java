/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.identity.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.brix.platform.identity.core.IdGenerator;
import io.brix.platform.identity.entity.Identity;
import io.brix.platform.identity.enums.IdentityStatus;
import io.brix.platform.identity.repository.IdentityRepository;
import io.brix.platform.identity.repository.PlatformAdminRepository;
import io.brix.platform.identity.repository.SetupTokenRepository;
import io.runtime.sdk.capability.NotificationCapability;
import io.runtime.sdk.capability.NotificationRequest;
import io.runtime.sdk.capability.NotificationTemplateKeys;

class IdentityFirstOwnerInviteeSetupCapabilityImplTest {

    private static final String SETUP_BASE_URL = "https://tenant.example.invalid/setup";

    private final IdentityRepository identityRepository = mock(IdentityRepository.class);
    private final PlatformAdminRepository platformAdminRepository = mock(PlatformAdminRepository.class);
    private final SetupTokenRepository setupTokenRepository = mock(SetupTokenRepository.class);
    private final NotificationCapability notification = mock(NotificationCapability.class);
    private final IdGenerator idGenerator = mock(IdGenerator.class);
    private final RecordingUnitOfWork unitOfWork = new RecordingUnitOfWork();
    private final IdentityFirstOwnerInviteeSetupCapabilityImpl capability =
        new IdentityFirstOwnerInviteeSetupCapabilityImpl(
            identityRepository,
            platformAdminRepository,
            setupTokenRepository,
            Optional.of(notification),
            idGenerator,
            SETUP_BASE_URL,
            unitOfWork);

    @Test
    void setupForANewInviteeRunsInOneIdentityUnitOfWork() {
        when(identityRepository.findByEmail("owner@example.com")).thenReturn(Optional.empty());
        when(identityRepository.save(any(Identity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(platformAdminRepository.findByIdentityId(any())).thenReturn(Optional.empty());
        when(idGenerator.nextId()).thenReturn(10L, 11L);

        boolean sent = capability.sendSetupIfRequired(5L, "Owner@Example.com", "platform-identity:1", "en");

        assertTrue(sent);
        assertEquals(List.of("required"), unitOfWork.modes());
        verify(setupTokenRepository).markActiveTokensUsed(eq(10L), eq(SetupTokenPurposes.TENANT_FIRST_OWNER_SETUP), any());
        ArgumentCaptor<NotificationRequest> request = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notification).send(request.capture());
        assertEquals(NotificationTemplateKeys.TENANT_OWNER_SETUP_INITIAL, request.getValue().templateKey());
        assertTrue(request.getValue().variables().get("setupUrl").startsWith(SETUP_BASE_URL + "?token="));
    }

    @Test
    void emailLookupsRunInAReadOnlyUnitOfWork() {
        Identity identity = new Identity("owner@example.com", "owner@example.com");
        identity.setStatus(IdentityStatus.ACTIVE);
        when(identityRepository.findById(10L)).thenReturn(Optional.of(identity));

        assertEquals("owner@example.com", capability.requireIdentityEmail(10L));
        assertEquals("owner@example.com", capability.requireActiveIdentityEmail(10L));
        assertEquals(List.of("readOnly", "readOnly"), unitOfWork.modes());
    }
}
