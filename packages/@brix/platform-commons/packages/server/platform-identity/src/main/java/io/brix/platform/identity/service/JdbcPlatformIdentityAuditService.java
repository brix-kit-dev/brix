/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.identity.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import io.brix.platform.identity.core.IdGenerator;
import io.brix.platform.identity.dto.AuditEvent;

/**
 * Minimal platform audit sink owned by platform-identity.
 */
public class JdbcPlatformIdentityAuditService implements AuditService {

    private final JdbcTemplate jdbcTemplate;
    private final IdGenerator idGenerator;

    public JdbcPlatformIdentityAuditService(JdbcTemplate jdbcTemplate, IdGenerator idGenerator) {
        this.jdbcTemplate = jdbcTemplate;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditEvent event) {
        Assert.notNull(event, "AuditEvent cannot be null");
        Assert.hasText(event.getAction(), "Audit action is required");
        Assert.hasText(event.getResourceType(), "Audit resourceType is required");
        jdbcTemplate.update(
            """
            INSERT INTO sys_platform_audit_log
                (id, operator_identity_id, action, resource_type, resource_id,
                 affected_tenants, description, context, success, error_code, created_at)
            VALUES (?, ?, ?, ?, ?, '[]'::jsonb, ?, NULL, ?, NULL, CURRENT_TIMESTAMP)
            """,
            idGenerator.nextId(),
            event.getCreatedBy(),
            event.getAction(),
            event.getResourceType(),
            event.getResourceId(),
            event.getDescription(),
            event.isSuccess());
    }
}
