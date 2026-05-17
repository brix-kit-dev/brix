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

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.transaction.annotation.Transactional;

import io.brix.platform.tenant.core.IdGenerator;
import io.brix.platform.tenant.entity.Identity;
import io.brix.platform.tenant.entity.PlatformAdmin;
import io.brix.platform.tenant.enums.MemberStatus;
import io.brix.platform.tenant.enums.PlatformAdminRole;
import io.brix.platform.tenant.repository.IdentityRepository;
import io.brix.platform.tenant.repository.PlatformAdminRepository;
import io.runtime.sdk.capability.PasswordCapability;

/**
 * One-shot, idempotent {@code SUPER_ADMIN} bootstrap runner.
 *
 * <h3>Purpose</h3>
 * <p>Brix Platform ships without any platform administrator account. Without a
 * deterministic bootstrap path the operator would have to insert rows into
 * {@code sys_identity} / {@code sys_platform_admin} by hand &mdash; a recipe
 * for inconsistent installs and credential leakage. This runner closes that
 * gap by provisioning the very first {@link PlatformAdminRole#SUPER_ADMIN}
 * from configuration that is sourced exclusively from secret-store backed
 * properties (env vars / mounted secrets / vault).</p>
 *
 * <h3>Idempotency Contract</h3>
 * <p>The runner does <b>nothing</b> when at least one ACTIVE
 * {@code SUPER_ADMIN} already exists, regardless of whether the configured
 * email matches. This guarantees that re-deploys never overwrite an
 * operator-managed account or expose a back-door reset path.</p>
 *
 * <h3>Layer Placement</h3>
 * <ul>
 *   <li>Layer 2C (platform-tenant) &mdash; consumes only Layer 2A capability
 *       contracts ({@link PasswordCapability}) plus its own JPA repositories.</li>
 *   <li>Host stays ultra-thin (constraint&nbsp;6): it merely toggles
 *       {@code brix.platform.bootstrap.super-admin.enabled=true} and provides
 *       the credentials via the configuration source of its choice.</li>
 * </ul>
 *
 * <h3>Failure Semantics</h3>
 * <p>If bootstrap is enabled but credentials are missing or invalid the
 * runner throws {@link IllegalStateException} which fails fast at startup.
 * Silent fallback to a default account is explicitly disallowed by the
 * security red-line.</p>
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
    private final PasswordCapability passwordCapability;
    private final IdGenerator idGenerator;

    public SuperAdminBootstrapRunner(
            SuperAdminBootstrapProperties properties,
            IdentityRepository identityRepository,
            PlatformAdminRepository platformAdminRepository,
            PasswordCapability passwordCapability,
            IdGenerator idGenerator) {
        this.properties = properties;
        this.identityRepository = identityRepository;
        this.platformAdminRepository = platformAdminRepository;
        this.passwordCapability = passwordCapability;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            log.debug("SUPER_ADMIN bootstrap disabled (brix.platform.bootstrap.super-admin.enabled=false)");
            return;
        }

        long activeSuperAdmins = platformAdminRepository.countActiveByRole(PlatformAdminRole.SUPER_ADMIN);
        if (activeSuperAdmins > 0) {
            log.info("SUPER_ADMIN bootstrap skipped: {} active SUPER_ADMIN account(s) already exist", activeSuperAdmins);
            return;
        }

        String email = trimToNull(properties.getEmail());
        String rawPassword = trimToNull(properties.getPassword());
        if (email == null || rawPassword == null) {
            throw new IllegalStateException(
                    "SUPER_ADMIN bootstrap is enabled but credentials are missing. " +
                    "Provide brix.platform.bootstrap.super-admin.email and " +
                    "brix.platform.bootstrap.super-admin.password via your secret store.");
        }

        Identity identity = identityRepository.findByEmail(email)
                .map(existing -> updateExistingIdentity(existing, rawPassword))
                .orElseGet(() -> createIdentity(email, rawPassword));

        PlatformAdmin admin = new PlatformAdmin(identity.getId(), PlatformAdminRole.SUPER_ADMIN);
        admin.setId(idGenerator.nextId());
        admin.setStatus(MemberStatus.ACTIVE);
        if (properties.isRequireMfa()) {
            admin.enableMfa();
        }
        admin.setNotes("Bootstrap-created SUPER_ADMIN. Operator MUST rotate password and enroll MFA on first login.");
        platformAdminRepository.save(admin);

        log.warn("SUPER_ADMIN bootstrap COMPLETED for email={} (mfaEnabled={}). " +
                "Operator MUST rotate the password and enroll MFA before exposing the admin console.",
                email, admin.isMfaEnabled());
    }

    private Identity createIdentity(String email, String rawPassword) {
        Identity identity = new Identity(email, properties.getUsername());
        identity.setId(idGenerator.nextId());
        identity.setPasswordHash(passwordCapability.hash(rawPassword));
        identity.verifyEmail(); // implicitly transitions PENDING -> ACTIVE
        identity.requirePasswordChange(); // operator MUST rotate the seeded password on first login
        return identityRepository.save(identity);
    }

    private Identity updateExistingIdentity(Identity existing, String rawPassword) {
        // Only rotate the password if the existing identity has none, otherwise
        // refuse to overwrite operator-managed credentials silently.
        boolean rotated = false;
        if (Optional.ofNullable(existing.getPasswordHash()).filter(h -> !h.isBlank()).isEmpty()) {
            existing.setPasswordHash(passwordCapability.hash(rawPassword));
            rotated = true;
        }
        if (!existing.isActive()) {
            existing.activate();
        }
        if (!existing.canLogin()) {
            existing.verifyEmail();
        }
        if (rotated) {
            // We just seeded a new password; force the operator to rotate it.
            existing.requirePasswordChange();
        }
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
