/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.brix.platform.auth.flow;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.brix.platform.auth.internal.RbacResolver;
import io.runtime.sdk.capability.AuthFlowCapability;
import io.runtime.sdk.capability.AuthFlowCapability.AuthFlowException;
import io.runtime.sdk.capability.IdentityTenantCapability;
import io.runtime.sdk.capability.IdentityTenantCapability.IdentityRecord;
import io.runtime.sdk.capability.IdentityTenantCapability.PlatformAdminRecord;
import io.runtime.sdk.capability.IdentityTenantCapability.TenantMembershipRecord;
import io.runtime.sdk.capability.IdentityTenantCapability.TenantPrincipalRecord;
import io.runtime.sdk.capability.JwtIssuerCapability;
import io.runtime.sdk.capability.PasswordCapability;
import io.runtime.sdk.capability.RefreshTokenCapability;

/**
 * <h2>Auth Flow Capability — Default Multi-Tenant Implementation</h2>
 *
 * <p>Layer 2C platform binding for the {@link AuthFlowCapability} contract.
 * Migrates the multi-tenant login orchestration that previously lived inside
 * the plugin-side {@code AuthServiceImpl} (D2 step of v3.2.0 architecture
 * remediation).</p>
 *
 * <h3>Status Decision Matrix</h3>
 * <ul>
 *   <li>0 active associations → {@link AuthFlowException#CODE_NO_TENANT_ASSOCIATION}</li>
 *   <li>1 association (B or C) → auto-select → {@link LoginStatus#COMPLETE}</li>
 *   <li>≥2 associations → issue Identity Token + tenant list → {@link LoginStatus#SELECT_TENANT}</li>
 * </ul>
 *
 * <h3>Dependencies</h3>
 * <ul>
 *   <li>{@link IdentityTenantCapability} — sys_identity / sys_tenant_member / sys_tenant_principal queries</li>
 *   <li>{@link PasswordCapability} — BCrypt verification</li>
 *   <li>{@link JwtIssuerCapability} — token signing</li>
 *   <li>{@link RbacResolver} (optional internal SPI) — fallback to membership type if absent</li>
 * </ul>
 *
 * @since 3.2.0
 */
public class AuthFlowCapabilityImpl implements AuthFlowCapability {

    private static final Logger log = LoggerFactory.getLogger(AuthFlowCapabilityImpl.class);

    private static final String IDENTITY_STATUS_ACTIVE = "ACTIVE";
        private static final String IDENTITY_STATUS_PENDING_SETUP = "PENDING_SETUP";
        private static final String IDENTITY_STATUS_LOCKED = "LOCKED";
    private static final String ROLE_TYPE_ACTOR = "actor";
    private static final String ROLE_TYPE_SUBJECT = "subject";

    private final IdentityTenantCapability identityTenantCapability;
    private final PasswordCapability passwordCapability;
    private final JwtIssuerCapability jwtIssuerCapability;
    private final RbacResolver rbacResolver;
    /** A2: optional — when absent, refresh token rotation is unavailable. */
    private final RefreshTokenCapability refreshTokenCapability;
        private final Supplier<MfaLoginSupport> mfaLoginSupportSupplier;
        private final PlatformLoginLockoutProperties lockoutProperties;

    public AuthFlowCapabilityImpl(IdentityTenantCapability identityTenantCapability,
                                  PasswordCapability passwordCapability,
                                  JwtIssuerCapability jwtIssuerCapability,
                                  RbacResolver rbacResolver) {
        this(identityTenantCapability, passwordCapability, jwtIssuerCapability, rbacResolver, null);
    }

    public AuthFlowCapabilityImpl(IdentityTenantCapability identityTenantCapability,
                                  PasswordCapability passwordCapability,
                                  JwtIssuerCapability jwtIssuerCapability,
                                  RbacResolver rbacResolver,
                                  RefreshTokenCapability refreshTokenCapability) {
        this(identityTenantCapability, passwordCapability, jwtIssuerCapability, rbacResolver,
                refreshTokenCapability, () -> null);
    }

