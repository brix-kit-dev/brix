/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.brix.platform.tenant.entity.TenantInvitation;
import io.brix.platform.tenant.enums.InvitationPurpose;
import io.brix.platform.tenant.enums.InvitationStatus;
import jakarta.persistence.LockModeType;

/**
 * Repository for tenant invitation records owned by {@code platform-tenant}.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@Repository
public interface TenantInvitationRepository extends JpaRepository<TenantInvitation, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM TenantInvitation i WHERE i.tokenHash = :tokenHash")
    Optional<TenantInvitation> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT i FROM TenantInvitation i
            WHERE i.tenantId = :tenantId
              AND i.invitationPurpose = :purpose
              AND i.status = :status
            ORDER BY i.createdAt DESC
            """)
    java.util.List<TenantInvitation> findLatestByTenantAndPurposeForUpdate(
            @Param("tenantId") Long tenantId,
            @Param("purpose") InvitationPurpose purpose,
            @Param("status") InvitationStatus status,
            Pageable pageable);

    @Query("""
            SELECT i FROM TenantInvitation i
            WHERE i.tenantId = :tenantId
              AND i.invitationPurpose = :purpose
            ORDER BY i.createdAt DESC
            """)
    java.util.List<TenantInvitation> findLatestByTenantAndPurpose(
            @Param("tenantId") Long tenantId,
            @Param("purpose") InvitationPurpose purpose,
            Pageable pageable);
}
