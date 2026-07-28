/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.brix.platform.tenant.entity.PlatformTenantInbox;
import io.brix.platform.tenant.entity.PlatformTenantInboxId;

/**
 * Repository for canonical {@code platform-tenant} Inbox receipts.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@Repository
public interface PlatformTenantInboxRepository extends JpaRepository<PlatformTenantInbox, PlatformTenantInboxId> {
}