    public AuthFlowCapabilityImpl(IdentityTenantCapability identityTenantCapability,
                                  PasswordCapability passwordCapability,
                                  JwtIssuerCapability jwtIssuerCapability,
                                  RbacResolver rbacResolver,
                                  RefreshTokenCapability refreshTokenCapability,
                                  Supplier<MfaLoginSupport> mfaLoginSupportSupplier) {
        this(identityTenantCapability, passwordCapability, jwtIssuerCapability, rbacResolver,
                refreshTokenCapability, mfaLoginSupportSupplier, new PlatformLoginLockoutProperties());
    }

    public AuthFlowCapabilityImpl(IdentityTenantCapability identityTenantCapability,
                                  PasswordCapability passwordCapability,
                                  JwtIssuerCapability jwtIssuerCapability,
                                  RbacResolver rbacResolver,
                                  RefreshTokenCapability refreshTokenCapability,
                                  Supplier<MfaLoginSupport> mfaLoginSupportSupplier,
                                  PlatformLoginLockoutProperties lockoutProperties) {
        this.identityTenantCapability = identityTenantCapability;
        this.passwordCapability = passwordCapability;
        this.jwtIssuerCapability = jwtIssuerCapability;
        this.rbacResolver = rbacResolver;
        this.refreshTokenCapability = refreshTokenCapability;
        this.mfaLoginSupportSupplier = mfaLoginSupportSupplier != null ? mfaLoginSupportSupplier : () -> null;
        this.lockoutProperties = lockoutProperties != null ? lockoutProperties : new PlatformLoginLockoutProperties();
    }

    // ==================== AuthFlowCapability ====================

    @Override
    public LoginResult login(LoginCommand command) {
        IdentityRecord identity = authenticateIdentity(command);

        List<TenantMembershipRecord> memberships =
                identityTenantCapability.getActiveMemberships(identity.id());
        List<TenantPrincipalRecord> principalships =
                identityTenantCapability.getActivePrincipalships(identity.id());

        int total = memberships.size() + principalships.size();

        if (total == 0) {
            log.warn("[AuthFlow] login — no active tenant associations: identity={} ({})",
                    identity.id(), command.loginId());
            throw new AuthFlowException(
                    AuthFlowException.CODE_NO_TENANT_ASSOCIATION,
                    "No active tenant associations. Please contact the administrator.");
        }

        if (total == 1) {
            if (!memberships.isEmpty()) {
                TenantMembershipRecord m = memberships.get(0);
                identityTenantCapability.touchMemberAccess(m.memberId());
                log.info("[AuthFlow] auto-select actor: identity={}, tenant={}",
                        identity.id(), m.tenantId());
                return buildActorLoginResult(identity, m);
            }
            TenantPrincipalRecord p = principalships.get(0);
            identityTenantCapability.touchPrincipalAccess(p.principalId());
            log.info("[AuthFlow] auto-select subject: identity={}, tenant={}",
                    identity.id(), p.tenantId());
            return buildSubjectLoginResult(identity, p);
        }

        // ≥2 associations → SELECT_TENANT
        String identityToken = jwtIssuerCapability.issueIdentityToken(
                new JwtIssuerCapability.IdentityTokenRequest(
                        identity.id(), identity.email(), identity.username()));
        List<TenantOption> tenantOptions = buildTenantOptions(memberships, principalships);
        log.info("[AuthFlow] SELECT_TENANT: identity={}, tenants={}", identity.id(), total);

        return new LoginResult(
                LoginStatus.SELECT_TENANT,
                /* accessToken */ null,
                /* refreshToken */ null,
                /* expiresIn */ 0L,
                /* identityToken */ identityToken,
                /* tenantOptions */ tenantOptions,
                /* identityId */ identity.id(),
                /* displayName */ null,
                /* email */ identity.email(),
                /* primaryRole */ null,
                /* roles */ Collections.emptyList(),
                /* permissions */ Collections.emptyList(),
                /* mustChangePassword */ identity.passwordMustChange(),
                /* mfaRequired */ false);
    }

