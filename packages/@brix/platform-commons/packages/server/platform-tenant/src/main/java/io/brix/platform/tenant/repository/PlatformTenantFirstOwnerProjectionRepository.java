/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.brix.platform.tenant.entity.PlatformTenantFirstOwnerProjection;

/**
 * Repository for FIRST_OWNER accepted projection side effects.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@Repository
public interface PlatformTenantFirstOwnerProjectionRepository
        extends JpaRepository<PlatformTenantFirstOwnerProjection, Long> {
}
