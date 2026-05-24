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
package io.brix.platform.admin.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.brix.platform.admin.dto.BootstrapSessionRequest;
import io.brix.platform.admin.dto.BootstrapSessionResponse;
import io.brix.platform.admin.dto.BootstrapStatusResponse;
import io.brix.platform.admin.dto.CreateFirstAdminRequest;
import io.brix.platform.admin.dto.CreatePlatformAdminResponse;
import io.brix.platform.admin.service.BootstrapSetupService;
import io.brix.platform.admin.service.BootstrapTokenService;
import io.brix.platform.auth.PlatformPermissions;
import io.brix.platform.auth.annotation.Anonymous;
import io.brix.platform.auth.annotation.RequirePermission;
import io.brix.platform.tenant.entity.BootstrapState;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** Dedicated Bootstrap Setup Flow endpoints. */
@RestController
@RequestMapping("/api/platform/bootstrap")
public class BootstrapController {

    private final BootstrapTokenService bootstrapTokenService;
    private final BootstrapSetupService bootstrapSetupService;

    public BootstrapController(
            BootstrapTokenService bootstrapTokenService,
            BootstrapSetupService bootstrapSetupService) {
        this.bootstrapTokenService = bootstrapTokenService;
        this.bootstrapSetupService = bootstrapSetupService;
    }

    @Anonymous
    @GetMapping("/status")
    public ResponseEntity<BootstrapStatusResponse> status() {
        BootstrapState state = bootstrapTokenService.readState();
        boolean open = state != null && state.isSetupCodeUsable(java.time.OffsetDateTime.now());
        return ResponseEntity.ok(new BootstrapStatusResponse(
                open,
                state != null && open ? state.getSetupCodeExpiresAt() : null,
                state != null ? state.getCompletedAt() : null));
    }

    @Anonymous
    @PostMapping("/session")
    public ResponseEntity<BootstrapSessionResponse> session(
            @Valid @RequestBody BootstrapSessionRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(bootstrapTokenService.openSession(request.setupCode(), extractClientIp(httpRequest)));
    }

    @PostMapping("/create-first-admin")
    @RequirePermission(PlatformPermissions.BOOTSTRAP_CREATE_FIRST_ADMIN)
    public ResponseEntity<CreatePlatformAdminResponse> createFirstAdmin(
            @Valid @RequestBody CreateFirstAdminRequest request) {
        CreatePlatformAdminResponse response = bootstrapSetupService.createFirstAdmin(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    private static String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            int comma = forwardedFor.indexOf(',');
            return (comma > 0 ? forwardedFor.substring(0, comma) : forwardedFor).trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }
}
