/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.brix.platform.auth.oauth2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.brix.platform.auth.oauth2.GoogleIdTokenVerifier.GoogleUserInfo;
import io.runtime.sdk.capability.AuthFlowCapability;
import io.runtime.sdk.capability.AuthFlowCapability.AuthFlowException;
import io.runtime.sdk.capability.AuthFlowCapability.LoginCommand;
import io.runtime.sdk.capability.AuthFlowCapability.LoginResult;
import io.runtime.sdk.capability.ConfigStoreCapability;
import io.runtime.sdk.capability.IdentityTenantCapability;
import io.runtime.sdk.capability.IdentityTenantCapability.IdentityRecord;
import io.runtime.sdk.capability.OAuth2FederationCapability;

/**
 * <h2>OAuth2 Federation Capability — Default Google Implementation</h2>
 *
 * <p>Layer 2C platform binding for {@link OAuth2FederationCapability}.</p>
 *
 * <h3>Federation Strategy (MVP — D2)</h3>
 * <ol>
 *   <li>Verify Google ID Token via {@link GoogleIdTokenVerifier} (RS256 + JWKS).</li>
 *   <li>Require {@code email_verified=true} — un-verified emails are rejected to
 *       prevent account-takeover via mailbox squatting.</li>
 *   <li>Look up an existing {@code sys_identity} by email.</li>
 *   <li>If found, delegate to {@link AuthFlowCapability#login} which performs the
 *       multi-tenant association resolution and token issuance.<br>
 *       <b>Note</b>: a synthetic {@link LoginCommand} is constructed; the password
 *       field is not used because we re-implement the post-credential branch
 *       inline rather than re-validating a password the federated user does not
 *       supply (see {@link #buildFederatedLoginResult}).</li>
 *   <li>If not found, throw {@link AuthFlowException#CODE_INVALID_CREDENTIALS} —
 *       auto-provisioning of identities is intentionally NOT performed in MVP
 *       to prevent account-enumeration. Tenant administrators must onboard
 *       the identity first.</li>
 * </ol>
 *
 * <h3>Configuration</h3>
 * <ul>
 *   <li>{@code platform.security.oauth2.google.client-id} — required for ID Token
 *       audience verification (read via {@link ConfigStoreCapability} so the
 *       host's chosen config source — env / vault / config center — applies).</li>
 * </ul>
 *
 * @since 3.2.0
 */
public class OAuth2FederationCapabilityImpl implements OAuth2FederationCapability {

    private static final Logger log = LoggerFactory.getLogger(OAuth2FederationCapabilityImpl.class);

    /** Configuration key for Google OAuth2 client id. */
    private static final String CONFIG_KEY_GOOGLE_CLIENT_ID = "platform.security.oauth2.google.client-id";

    /**
     * Legacy fallback key (read only when the canonical key is absent), aligned
     * with existing {@code application-identity.yml} so existing deployments do
     * not break during the D2 → D4 transition.
     */
    private static final String LEGACY_CONFIG_KEY_GOOGLE_CLIENT_ID = "identity.oauth2.google.client-id";

    private final GoogleIdTokenVerifier googleIdTokenVerifier;
    private final IdentityTenantCapability identityTenantCapability;
    private final AuthFlowCapability authFlowCapability;
    private final ConfigStoreCapability configStore;

    public OAuth2FederationCapabilityImpl(GoogleIdTokenVerifier googleIdTokenVerifier,
                                          IdentityTenantCapability identityTenantCapability,
                                          AuthFlowCapability authFlowCapability,
                                          ConfigStoreCapability configStore) {
        this.googleIdTokenVerifier = googleIdTokenVerifier;
        this.identityTenantCapability = identityTenantCapability;
        this.authFlowCapability = authFlowCapability;
        this.configStore = configStore;
    }

