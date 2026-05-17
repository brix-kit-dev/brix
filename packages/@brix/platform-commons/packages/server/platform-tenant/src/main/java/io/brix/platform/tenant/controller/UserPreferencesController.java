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
package io.brix.platform.tenant.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.brix.platform.common.tenant.TenantContext;
import io.brix.platform.tenant.dto.UserPreferencesDto;
import io.brix.platform.tenant.service.TenantSettingsService;
import jakarta.validation.Valid;

/**
 * User Preferences REST Controller.
 *
 * <p>Provides APIs for authenticated users to manage their per-tenant preferences
 * stored in {@code biz_user_profile.preferences} JSONB column.</p>
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Platform Commons — REST endpoint for user preferences.</p>
 *
 * <h3>API Endpoints</h3>
 * <ul>
 *   <li>GET   /api/v1/user/preferences  — Get user preferences</li>
 *   <li>PATCH /api/v1/user/preferences  — Update user preferences</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 */
@RestController
@RequestMapping("/api/v1/user")
public class UserPreferencesController {

    private static final Logger log = LoggerFactory.getLogger(UserPreferencesController.class);

    private final TenantSettingsService settingsService;

    public UserPreferencesController(TenantSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    /**
     * Gets the current user's preferences.
     */
    @GetMapping("/preferences")
    public ResponseEntity<?> getPreferences() {
        Long userId = requireUserId();
        UserPreferencesDto prefs = settingsService.getUserPreferences(userId);
        return ResponseEntity.ok(prefs);
    }

    /**
     * Partially updates the current user's preferences (PATCH semantics).
     */
    @PatchMapping("/preferences")
    public ResponseEntity<?> updatePreferences(@Valid @RequestBody UserPreferencesDto dto) {
        Long userId = requireUserId();
        settingsService.updateUserPreferences(userId, dto);
        return ResponseEntity.ok(Map.of("success", true));
    }

    // ========================================================================
    // Private Helpers
    // ========================================================================

    private Long requireUserId() {
        return TenantContext.getUserId()
                .filter(s -> !s.isBlank())
                .map(Long::parseLong)
                .orElseThrow(() -> new IllegalStateException("User context not available"));
    }
}