    @Override
    public LoginResult loginPlatformAdmin(LoginCommand command) {
                IdentityRecord identity = authenticateIdentity(command, true);
        PlatformAdminRecord admin = identityTenantCapability.findActivePlatformAdmin(identity.id())
                .orElseThrow(() -> {
                    log.warn("[AuthFlow] platform login rejected — identity is not an active platform admin: {}",
                            identity.id());
                    return new AuthFlowException(
                            AuthFlowException.CODE_INVALID_CREDENTIALS,
                            "Invalid credentials");
                });

        log.info("[AuthFlow] platform admin login: identity={}, role={}",
                identity.id(), admin.adminRole());
        return buildPlatformAdminLoginResult(identity, admin);
    }

    @Override
    public LoginResult selectTenant(Long identityId, SelectTenantCommand command) {
        if (identityId == null) {
            throw new AuthFlowException(
                    AuthFlowException.CODE_IDENTITY_NOT_FOUND, "identityId is required");
        }
        if (command == null || command.tenantId() == null) {
            throw new AuthFlowException(
                    AuthFlowException.CODE_TENANT_ACCESS_DENIED, "tenantId is required");
        }

        // Reload identity to re-surface passwordMustChange (Identity Token does not carry it).
        IdentityRecord identity = identityTenantCapability.findIdentityById(identityId)
                .orElseThrow(() -> new AuthFlowException(
                        AuthFlowException.CODE_IDENTITY_NOT_FOUND, "Identity not found"));

        Long targetTenantId = command.tenantId();

        Optional<TenantMembershipRecord> mOpt =
                identityTenantCapability.findMembership(identityId, targetTenantId);
        if (mOpt.isPresent()) {
            TenantMembershipRecord m = mOpt.get();
            identityTenantCapability.touchMemberAccess(m.memberId());
            return buildActorLoginResult(identity, m);
        }

        Optional<TenantPrincipalRecord> pOpt =
                identityTenantCapability.findPrincipalship(identityId, targetTenantId);
        if (pOpt.isPresent()) {
            TenantPrincipalRecord p = pOpt.get();
            identityTenantCapability.touchPrincipalAccess(p.principalId());
            return buildSubjectLoginResult(identity, p);
        }

        log.warn("[AuthFlow] selectTenant denied — identity={}, tenant={}",
                identityId, targetTenantId);
        throw new AuthFlowException(
                AuthFlowException.CODE_TENANT_ACCESS_DENIED,
                "You do not have access to the selected tenant");
    }

    @Override
    public LoginResult refreshToken(RefreshCommand command) {
        if (command == null || command.refreshToken() == null || command.refreshToken().isBlank()) {
            throw new AuthFlowException(
                    AuthFlowException.CODE_INVALID_REFRESH_TOKEN, "refreshToken is required");
        }
        if (refreshTokenCapability == null) {
            log.warn("[AuthFlow] RefreshTokenCapability not available — cannot rotate refresh token");
            throw new AuthFlowException(
                    AuthFlowException.CODE_INVALID_REFRESH_TOKEN,
                    "Refresh token rotation is not available — please re-authenticate");
        }
        // A2: validate and rotate
        RefreshTokenCapability.RotatedToken rotated =
                refreshTokenCapability.validateAndRotate(command.refreshToken())
                        .orElseThrow(() -> {
                            log.warn("[AuthFlow] refresh token invalid or expired");
                            return new AuthFlowException(
                                    AuthFlowException.CODE_INVALID_REFRESH_TOKEN,
                                    "Refresh token is invalid, expired, or already used");
                        });

        // Re-issue access token for the identity
        IdentityRecord identity = identityTenantCapability.findIdentityById(rotated.identityId())
                .orElseThrow(() -> new AuthFlowException(
                        AuthFlowException.CODE_IDENTITY_NOT_FOUND, "Identity not found"));

        if (rotated.adminId() != null) {
            // Refresh for a platform admin session
            PlatformAdminRecord admin = identityTenantCapability
                    .findActivePlatformAdmin(identity.id())
                    .orElseThrow(() -> new AuthFlowException(
                            AuthFlowException.CODE_ACCOUNT_DISABLED,
                            "Platform admin account is no longer active"));
            return buildPlatformAdminLoginResult(identity, admin);
        }

        // For tenant member/principal refresh, re-authenticate requires tenant selection.
        // Return a SELECT_TENANT flow token so the client can re-select tenant.
        String identityToken = jwtIssuerCapability.issueIdentityToken(
                new JwtIssuerCapability.IdentityTokenRequest(
                        identity.id(), identity.email(), identity.username()));
        return new LoginResult(
                LoginStatus.SELECT_TENANT,
                null, rotated.newTokenId(),
                jwtIssuerCapability.getIdentityTokenExpirationSeconds(),
                identityToken,
                null,
                identity.id(), null, identity.email(),
                null,
                Collections.emptyList(), Collections.emptyList(),
                identity.passwordMustChange(), false);
    }

