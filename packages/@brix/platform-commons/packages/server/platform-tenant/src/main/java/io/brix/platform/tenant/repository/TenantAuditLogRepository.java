/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.brix.platform.tenant.entity.TenantAuditLog;

/**
 * Repository for tenant-scoped audit records owned by platform-tenant.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@Repository
public interface TenantAuditLogRepository extends JpaRepository<TenantAuditLog, Long> {
}
