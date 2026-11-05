/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.e2e;

import java.security.Principal;
import java.util.Set;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import io.runtime.sdk.capability.AuthCapability;
import io.runtime.sdk.capability.AuthenticatedPrincipal;
import io.runtime.sdk.capability.DataScope;

/**
 * A host of the sample plugins: auto-configurations and properties only. Its one bean stands for the identity
 * provider a real host selects: every request comes from member 2001 of tenant 42 with an access token.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
class SampleHost {

    /** The tenant every member's request comes from. */
    static final long TENANT = 42L;

    /** A request that names this header comes from a platform operator, not from the member. */
    static final String OPERATOR_HEADER = "X-Sample-Operator";

    @Bean
    AuthCapability signedInMember() {
        return new SignedInMember();
    }

    private static boolean operator() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
            && attributes.getRequest().getHeader(OPERATOR_HEADER) != null;
    }

    /** Member 2001 of tenant 42, signed in with an access token, or operator 9001 of the platform. */
    private static final class SignedInMember implements AuthCapability {

        @Override
        public Principal getCurrentPrincipal() {
            return operator() ? new Operator() : new Member();
        }

        @Override
        public boolean hasPermission(String permission) {
            return false;
        }

        @Override
        public boolean hasRole(String role) {
            return false;
        }

        @Override
        public Set<DataScope> getAuthorizedScopes() {
            return Set.of();
        }

        @Override
        public String getTenantId() {
            return operator() ? null : String.valueOf(TENANT);
        }
    }

    /** A platform operator: a platform token, in no tenant. */
    private static final class Operator implements AuthenticatedPrincipal {

        @Override
        public String getName() {
            return "9001";
        }

        @Override
        public String getUserId() {
            return "9001";
        }

        @Override
        public String getTenantId() {
            return null;
        }

        @Override
        public String getEmail() {
            return null;
        }

        @Override
        public String getTokenRole() {
            return "platform-admin";
        }

        @Override
        public String getTokenType() {
            return "access";
        }

        @Override
        public String getTokenScope() {
            return "PLATFORM";
        }

        @Override
        public Set<String> getPermissions() {
            return Set.of("sample-operations:order:adjust");
        }
    }

    private static final class Member implements AuthenticatedPrincipal {

        @Override
        public String getName() {
            return "2001";
        }

        @Override
        public String getUserId() {
            return "2001";
        }

        @Override
        public String getTenantId() {
            return String.valueOf(TENANT);
        }

        @Override
        public String getEmail() {
            return null;
        }

        @Override
        public String getTokenRole() {
            return "actor";
        }

        @Override
        public String getTokenType() {
            return "access";
        }

        @Override
        public String getTokenScope() {
            return "actor";
        }

        @Override
        public String getTenantRoleType() {
            return "MEMBER";
        }
    }
}