    @Override
    public LoginResult mfaVerify(MfaVerifyCommand command) {
                MfaLoginSupport mfaLoginSupport = mfaLoginSupportSupplier.get();
                if (mfaLoginSupport == null) {
                        throw new AuthFlowException(
                                        AuthFlowException.CODE_CAPABILITY_UNAVAILABLE,
                                        "MFA/TOTP verification capability is unavailable.");
                }
                return mfaLoginSupport.verify(command);
    }

    @Override
    public void changePassword(ChangePasswordCommand command) {
        // S3 — 实现以下顺序：
        //   1. 加载身份、验证 ACTIVE
        //   2. 验证旧密码与 password_hash 匹配
        //   3. 检查新密码策略及"与旧不同"约束
        //   4. 调用 IdentityTenantCapability.updatePasswordHash() 写入新哈希并清除必须改密标志
        if (command == null || command.identityId() == null) {
            throw new AuthFlowException(
                    AuthFlowException.CODE_IDENTITY_NOT_FOUND, "identityId is required");
        }
        String oldPassword = command.oldPassword();
        String newPassword = command.newPassword();
        if (oldPassword == null || oldPassword.isBlank()) {
            throw new AuthFlowException(
                    AuthFlowException.CODE_OLD_PASSWORD_MISMATCH, "oldPassword is required");
        }
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 128) {
            throw new AuthFlowException(
                    AuthFlowException.CODE_PASSWORD_POLICY_VIOLATION,
                    "New password must be between 8 and 128 characters");
        }
        if (newPassword.equals(oldPassword)) {
            throw new AuthFlowException(
                    AuthFlowException.CODE_PASSWORD_POLICY_VIOLATION,
                    "New password must differ from the old one");
        }

        IdentityRecord identity = identityTenantCapability.findIdentityById(command.identityId())
                .orElseThrow(() -> new AuthFlowException(
                        AuthFlowException.CODE_IDENTITY_NOT_FOUND, "Identity not found"));

        if (!IDENTITY_STATUS_ACTIVE.equals(identity.status())) {
            throw new AuthFlowException(
                    AuthFlowException.CODE_ACCOUNT_DISABLED,
                    "Account is disabled. Please contact the administrator.");
        }

        if (identity.passwordHash() == null
                || !passwordCapability.verify(oldPassword, identity.passwordHash())) {
            log.warn("[AuthFlow] changePassword failed — oldPassword mismatch: identity={}",
                    identity.id());
            throw new AuthFlowException(
                    AuthFlowException.CODE_OLD_PASSWORD_MISMATCH, "Old password does not match");
        }

