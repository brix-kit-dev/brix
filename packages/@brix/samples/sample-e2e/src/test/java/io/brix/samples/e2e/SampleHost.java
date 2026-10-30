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

    /** The tenant every request of the test comes from. */
    static final long TENANT = 42L;

    @Bean
    AuthCapability signedInMember() {
        return new SignedInMember();
    }

    /** Member 2001 of tenant 42, signed in with an access token. */
    private static final class SignedInMember implements AuthCapability {

        @Override
        public Principal getCurrentPrincipal() {
            return new Member();
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
            return String.valueOf(TENANT);
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
