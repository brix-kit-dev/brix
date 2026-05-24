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

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.brix.platform.admin.config.PlatformAdminSetupProperties;
import io.brix.platform.auth.AuditAction;
import io.brix.platform.tenant.core.IdGenerator;
import io.brix.platform.tenant.dto.AuditEvent;
import io.brix.platform.tenant.entity.SetupToken;
import io.brix.platform.tenant.repository.SetupTokenRepository;
import io.brix.platform.tenant.security.SecretHashing;
import io.brix.platform.tenant.service.AuditService;

/** Issues one-time setup tokens for platform administrator onboarding. */
@Service
public class SetupTokenService {

    public static final String PURPOSE_INITIAL_SETUP = "INITIAL_SETUP";
    public static final String PURPOSE_PASSWORD_RESET = "PASSWORD_RESET";
    public static final String PURPOSE_PLATFORM_ADMIN_SETUP = PURPOSE_INITIAL_SETUP;

    private final SetupTokenRepository setupTokenRepository;
    private final PlatformAdminSetupProperties setupProperties;
    private final IdGenerator idGenerator;
    private final AuditService auditService;
    private final SecureRandom secureRandom = new SecureRandom();

    public SetupTokenService(
            SetupTokenRepository setupTokenRepository,
            PlatformAdminSetupProperties setupProperties,
            IdGenerator idGenerator,
            AuditService auditService) {
        this.setupTokenRepository = setupTokenRepository;
        this.setupProperties = setupProperties;
        this.idGenerator = idGenerator;
        this.auditService = auditService;
    }

    @Transactional
    public IssuedSetupToken issue(Long identityId, String purpose, Long issuedBy) {
        if (identityId == null) {
            throw new IllegalArgumentException("identityId is required");
        }
        if (!PURPOSE_INITIAL_SETUP.equals(purpose) && !PURPOSE_PASSWORD_RESET.equals(purpose)) {
            throw new IllegalArgumentException("Unsupported setup token purpose: " + purpose);
        }
        OffsetDateTime now = OffsetDateTime.now();
        setupTokenRepository.markActiveTokensUsed(identityId, purpose, now);

        String rawToken = generateToken();
        long ttlSeconds = Math.max(300L, setupProperties.getTokenTtlSeconds());
        SetupToken token = new SetupToken();
        token.setId(idGenerator.nextId());
        token.setIdentityId(identityId);
        token.setPurpose(purpose);
        token.setTokenHash(SecretHashing.sha256Base64Url(rawToken));
        token.setExpiresAt(now.plusSeconds(ttlSeconds));
        token.setCreatedBy(issuedBy);
        setupTokenRepository.saveAndFlush(token);
        auditService.log(AuditEvent.builder()
            .createdBy(issuedBy)
            .action(AuditAction.SETUP_TOKEN_ISSUED)
            .resourceType("SETUP_TOKEN")
            .resourceId(String.valueOf(token.getId()))
            .description("Platform setup token issued for purpose " + purpose + ".")
            .success(true)
            .build());
        return new IssuedSetupToken(rawToken, purpose, token.getExpiresAt());
    }

    @Transactional
    public IssuedSetupToken issuePlatformAdminSetupToken(Long identityId, Long createdBy) {
        return issue(identityId, PURPOSE_INITIAL_SETUP, createdBy);
    }

    @Transactional
    public SetupToken validate(String rawToken) {
        SetupToken token;
        try {
            token = loadByRawToken(rawToken);
        } catch (IllegalArgumentException ex) {
            auditInvalidToken(null, "NOT_FOUND");
            throw ex;
        }
        if (!token.isUsable(OffsetDateTime.now())) {
            auditInvalidToken(token, "EXPIRED_OR_USED");
            throw new IllegalArgumentException("Setup token is expired or already used");
        }
        return token;
    }

    @Transactional
    public SetupToken consume(String rawToken) {
        SetupToken token = validate(rawToken);
        token.setUsedAt(OffsetDateTime.now());
        SetupToken saved = setupTokenRepository.save(token);
        auditService.log(AuditEvent.builder()
            .createdBy(token.getIdentityId())
            .action(AuditAction.SETUP_TOKEN_USED)
            .resourceType("SETUP_TOKEN")
            .resourceId(String.valueOf(token.getId()))
            .description("Platform setup token consumed for purpose " + token.getPurpose() + ".")
            .success(true)
            .build());
        return saved;
    }

    @Transactional
    public void invalidatePreviousFor(Long identityId) {
        if (identityId == null) {
            throw new IllegalArgumentException("identityId is required");
        }
        setupTokenRepository.markAllActiveTokensUsed(identityId, OffsetDateTime.now());
    }

    private SetupToken loadByRawToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("setup token is required");
        }
        String hash = SecretHashing.sha256Base64Url(rawToken);
        return setupTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new IllegalArgumentException("Setup token is invalid"));
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void auditInvalidToken(SetupToken token, String reasonCode) {
        auditService.log(AuditEvent.builder()
                .createdBy(token != null ? token.getIdentityId() : null)
                .action(AuditAction.SETUP_TOKEN_INVALID)
                .resourceType("SETUP_TOKEN")
                .resourceId(token != null ? String.valueOf(token.getId()) : null)
                .description("Platform setup token validation failed with reason " + reasonCode + ".")
                .success(false)
                .errorCode(reasonCode)
                .build());
    }

    public record IssuedSetupToken(String token, String purpose, OffsetDateTime expiresAt) {
    }
}
