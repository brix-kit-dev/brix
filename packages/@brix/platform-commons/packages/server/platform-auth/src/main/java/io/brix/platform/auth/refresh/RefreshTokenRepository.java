/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.auth.refresh;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * JPA repository for {@link StoredRefreshToken}.
 *
 * @since 3.2.1
 */
public interface RefreshTokenRepository extends JpaRepository<StoredRefreshToken, Long> {

    Optional<StoredRefreshToken> findByTokenId(String tokenId);

    @Query("SELECT t FROM StoredRefreshToken t WHERE t.identityId = :identityId AND t.revokedAt IS NULL")
    List<StoredRefreshToken> findActiveByIdentityId(@Param("identityId") Long identityId);

    @Modifying
    @Query("UPDATE StoredRefreshToken t SET t.revokedAt = :revokedAt, t.revokeReason = :reason "
            + "WHERE t.identityId = :identityId AND t.revokedAt IS NULL")
    int revokeAllByIdentityId(@Param("identityId") Long identityId,
                              @Param("revokedAt") OffsetDateTime revokedAt,
                              @Param("reason") String reason);
}
