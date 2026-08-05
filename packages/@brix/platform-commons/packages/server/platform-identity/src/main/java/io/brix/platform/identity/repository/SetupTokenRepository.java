/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.brix.platform.identity.repository;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import io.brix.platform.identity.entity.SetupToken;

/** Repository for one-time setup tokens. */
@Repository
public interface SetupTokenRepository extends JpaRepository<SetupToken, Long> {

    Optional<SetupToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE PlatformIdentitySetupToken t SET t.usedAt = :usedAt, t.updatedAt = :usedAt "
            + "WHERE t.identityId = :identityId AND t.purpose = :purpose AND t.usedAt IS NULL")
    int markActiveTokensUsed(@Param("identityId") Long identityId,
                             @Param("purpose") String purpose,
                             @Param("usedAt") OffsetDateTime usedAt);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE PlatformIdentitySetupToken t SET t.usedAt = :usedAt, t.updatedAt = :usedAt "
            + "WHERE t.identityId = :identityId AND t.usedAt IS NULL")
    int markAllActiveTokensUsed(@Param("identityId") Long identityId,
                                @Param("usedAt") OffsetDateTime usedAt);
}
