/*
 * Copyright 2026 Runtime SDK Authors
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
package io.runtime.orchestrator.endpoint;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Dispatcher security policy of one manifest-declared endpoint.
 *
 * <p>The policy is built only from the {@code authentication},
 * {@code authorization} and {@code tenantContext} sections of an endpoint
 * declaration. {@code authorization.permissions} is the only source of
 * permission IDs checked against the verified principal. Named references such
 * as {@code authorization.policyRef}/{@code resourcePolicyRef}, and the
 * {@code timeout} or {@code idempotency} policy references of the same
 * declaration, are never interpreted as permissions.</p>
 *
 * <p>Step-up re-verification ({@code acr}/{@code max_age}, BRX-RS-SECURITY-015)
 * is not enforced by this Dispatcher yet, so a declaration that asks for it is
 * rejected instead of being published unchecked.</p>
 *
 * @param authentication declared authentication mode
 * @param tokenPolicy token class accepted when authentication is required
 * @param permissions permission IDs the verified principal must hold
 * @param decision combination rule for {@code permissions}
 * @param authorizationPolicyRef named, non-permission authorization policy reference
 * @param tenantContext declared tenant context mode
 * @author Runtime SDK Team
 * @since 3.2.0
 */
public record EndpointAccessPolicy(
        AuthenticationMode authentication,
        TokenPolicy tokenPolicy,
        Set<String> permissions,
        PermissionDecision decision,
        String authorizationPolicyRef,
        TenantContextMode tenantContext) {

    /**
     * Validates the policy and defensively copies the permission set.
     */
    public EndpointAccessPolicy {
        authentication = Objects.requireNonNull(authentication, "authentication must not be null");
        tokenPolicy = Objects.requireNonNull(tokenPolicy, "tokenPolicy must not be null");
        Objects.requireNonNull(permissions, "permissions must not be null");
        for (String permission : permissions) {
            if (permission == null || permission.isBlank()) {
                throw new IllegalArgumentException("authorization.permissions must not contain blank values");
            }
        }
        permissions = Set.copyOf(permissions);
        decision = Objects.requireNonNull(decision, "decision must not be null");
        authorizationPolicyRef = authorizationPolicyRef == null ? "" : authorizationPolicyRef.trim();
        tenantContext = Objects.requireNonNull(tenantContext, "tenantContext must not be null");
        if (authentication != AuthenticationMode.REQUIRED && tokenPolicy != TokenPolicy.ANY_VERIFIED) {
            throw new IllegalArgumentException("authentication.policyRef '" + tokenPolicy.value()
                + "' requires authentication.mode=required");
        }
        if (authentication != AuthenticationMode.REQUIRED && !permissions.isEmpty()) {
            throw new IllegalArgumentException("authorization.permissions require authentication.mode=required");
        }
        if (authentication != AuthenticationMode.REQUIRED && tenantContext == TenantContextMode.REQUIRED) {
            throw new IllegalArgumentException("tenantContext.mode=required requires authentication.mode=required");
        }
        if (tenantContext == TenantContextMode.REQUIRED && tokenPolicy.tenantTokenRole().isEmpty()) {
            throw new IllegalArgumentException("tenantContext.mode=required requires authentication.policyRef "
                + "'tenant-actor' or 'tenant-subject'");
        }
        if (permissions.isEmpty() && authorizationPolicyRef.isEmpty()) {
            throw new IllegalArgumentException(
                "authorization must declare permissions or a policyRef/resourcePolicyRef");
        }
    }

    /**
     * Builds a policy from the raw manifest or descriptor sections of an
     * endpoint declaration.
     *
     * @param authentication {@code authentication} section
     * @param authorization {@code authorization} section
     * @param tenantContext {@code tenantContext} section
     * @return validated policy
     * @throws IllegalArgumentException when a section is missing or declares an
     *         unsupported value
     */
    public static EndpointAccessPolicy fromDeclaration(
            Map<String, ?> authentication,
            Map<String, ?> authorization,
            Map<String, ?> tenantContext) {
        rejectStepUp(authentication, "authentication");
        rejectStepUp(authorization, "authorization");
        String resourcePolicyRef = text(authorization, "authorization", "resourcePolicyRef");
        return new EndpointAccessPolicy(
            AuthenticationMode.fromValue(text(authentication, "authentication", "mode")),
            TokenPolicy.fromValue(text(authentication, "authentication", "policyRef")),
            permissions(authorization),
            PermissionDecision.fromValue(text(authorization, "authorization", "decision")),
            resourcePolicyRef.isEmpty()
                ? text(authorization, "authorization", "policyRef")
                : resourcePolicyRef,
            TenantContextMode.fromValue(text(tenantContext, "tenantContext", "mode")));
    }

    /**
     * Returns whether the Dispatcher must see a Runtime-verified principal
     * before the handler runs.
     *
     * @return {@code true} for {@code authentication.mode=required}
     */
    public boolean requiresVerifiedPrincipal() {
        return authentication == AuthenticationMode.REQUIRED;
    }

    private static void rejectStepUp(Map<String, ?> section, String sectionName) {
        if (section == null) {
            return;
        }
        for (String field : List.of("acr", "max_age", "maxAge")) {
            if (section.containsKey(field)) {
                throw new IllegalArgumentException(sectionName + "." + field
                    + " declares step-up re-verification (BRX-RS-SECURITY-015), "
                    + "which this Dispatcher does not enforce yet");
            }
        }
    }

    private static String text(Map<String, ?> section, String sectionName, String field) {
        if (section == null) {
            return "";
        }
        Object value = section.get(field);
        if (value == null) {
            return "";
        }
        if (!(value instanceof CharSequence)) {
            throw new IllegalArgumentException(sectionName + "." + field + " must be a string");
        }
        return value.toString().trim();
    }

    private static Set<String> permissions(Map<String, ?> authorization) {
        Object value = authorization == null ? null : authorization.get("permissions");
        if (value == null) {
            return Set.of();
        }
        if (!(value instanceof Collection<?> values)) {
            throw new IllegalArgumentException("authorization.permissions must be a list of permission IDs");
        }
        Set<String> result = new LinkedHashSet<>();
        for (Object permission : values) {
            if (!(permission instanceof CharSequence) || permission.toString().isBlank()) {
                throw new IllegalArgumentException("authorization.permissions must contain permission IDs");
            }
            result.add(permission.toString().trim());
        }
        return result;
    }

    /**
     * Declared {@code authentication.mode}.
     */
    public enum AuthenticationMode {
        /** A Runtime-verified principal is required before the handler runs. */
        REQUIRED("required"),
        /** Approved anonymous entry; no Runtime principal is required. */
        ANONYMOUS("anonymous"),
        /** Setup-token entry whose one-time token is verified by its Data Owner. */
        SETUP_TOKEN("setup-token"),
        /** Bootstrap entry whose setup session is verified by its Data Owner. */
        BOOTSTRAP_SETUP("bootstrap-setup");

        private final String value;

        AuthenticationMode(String value) {
            this.value = value;
        }

        /**
         * Returns the manifest value.
         *
         * @return manifest value
         */
        public String value() {
            return value;
        }

        static AuthenticationMode fromValue(String value) {
            if (value.isEmpty()) {
                throw new IllegalArgumentException("authentication.mode is required");
            }
            for (AuthenticationMode mode : values()) {
                if (mode.value.equals(value)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Unsupported authentication.mode '" + value + "'");
        }
    }

    /**
     * Token class accepted by an endpoint, declared by
     * {@code authentication.policyRef}.
     */
    public enum TokenPolicy {
        /** No {@code policyRef}: any Runtime-verified principal is accepted. */
        ANY_VERIFIED(""),
        /** Formal platform access token: role {@code platform-admin}, type {@code access}, scope {@code PLATFORM}. */
        PLATFORM_TOKEN("platform-token"),
        /** Tenant user access token: role and scope {@code actor} or {@code subject}, type {@code access}. */
        AUTHENTICATED_USER("authenticated-user"),
        /** Tenant management API: only actor access tokens (role and scope {@code actor}). */
        TENANT_ACTOR("tenant-actor"),
        /** Tenant end-user API: only subject access tokens (role and scope {@code subject}). */
        TENANT_SUBJECT("tenant-subject");

        private static final String ACCESS_TOKEN = "access";

        private final String value;

        TokenPolicy(String value) {
            this.value = value;
        }

        /**
         * Returns the manifest value.
         *
         * @return manifest value; empty for {@link #ANY_VERIFIED}
         */
        public String value() {
            return value;
        }

        /**
         * Returns whether a verified token is acceptable for this policy.
         *
         * @param tokenRole verified token role, or {@code null}
         * @param tokenType verified token type, or {@code null}
         * @param tokenScope verified token scope, or {@code null}
         * @return {@code true} when the token class matches
         */
        public boolean accepts(String tokenRole, String tokenType, String tokenScope) {
            return switch (this) {
                case ANY_VERIFIED -> true;
                case PLATFORM_TOKEN -> "platform-admin".equals(tokenRole)
                    && ACCESS_TOKEN.equals(tokenType)
                    && "PLATFORM".equals(tokenScope);
                case AUTHENTICATED_USER -> ACCESS_TOKEN.equals(tokenType)
                    && ("actor".equals(tokenRole) || "subject".equals(tokenRole))
                    && tokenRole.equals(tokenScope);
                case TENANT_ACTOR, TENANT_SUBJECT -> ACCESS_TOKEN.equals(tokenType)
                    && tenantTokenRole().orElseThrow().equals(tokenRole)
                    && tokenRole.equals(tokenScope);
            };
        }

        /**
         * Returns the only tenant token role this policy admits.
         *
         * @return {@code actor} for {@link #TENANT_ACTOR}, {@code subject} for
         *         {@link #TENANT_SUBJECT}, empty for every other policy
         */
        public Optional<String> tenantTokenRole() {
            return switch (this) {
                case TENANT_ACTOR -> Optional.of("actor");
                case TENANT_SUBJECT -> Optional.of("subject");
                default -> Optional.empty();
            };
        }

        static TokenPolicy fromValue(String value) {
            for (TokenPolicy policy : values()) {
                if (policy.value.equals(value)) {
                    return policy;
                }
            }
            throw new IllegalArgumentException("Unsupported authentication.policyRef '" + value + "'");
        }
    }

    /**
     * Declared {@code authorization.decision}.
     */
    public enum PermissionDecision {
        /** Every declared permission is required. */
        ALL("all"),
        /** At least one declared permission is required. */
        ANY("any");

        private final String value;

        PermissionDecision(String value) {
            this.value = value;
        }

        /**
         * Returns whether the granted permissions satisfy the declared ones.
         *
         * @param required declared permission IDs
         * @param granted permission IDs of the verified principal
         * @return {@code true} when the decision is satisfied
         */
        public boolean satisfiedBy(Set<String> required, Set<String> granted) {
            if (required.isEmpty()) {
                return true;
            }
            return this == ALL
                ? granted.containsAll(required)
                : required.stream().anyMatch(granted::contains);
        }

        static PermissionDecision fromValue(String value) {
            if (value.isEmpty()) {
                return ALL;
            }
            for (PermissionDecision decision : values()) {
                if (decision.value.equals(value)) {
                    return decision;
                }
            }
            throw new IllegalArgumentException("Unsupported authorization.decision '" + value + "'");
        }
    }

    /**
     * Declared {@code tenantContext.mode}.
     */
    public enum TenantContextMode {
        /** The verified principal must carry a tenant context. */
        REQUIRED("required"),
        /** A tenant context may or may not be present. */
        OPTIONAL("optional"),
        /** The invocation must not carry a tenant context. */
        FORBIDDEN("forbidden");

        private final String value;

        TenantContextMode(String value) {
            this.value = value;
        }

        static TenantContextMode fromValue(String value) {
            if (value.isEmpty()) {
                throw new IllegalArgumentException("tenantContext.mode is required");
            }
            for (TenantContextMode mode : values()) {
                if (mode.value.equals(value)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Unsupported tenantContext.mode '" + value + "'");
        }
    }
}
