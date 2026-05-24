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
package io.brix.platform.tenant.bootstrap;

import java.time.OffsetDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.transaction.annotation.Transactional;

import io.brix.platform.tenant.core.IdGenerator;
import io.brix.platform.tenant.entity.BootstrapState;
import io.brix.platform.tenant.entity.Identity;
import io.brix.platform.tenant.entity.PlatformAdmin;
import io.brix.platform.tenant.enums.IdentityStatus;
import io.brix.platform.tenant.enums.PlatformAdminRole;
import io.brix.platform.tenant.enums.PlatformAdminStatus;
import io.brix.platform.tenant.repository.BootstrapStateRepository;
import io.brix.platform.tenant.repository.IdentityRepository;
import io.brix.platform.tenant.repository.PlatformAdminRepository;
import io.brix.platform.tenant.security.SecretHashing;

/**
 * One-shot, idempotent bootstrap-anchor runner.
 *
 * <h3>Purpose</h3>
 * <p>Brix Platform ships without any platform administrator account. Without a
 * deterministic bootstrap path the operator would have to insert rows into
 * {@code sys_identity} / {@code sys_platform_admin} by hand. This runner
 * creates only the passwordless {@link PlatformAdminRole#BOOTSTRAP} anchor and
 * stores a short-lived setup-code hash for the dedicated setup flow.</p>
 *
 * <h3>Idempotency Contract</h3>
 * <p>The runner does <b>nothing</b> after {@code sys_bootstrap_state} has a
 * {@code completed_at} timestamp. Stage B is permanently closed.</p>
 *
 * <h3>Layer Placement</h3>
 * <ul>
 *   <li>Layer 2C (platform-tenant) &mdash; consumes only its own JPA
 *       repositories.</li>
 *   <li>Host stays ultra-thin (constraint&nbsp;6): it merely toggles
 *       {@code brix.platform.bootstrap.super-admin.enabled=true} and provides
 *       the setup code via the configuration source of its choice.</li>
 * </ul>
 *
 * <h3>Failure Semantics</h3>
 * <p>If bootstrap is enabled but the email or setup code is missing the
 * runner throws {@link IllegalStateException} which fails fast at startup.
 * Silent fallback to a default account or password is explicitly disallowed.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 * @see SuperAdminBootstrapProperties
 */
public class SuperAdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SuperAdminBootstrapRunner.class);

    private final SuperAdminBootstrapProperties properties;
    private final IdentityRepository identityRepository;
    private final PlatformAdminRepository platformAdminRepository;
    private final BootstrapStateRepository bootstrapStateRepository;
    private final IdGenerator idGenerator;

    public SuperAdminBootstrapRunner(
            SuperAdminBootstrapProperties properties,
            IdentityRepository identityRepository,
            PlatformAdminRepository platformAdminRepository,
            BootstrapStateRepository bootstrapStateRepository,
            IdGenerator idGenerator) {
        this.properties = properties;
        this.identityRepository = identityRepository;
        this.platformAdminRepository = platformAdminRepository;
        this.bootstrapStateRepository = bootstrapStateRepository;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.debug("Platform bootstrap disabled (brix.platform.bootstrap.super-admin.enabled=false)");
            return;
        }

        BootstrapState state = bootstrapStateRepository.findByIdForUpdate(BootstrapState.SINGLETON_ID)
                .orElseGet(() -> {
                    BootstrapState created = new BootstrapState();
                    created.setId(BootstrapState.SINGLETON_ID);
                    return created;
                });
        if (state.isCompleted()) {
            log.info("Platform bootstrap skipped: permanently closed at {}", state.getCompletedAt());
            return;
        }

        String email = trimToNull(properties.getEmail());
        String setupCode = trimToNull(properties.getSetupCode());
        if (email == null || setupCode == null) {
            throw new IllegalStateException(
                    "Platform bootstrap is enabled but email or setup-code is missing. " +
                    "Provide brix.platform.bootstrap.super-admin.email and " +
                    "brix.platform.bootstrap.super-admin.setup-code via your secret store.");
        }

        Identity identity = identityRepository.findByEmail(email)
                .map(this::updateExistingIdentity)
                .orElseGet(() -> createIdentity(email));

        PlatformAdmin admin = platformAdminRepository.findByIdentityId(identity.getId())
                .orElseGet(() -> {
                    PlatformAdmin created = new PlatformAdmin(identity.getId(), PlatformAdminRole.BOOTSTRAP);
                    created.setId(idGenerator.nextId());
                    return created;
                });
        admin.setRole(PlatformAdminRole.BOOTSTRAP);
        admin.setStatus(PlatformAdminStatus.ACTIVE);
        admin.setMfaEnabled(false);
        admin.setNotes("Passwordless bootstrap setup anchor. Not a formal platform administrator.");
        platformAdminRepository.save(admin);

        long ttlSeconds = Math.max(60L, properties.getSetupCodeTtlSeconds());
        OffsetDateTime expiresAt = OffsetDateTime.now().plusSeconds(ttlSeconds);
        state.setBootstrapIdentityId(identity.getId());
        state.openSetupCode(SecretHashing.sha256Base64Url(setupCode), expiresAt);
        bootstrapStateRepository.save(state);

        log.warn("Platform bootstrap Stage A opened for email={} (setupCodeExpiresAt={}). "
                + "No bootstrap password was created.", email, expiresAt);
    }

    private Identity createIdentity(String email) {
        Identity identity = new Identity(email, properties.getUsername());
        identity.setId(idGenerator.nextId());
        identity.setPasswordHash(null);
        identity.setStatus(IdentityStatus.PENDING_SETUP);
        identity.setMfaEnabled(false);
        identity.verifyEmail();
        return identityRepository.save(identity);
    }

    private Identity updateExistingIdentity(Identity existing) {
        platformAdminRepository.findByIdentityId(existing.getId())
            .filter(admin -> admin.getRole() != PlatformAdminRole.BOOTSTRAP)
            .ifPresent(admin -> {
                throw new IllegalStateException(
                    "Bootstrap email already belongs to a formal platform admin identity: "
                        + existing.getEmail());
            });
        existing.setPasswordHash(null);
        existing.setStatus(IdentityStatus.PENDING_SETUP);
        existing.setMfaEnabled(false);
        existing.verifyEmail();
        return identityRepository.save(existing);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
