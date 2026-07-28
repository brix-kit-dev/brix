/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.service;

import java.util.Objects;

import io.brix.platform.tenant.entity.PlatformTenantFirstOwnerProjection;
import io.brix.platform.tenant.event.TenantFirstOwnerAcceptedEvent;
import io.brix.platform.tenant.repository.PlatformTenantFirstOwnerProjectionRepository;

/**
 * JPA implementation of the FIRST_OWNER projection side effect.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public final class JpaFirstOwnerProjectionWriter implements FirstOwnerProjectionWriter {

    private final PlatformTenantFirstOwnerProjectionRepository projectionRepository;

    /**
     * Creates a projection writer.
     *
     * @param projectionRepository projection repository
     */
    public JpaFirstOwnerProjectionWriter(
            PlatformTenantFirstOwnerProjectionRepository projectionRepository) {
        this.projectionRepository = Objects.requireNonNull(
            projectionRepository,
            "projectionRepository must not be null");
    }

    @Override
    public void write(String messageId, TenantFirstOwnerAcceptedEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        projectionRepository.save(PlatformTenantFirstOwnerProjection.create(
            event.tenantIdValue(),
            messageId,
            event.memberId(),
            event.profileId(),
            event.invitationId()));
    }
}