        String newHash = passwordCapability.hash(newPassword);
        identityTenantCapability.updatePasswordHash(identity.id(), newHash);
        // A3: increment token_version so all existing JWTs become invalid immediately.
        identityTenantCapability.incrementTokenVersion(identity.id());
        // A2: revoke all persistent refresh tokens for this identity.
        if (refreshTokenCapability != null) {
            refreshTokenCapability.revokeAllByIdentityId(identity.id());
        }
        log.info("[AuthFlow] password changed and tokens invalidated: identity={}", identity.id());
    }

    // ==================== Internals — token issuance ====================

    private LoginResult buildActorLoginResult(IdentityRecord identity, TenantMembershipRecord m) {
        // A4: block access if password must be changed — issue a short-lived
        // identity token so the client can authenticate the change-password call
        // (mirrors the MFA pattern below; without this the client has no token to
        // present to /api/auth/change-password and the bootstrap flow deadlocks).
        if (identity.passwordMustChange()) {
            log.info("[AuthFlow][A4] actor login blocked — password must change: identity={}",
                    identity.id());
            String pwdChangeToken = jwtIssuerCapability.issueIdentityToken(
                    new JwtIssuerCapability.IdentityTokenRequest(
                            identity.id(), identity.email(), identity.username()));
            return new LoginResult(
                    LoginStatus.PASSWORD_MUST_CHANGE,
                    null, null,
                    jwtIssuerCapability.getIdentityTokenExpirationSeconds(),
                    pwdChangeToken, null,
                    identity.id(), null, identity.email(),
                    null, Collections.emptyList(), Collections.emptyList(),
                    true, false);
        }

        String identityIdStr = String.valueOf(identity.id());
        String tenantIdStr = String.valueOf(m.tenantId());

        List<String> roles = resolveRoles(identityIdStr, tenantIdStr);
        if (roles.isEmpty()) {
            roles = List.of(m.memberType());
        }
        List<String> permissions = resolvePermissions(identityIdStr, tenantIdStr);

        String accessToken = jwtIssuerCapability.issueActorAccessToken(
                new JwtIssuerCapability.ActorTokenRequest(
                        identity.id(),
                        identity.email(),
                        identity.username(),
                        m.tenantId(),
                        m.memberId(),
                        m.memberType(),
                        roles,
                        permissions,
                        identity.tokenVersion()  // A3
                ));

        // A2: persist refresh token so it can be revoked on password change.
        String refreshToken = UUID.randomUUID().toString();
        if (refreshTokenCapability != null) {
            refreshTokenCapability.store(refreshToken, identity.id(), null,
                    jwtIssuerCapability.getAccessTokenExpirationSeconds() * 24); // ~1 day refresh
        }

        return new LoginResult(
                LoginStatus.COMPLETE,
                accessToken,
                refreshToken,
                jwtIssuerCapability.getAccessTokenExpirationSeconds(),
                /* identityToken */ null,
                /* tenantOptions */ null,
                identity.id(),
                /* displayName */ null,
                identity.email(),
                roles.get(0),
                roles,
                permissions,
                identity.passwordMustChange(),
                /* mfaRequired */ false);
    }

    /**
     * S3 — 构造平台管理员登录结果（无租户上下文）。
     */
    private LoginResult buildPlatformAdminLoginResult(IdentityRecord identity,
                                                                                                          PlatformAdminRecord admin) {
        // A4: block access if password must be changed — issue a short-lived
        // identity token so the client can authenticate the change-password call
        // (mirrors the MFA pattern below; used by setup/reset flows that require
        // password rotation before issuing a full access token).
        if (identity.passwordMustChange()) {
            log.info("[AuthFlow][A4] platform admin login blocked — password must change: identity={}",
                    identity.id());
            String pwdChangeToken = jwtIssuerCapability.issueIdentityToken(
                    new JwtIssuerCapability.IdentityTokenRequest(
                            identity.id(), identity.email(), identity.username()));
            return new LoginResult(
                    LoginStatus.PASSWORD_MUST_CHANGE,
                    null, null,
                    jwtIssuerCapability.getIdentityTokenExpirationSeconds(),
                    pwdChangeToken, null,
                    identity.id(), identity.username(), identity.email(),
                    null, Collections.emptyList(), Collections.emptyList(),
                    true, false);
        }

        if (!admin.mfaEnabled()) {
            log.warn("[AuthFlow][A1] platform admin login rejected — MFA is not bound: identity={}, adminId={}",
                    identity.id(), admin.adminId());
            throw new AuthFlowException(
                    AuthFlowException.CODE_MFA_SETUP_REQUIRED,
                    "Platform administrator MFA setup is required.");
        }

        log.info("[AuthFlow][A1] MFA required for admin: identity={}, adminId={}",
                identity.id(), admin.adminId());
        String challengeToken = jwtIssuerCapability.issuePlatformMfaChallengeToken(
                new JwtIssuerCapability.PlatformMfaChallengeTokenRequest(
                        admin.adminId(),
                        identity.id(),
                        identity.email(),
                        identity.username(),
                        admin.adminRole(),
                        identity.tokenVersion()));
        return new LoginResult(
                LoginStatus.MFA_REQUIRED,
                null, null,
                jwtIssuerCapability.getIdentityTokenExpirationSeconds(),
                challengeToken, null,
                identity.id(), identity.username(), identity.email(),
                null, Collections.emptyList(), Collections.emptyList(),
                false, true);

    }

    private LoginResult buildSubjectLoginResult(IdentityRecord identity, TenantPrincipalRecord p) {
        // A4: block access if password must be changed — issue a short-lived
        // identity token so the client can authenticate the change-password call
        // (mirrors the MFA pattern; without this the change-password endpoint is unreachable).
        if (identity.passwordMustChange()) {
            log.info("[AuthFlow][A4] subject login blocked — password must change: identity={}",
                    identity.id());
            String pwdChangeToken = jwtIssuerCapability.issueIdentityToken(
                    new JwtIssuerCapability.IdentityTokenRequest(
                            identity.id(), identity.email(), identity.username()));
            return new LoginResult(
                    LoginStatus.PASSWORD_MUST_CHANGE,
                    null, null,
                    jwtIssuerCapability.getIdentityTokenExpirationSeconds(),
                    pwdChangeToken, null,
                    identity.id(), null, identity.email(),
                    null, Collections.emptyList(), Collections.emptyList(),
                    true, false);
        }

        // A3: embed per-user token version in JWT.
        String accessToken = jwtIssuerCapability.issueSubjectAccessToken(
                new JwtIssuerCapability.SubjectTokenRequest(
                        identity.id(),
                        identity.email(),
                        identity.username(),
                        p.tenantId(),
                        p.principalId(),
                        p.principalType(),
                        p.displayName(),
                        identity.tokenVersion()  // A3
                ));

        // A2: persist refresh token.
        String refreshToken = UUID.randomUUID().toString();
        if (refreshTokenCapability != null) {
            refreshTokenCapability.store(refreshToken, identity.id(), null,
                    jwtIssuerCapability.getAccessTokenExpirationSeconds() * 24);
        }

        String primaryRole = p.principalType();

        return new LoginResult(
                LoginStatus.COMPLETE,
                accessToken,
                refreshToken,
                jwtIssuerCapability.getAccessTokenExpirationSeconds(),
                /* identityToken */ null,
                /* tenantOptions */ null,
                identity.id(),
                p.displayName(),
                identity.email(),
                primaryRole,
                List.of(primaryRole),
                Collections.emptyList(),
                false, // passwordMustChange gated above
                /* mfaRequired */ false);
    }

    /**
     * Validates the login command and verifies the identity password.
     *
     * @param command login command
     * @return active identity whose password matched the supplied credential
     * @throws AuthFlowException when credentials are invalid or the identity is not active
     */
        private IdentityRecord authenticateIdentity(LoginCommand command) {
                return authenticateIdentity(command, false);
        }

        private IdentityRecord authenticateIdentity(LoginCommand command, boolean trackPlatformLoginFailures) {
        if (command == null || command.loginId() == null || command.loginId().isBlank()) {
            throw new AuthFlowException(
                    AuthFlowException.CODE_INVALID_CREDENTIALS, "loginId is required");
        }
        String loginId = command.loginId();

        IdentityRecord identity = identityTenantCapability.findIdentityByEmail(loginId)
                .orElse(null);
        if (identity == null) {
            log.warn("[AuthFlow] login failed — identity not found: {}", loginId);
            throw new AuthFlowException(
                    AuthFlowException.CODE_INVALID_CREDENTIALS, "Invalid credentials");
        }

        if (IDENTITY_STATUS_PENDING_SETUP.equals(identity.status())) {
            log.warn("[AuthFlow] login failed — identity pending setup: {}", loginId);
            throw new AuthFlowException(
                    AuthFlowException.CODE_PENDING_SETUP,
                    "Account setup is pending. Use the dedicated setup flow.");
        }

        if (IDENTITY_STATUS_LOCKED.equals(identity.status())) {
                        if (identityTenantCapability.unlockExpiredLoginLock(identity.id(), Instant.now())) {
                                identity = identityTenantCapability.findIdentityById(identity.id()).orElse(identity);
                        }
                }

                if (IDENTITY_STATUS_LOCKED.equals(identity.status())) {
            log.warn("[AuthFlow] login failed — identity locked: {}", loginId);
            throw new AuthFlowException(
                    AuthFlowException.CODE_ACCOUNT_LOCKED,
                    "Account is locked. Please contact the administrator.");
        }

        if (!IDENTITY_STATUS_ACTIVE.equals(identity.status())) {
            log.warn("[AuthFlow] login failed — identity not active: {} (status={})",
                    loginId, identity.status());
            throw new AuthFlowException(
                    AuthFlowException.CODE_ACCOUNT_DISABLED,
                    "Account is disabled. Please contact the administrator.");
        }

        if (command.password() == null || identity.passwordHash() == null
                || !passwordCapability.verify(command.password(), identity.passwordHash())) {
            log.warn("[AuthFlow] login failed — wrong password: {}", loginId);
                        if (trackPlatformLoginFailures) {
                                identityTenantCapability.recordFailedLogin(identity.id(),
                                                lockoutProperties.getMaxFailedAttempts(),
                                                lockoutProperties.getLockMinutes(),
                                                command.clientIp());
                        }
            throw new AuthFlowException(
                    AuthFlowException.CODE_INVALID_CREDENTIALS, "Invalid credentials");
        }

                if (trackPlatformLoginFailures) {
                        identityTenantCapability.recordSuccessfulLogin(identity.id(), command.clientIp());
                        identity = identityTenantCapability.findIdentityById(identity.id()).orElse(identity);
                }

        return identity;
    }

    private List<String> resolveRoles(String identityId, String tenantId) {
        if (rbacResolver == null) {
            return Collections.emptyList();
        }
        try {
            List<String> roles = rbacResolver.findRoles(identityId, tenantId);
            return roles != null ? roles : Collections.emptyList();
        } catch (RuntimeException e) {
            log.warn("[AuthFlow] RbacResolver.findRoles failed — falling back to empty list ({})",
                    e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<String> resolvePermissions(String identityId, String tenantId) {
        if (rbacResolver == null) {
            return Collections.emptyList();
        }
        try {
            List<String> perms = rbacResolver.findPermissions(identityId, tenantId);
            return perms != null ? perms : Collections.emptyList();
        } catch (RuntimeException e) {
            log.warn("[AuthFlow] RbacResolver.findPermissions failed — falling back to empty list ({})",
                    e.getMessage());
            return Collections.emptyList();
        }
    }

    private static List<TenantOption> buildTenantOptions(List<TenantMembershipRecord> memberships,
                                                         List<TenantPrincipalRecord> principalships) {
        List<TenantOption> options = new ArrayList<>(memberships.size() + principalships.size());
        for (TenantMembershipRecord m : memberships) {
            options.add(new TenantOption(
                    m.tenantId(),
                    m.tenantCode(),
                    m.tenantName(),
                    ROLE_TYPE_ACTOR,
                    m.memberType(),
                    /* lastAccessAt */ null));
        }
        for (TenantPrincipalRecord p : principalships) {
            options.add(new TenantOption(
                    p.tenantId(),
                    p.tenantCode(),
                    p.tenantName(),
                    ROLE_TYPE_SUBJECT,
                    p.principalType(),
                    p.lastAccessAt() != null ? p.lastAccessAt().toString() : null));
        }
        return options;
    }
}
