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
package io.brix.platform.admin.service;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import io.brix.platform.admin.dto.BootstrapSessionResponse;
import io.brix.platform.auth.AuditAction;
import io.brix.platform.auth.PlatformPermissions;
import io.brix.platform.auth.RoleCode;
import io.brix.platform.auth.context.AuthenticatedUser;
import io.brix.platform.auth.context.SecurityContextHolder;
import io.brix.platform.auth.enums.TokenType;
import io.brix.platform.tenant.bootstrap.SuperAdminBootstrapProperties;
import io.brix.platform.tenant.dto.AuditEvent;
import io.brix.platform.tenant.entity.BootstrapState;
import io.brix.platform.tenant.entity.Identity;
import io.brix.platform.tenant.entity.PlatformAdmin;
import io.brix.platform.tenant.enums.IdentityStatus;
import io.brix.platform.tenant.enums.PlatformAdminRole;
import io.brix.platform.tenant.repository.BootstrapStateRepository;
import io.brix.platform.tenant.repository.IdentityRepository;
import io.brix.platform.tenant.repository.PlatformAdminRepository;
import io.brix.platform.tenant.security.SecretHashing;
import io.brix.platform.tenant.service.AuditService;
import io.runtime.sdk.capability.JwtIssuerCapability;

/** Opens and validates dedicated Bootstrap Setup JWT sessions. */
@Service
public class BootstrapTokenService {

    private final BootstrapStateRepository bootstrapStateRepository;
    private final IdentityRepository identityRepository;
    private final PlatformAdminRepository platformAdminRepository;
    private final JwtIssuerCapability jwtIssuerCapability;
    private final SuperAdminBootstrapProperties bootstrapProperties;
    private final SecurityContextHolder securityContextHolder;
    private final AuditService auditService;

    public BootstrapTokenService(
            BootstrapStateRepository bootstrapStateRepository,
            IdentityRepository identityRepository,
            PlatformAdminRepository platformAdminRepository,
            JwtIssuerCapability jwtIssuerCapability,
            SuperAdminBootstrapProperties bootstrapProperties,
            SecurityContextHolder securityContextHolder,
            AuditService auditService) {
        this.bootstrapStateRepository = bootstrapStateRepository;
        this.identityRepository = identityRepository;
        this.platformAdminRepository = platformAdminRepository;
        this.jwtIssuerCapability = jwtIssuerCapability;
        this.bootstrapProperties = bootstrapProperties;
        this.securityContextHolder = securityContextHolder;
        this.auditService = auditService;
    }

    @Transactional
    public BootstrapSessionResponse openSession(String setupCode, String clientIp) {
        BootstrapState state = bootstrapStateRepository.findByIdForUpdate(BootstrapState.SINGLETON_ID)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "BOOTSTRAP_NOT_OPEN"));
        OffsetDateTime now = OffsetDateTime.now();
        if (!state.isSetupCodeUsable(now) || !SecretHashing.matches(setupCode, state.getSetupCodeHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "BOOTSTRAP_SETUP_CODE_INVALID");
        }

        Identity identity = identityRepository.findById(state.getBootstrapIdentityId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "BOOTSTRAP_IDENTITY_MISSING"));
        PlatformAdmin bootstrapAdmin = platformAdminRepository.findByIdentityId(identity.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "BOOTSTRAP_ADMIN_MISSING"));
        if (identity.getStatus() != IdentityStatus.PENDING_SETUP
                || identity.getPasswordHash() != null
                || identity.isMfaEnabled()
                || !bootstrapAdmin.isActive()
                || bootstrapAdmin.getRole() != PlatformAdminRole.BOOTSTRAP) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "BOOTSTRAP_ANCHOR_INVALID");
        }

        long ttlSeconds = Math.max(30L, bootstrapProperties.getBootstrapSessionTtlSeconds());
        String jti = UUID.randomUUID().toString();
        state.activateSession(jti, now.plusSeconds(ttlSeconds));
        bootstrapStateRepository.save(state);

        String accessToken = jwtIssuerCapability.issueBootstrapSetupToken(
                new JwtIssuerCapability.BootstrapSetupTokenRequest(
                        bootstrapAdmin.getId(),
                        identity.getId(),
                        identity.getEmail(),
                        identity.getUsername(),
                        PlatformPermissions.defaultPermissionsFor(RoleCode.BOOTSTRAP),
                        identity.getTokenVersion(),
                        ttlSeconds,
                        jti));

        auditService.log(AuditEvent.builder()
                .createdBy(identity.getId())
                .action(AuditAction.BOOTSTRAP_SESSION_OPENED)
                .resourceType("BOOTSTRAP")
                .resourceId(String.valueOf(identity.getId()))
                .description("Bootstrap setup session opened.")
                .clientIp(clientIp)
                .success(true)
                .build());
        return new BootstrapSessionResponse(TokenType.BOOTSTRAP_SETUP.getValue(), accessToken, ttlSeconds);
    }

    @Transactional(readOnly = true)
    public BootstrapState readState() {
        return bootstrapStateRepository.findById(BootstrapState.SINGLETON_ID).orElse(null);
    }

    @Transactional
    public ValidBootstrapSession requireCurrentSessionForUpdate() {
        AuthenticatedUser user = securityContextHolder.getCurrentUser()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "BOOTSTRAP_TOKEN_REQUIRED"));
        if (!user.isBootstrapSetupToken()
                || !RoleCode.BOOTSTRAP.equals(user.getPlatformRole())
                || !RoleCode.BOOTSTRAP.equals(user.getScope())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "BOOTSTRAP_TOKEN_REQUIRED");
        }
        BootstrapState state = bootstrapStateRepository.findByIdForUpdate(BootstrapState.SINGLETON_ID)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "BOOTSTRAP_NOT_OPEN"));
        if (!state.isBootstrapSessionUsable(user.getJti(), OffsetDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "BOOTSTRAP_SESSION_INVALID");
        }
        if (state.getBootstrapIdentityId() == null
                || !String.valueOf(state.getBootstrapIdentityId()).equals(user.getUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "BOOTSTRAP_SESSION_INVALID");
        }
        return new ValidBootstrapSession(state, user);
    }

    public record ValidBootstrapSession(BootstrapState state, AuthenticatedUser user) {
    }
}