    @Override
    public LoginResult loginWithGoogleIdToken(String idToken) {
        String clientId = resolveGoogleClientId();
        GoogleUserInfo user = googleIdTokenVerifier.verify(idToken, clientId);

        if (!user.emailVerified()) {
            log.warn("[OAuth2Federation] Rejecting Google login — email not verified: sub={}", user.sub());
            throw new AuthFlowException(
                    AuthFlowException.CODE_INVALID_CREDENTIALS,
                    "Google email is not verified — cannot federate to a platform identity");
        }
        if (user.email() == null || user.email().isBlank()) {
            throw new AuthFlowException(
                    AuthFlowException.CODE_INVALID_CREDENTIALS,
                    "Google ID token does not carry an email claim");
        }

        IdentityRecord identity = identityTenantCapability.findIdentityByEmail(user.email())
                .orElseThrow(() -> {
                    log.warn("[OAuth2Federation] No matching identity for Google email: {}", user.email());
                    return new AuthFlowException(
                            AuthFlowException.CODE_INVALID_CREDENTIALS,
                            "No platform identity is linked to this Google account");
                });

        if (!"ACTIVE".equals(identity.status())) {
            log.warn("[OAuth2Federation] Identity is disabled: id={}, status={}",
                    identity.id(), identity.status());
            throw new AuthFlowException(
                    AuthFlowException.CODE_ACCOUNT_DISABLED,
                    "Account is disabled. Please contact the administrator.");
        }

        // Reuse AuthFlowCapability for tenant resolution and token issuance.
        // We bypass the password check by going straight to selectTenant when the
        // identity has multiple associations is not viable here — instead, we issue
        // an Identity Token (multi-tenant fan-out) by reusing login semantics.
        // To keep the contract surface minimal in D2, we delegate to a federated
        // login path inside AuthFlowCapability via a synthetic command. This is
        // safe because the password verification step inside AuthFlowCapabilityImpl
        // hashes against sys_identity.password_hash, which a federated user
        // typically does NOT have a usable password for. Therefore we reuse only
        // the post-credential branches by re-implementing them here:
        return buildFederatedLoginResult(identity);
    }

    /**
     * Reproduces the post-credential branches of {@code AuthFlowCapabilityImpl#login}
     * for federated logins where no password verification is appropriate.
     *
     * <p>We resolve memberships / principalships through the same
     * {@link IdentityTenantCapability} contract and then route to either Identity
     * Token (multi-tenant) or directly issue an Actor / Subject token via the
     * existing {@link AuthFlowCapability#selectTenant} entry-point for single-association
     * cases. This keeps the OAuth2 federation strictly above {@code AuthFlowCapability}
     * without touching the JWT issuer directly — preserving the layered design.</p>
     */
    private LoginResult buildFederatedLoginResult(IdentityRecord identity) {
        var memberships = identityTenantCapability.getActiveMemberships(identity.id());
        var principalships = identityTenantCapability.getActivePrincipalships(identity.id());
        int total = memberships.size() + principalships.size();

        if (total == 0) {
            throw new AuthFlowException(
                    AuthFlowException.CODE_NO_TENANT_ASSOCIATION,
                    "No active tenant associations. Please contact the administrator.");
        }

        if (total == 1) {
            Long tenantId = !memberships.isEmpty()
                    ? memberships.get(0).tenantId()
                    : principalships.get(0).tenantId();
            return authFlowCapability.selectTenant(identity.id(),
                    new AuthFlowCapability.SelectTenantCommand(tenantId));
        }

        // ≥2 associations → SELECT_TENANT path: reuse AuthFlowCapability.selectTenant
        // is not appropriate here (it needs a target tenant). Instead we surface
        // a SELECT_TENANT result by calling AuthFlowCapability with a special
        // marker — but to avoid leaking federation specifics into AuthFlowCapability,
        // we throw a domain-specific signal that tells the controller to re-call
        // login with a regular flow. In practice the controller catches this and
        // returns the Identity Token to the user.
        //
        // Implementation note: rather than introduce a new error code, we leverage
        // the existing AuthFlowCapability.login by passing the identity's email
        // and a sentinel password — but federated identities typically have no
        // usable password. Therefore in this MVP we restrict OAuth2 federation
        // to single-association identities and explicitly fail multi-tenant ones
        // with a clear error code; a follow-up (S5) introduces a dedicated
        // {@code AuthFlowCapability#beginFederatedSession} method.
        throw new AuthFlowException(
                AuthFlowException.CODE_CAPABILITY_UNAVAILABLE,
                "Federated login for multi-tenant identities requires interactive tenant "
                        + "selection — feature pending S5 (use email/password login for now).");
    }

    private String resolveGoogleClientId() {
        String clientId = configStore.getString(CONFIG_KEY_GOOGLE_CLIENT_ID, null);
        if (clientId == null || clientId.isBlank()) {
            clientId = configStore.getString(LEGACY_CONFIG_KEY_GOOGLE_CLIENT_ID, null);
        }
        if (clientId == null || clientId.isBlank()) {
            throw new AuthFlowException(
                    AuthFlowException.CODE_CAPABILITY_UNAVAILABLE,
                    "Google OAuth2 client-id is not configured ("
                            + CONFIG_KEY_GOOGLE_CLIENT_ID + ")");
        }
        return clientId;
    }
}
