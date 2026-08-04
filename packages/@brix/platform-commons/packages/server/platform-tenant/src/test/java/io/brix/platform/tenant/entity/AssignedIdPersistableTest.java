/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.tenant.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

import io.brix.platform.tenant.enums.TenantMemberType;

class AssignedIdPersistableTest {

    @Test
    void tenantMemberWithAssignedIdRemainsNewUntilPersisted() {
        TenantMember member = new TenantMember(100L, 500L, TenantMemberType.OWNER);
        member.setId(300L);

        assertTrue(member.isNew());

        member.setCreatedAt(OffsetDateTime.now());

        assertFalse(member.isNew());
    }

    @Test
    void userProfileWithAssignedIdRemainsNewUntilPersisted() {
        BizUserProfile profile = new BizUserProfile();
        profile.setId(400L);
        profile.setTenantId(100L);
        profile.setMemberId(300L);

        assertTrue(profile.isNew());

        profile.setCreatedAt(OffsetDateTime.now());

        assertFalse(profile.isNew());
    }

    @Test
    void platformTenantOutboxWithAssignedMessageIdRemainsNewUntilPersisted() {
        PlatformTenantOutbox outbox = new PlatformTenantOutbox();
        outbox.setMessageId("event-100");
        outbox.setCreatedAt(OffsetDateTime.now());

        assertTrue(outbox.isNew());

        outbox.markNotNew();

        assertFalse(outbox.isNew());
    }

    @Test
    void tenantAuditLogWithAssignedIdRemainsNewUntilPersisted() {
        TenantAuditLog auditLog = new TenantAuditLog();
        auditLog.setId(500L);
        auditLog.setTenantId(100L);
        auditLog.setCreatedAt(OffsetDateTime.now());

        assertTrue(auditLog.isNew());

        auditLog.markNotNew();

        assertFalse(auditLog.isNew());
    }
}
