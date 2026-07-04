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

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import io.brix.platform.tenant.entity.BizUserProfile;

/**
 * Repository for BizUserProfile entity operations.
 *
 * <p>Provides data access for the {@code biz_user_profile} table,
 * primarily used by {@code TenantSettingsService} to read and update
 * user preferences for the three-layer configuration merge.</p>
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Platform Commons — data access for user profiles.</p>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see BizUserProfile
 */
@Repository
public interface BizUserProfileRepository extends JpaRepository<BizUserProfile, Long> {

    Optional<BizUserProfile> findByTenantIdAndMemberId(Long tenantId, Long memberId);

    Optional<BizUserProfile> findByTenantIdAndPrincipalId(Long tenantId, Long principalId);
}
