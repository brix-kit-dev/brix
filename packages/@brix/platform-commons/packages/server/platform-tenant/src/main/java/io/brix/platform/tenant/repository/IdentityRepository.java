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
package io.brix.platform.tenant.repository;

import io.brix.platform.tenant.entity.Identity;
import io.brix.platform.tenant.enums.IdentityStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Identity entity operations.
 *
 * <p>Provides data access methods for the sys_identity table.
 * This is a system-level repository (no automatic tenant filtering)
 * as identities exist at the platform level.
 *
 * <h3>Security Considerations</h3>
 * <ul>
 *   <li>Email lookup is used for authentication - must be efficient</li>
 *   <li>Password hash should never be logged or returned in toString</li>
 *   <li>Failed login tracking should use this repository</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see Identity
 */
@Repository
public interface IdentityRepository extends JpaRepository<Identity, Long> {

    /**
     * Finds an identity by email address.
     *
     * <p>Primary method for authentication lookup.
     * Email is case-insensitive for matching.
     *
     * @param email the email address
     * @return optional containing the identity, or empty if not found
     */
    @Query("SELECT i FROM Identity i WHERE LOWER(i.email) = LOWER(:email)")
    Optional<Identity> findByEmail(@Param("email") String email);

    /**
     * Checks if an email address is already registered.
     *
     * <p>Used for registration validation.
     *
     * @param email the email to check
     * @return true if the email is already in use
     */
    @Query("SELECT CASE WHEN COUNT(i) > 0 THEN true ELSE false END FROM Identity i WHERE LOWER(i.email) = LOWER(:email)")
    boolean existsByEmail(@Param("email") String email);

    /**
     * Finds identities by status.
     *
     * @param status the status to filter by
     * @return list of identities with the specified status
     */
    List<Identity> findByStatus(IdentityStatus status);

    /**
     * Finds active identities with unverified email.
     *
     * <p>Used for reminder emails and compliance monitoring.
     *
     * @return list of identities pending email verification
     */
    @Query("SELECT i FROM Identity i WHERE i.status = 'ACTIVE' AND i.emailVerified = false")
    List<Identity> findPendingEmailVerification();

    /**
     * Finds identities that haven't logged in since a specified date.
     *
     * <p>Used for inactive account detection.
     *
     * @param since the cutoff date
     * @return list of inactive identities
     */
    @Query("SELECT i FROM Identity i WHERE i.lastLoginAt < :since OR i.lastLoginAt IS NULL")
    List<Identity> findInactiveSince(@Param("since") OffsetDateTime since);

    /**
     * Updates the last login timestamp for an identity.
     *
     * @param identityId the identity ID
     * @param loginTime the login timestamp
     */
    @Modifying
    @Query("UPDATE Identity i SET i.lastLoginAt = :loginTime, i.updatedAt = :loginTime WHERE i.id = :id")
    void updateLastLogin(@Param("id") Long identityId, @Param("loginTime") OffsetDateTime loginTime);

    /**
     * 更新指定身份的密码哈希并清除强制改密标志（S3 — 改密 / 强制改密回执）。
     *
     * <p>同步把 {@code password_must_change} 置为 {@code false}、{@code updated_at}
     * 设为当前时间。{@code @Modifying(clearAutomatically=true)} 使一级缓存失效，
     * 避免后续 {@code findById} 命中过期实体。
     *
     * @param identityId   身份 ID
     * @param newHash      新密码哈希
     * @param updatedAt    更新时间
     * @return 受影响行数（正常应为 1）
     * @since 3.2.0
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Identity i SET i.passwordHash = :hash, i.passwordMustChange = false, "
            + "i.updatedAt = :updatedAt WHERE i.id = :id")
    int updatePasswordHash(@Param("id") Long identityId,
                           @Param("hash") String newHash,
                           @Param("updatedAt") OffsetDateTime updatedAt);

    /**
     * 递增指定身份的 token_version（A3 — 密码修改时使旧令牌失效）。
     *
     * @param identityId 身份 ID
     * @param updatedAt  更新时间
     * @return 受影响行数（正常应为 1）
     * @since 3.2.1
     */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Identity i SET i.tokenVersion = i.tokenVersion + 1, i.updatedAt = :updatedAt WHERE i.id = :id")
    int incrementTokenVersion(@Param("id") Long identityId,
                              @Param("updatedAt") OffsetDateTime updatedAt);

    /**
     * Counts identities by status.
     *
     * @param status the status to count
     * @return number of identities with the specified status
     */
    long countByStatus(IdentityStatus status);

    /**
     * Searches identities by email or username.
     *
     * @param searchTerm the search term
     * @return list of matching identities
     */
    @Query("SELECT i FROM Identity i WHERE " +
           "LOWER(i.email) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(i.username) LIKE LOWER(CONCAT('%', :term, '%'))")
    List<Identity> search(@Param("term") String searchTerm);
}
