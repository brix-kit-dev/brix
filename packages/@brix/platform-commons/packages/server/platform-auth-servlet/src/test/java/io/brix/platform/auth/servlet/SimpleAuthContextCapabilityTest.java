/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.auth.servlet;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.brix.platform.auth.context.AuthenticatedUser;
import io.brix.platform.auth.context.SecurityContextHolder;
import io.brix.platform.auth.enums.TokenRole;
import io.brix.platform.auth.enums.TokenType;
import io.runtime.sdk.capability.AuthenticatedPrincipal;

class SimpleAuthContextCapabilityTest {

    private final SecurityContextHolder holder = new SecurityContextHolder();
    private final SimpleAuthContextCapability capability = new SimpleAuthContextCapability(holder);

    @AfterEach
    void clear() {
        holder.clear();
    }

    @Test
    void principalExposesVerifiedTokenScopeAndPermissions() {
        AuthenticatedUser user = new AuthenticatedUser();
        user.setUserId("1001");
        user.setTokenRole(TokenRole.PLATFORM_ADMIN);
        user.setTokenType(TokenType.ACCESS);
        user.setScope("PLATFORM");
        user.setPermissions(Arrays.asList("platform:tenant:read", null, " ", "platform:tenant:create"));
        holder.setCurrentUser(user);

        AuthenticatedPrincipal principal = (AuthenticatedPrincipal) capability.getCurrentPrincipal();

        assertThat(principal.getTokenRole()).isEqualTo("platform-admin");
        assertThat(principal.getTokenType()).isEqualTo("access");
        assertThat(principal.getTokenScope()).isEqualTo("PLATFORM");
        assertThat(principal.getPermissions())
                .containsExactlyInAnyOrder("platform:tenant:read", "platform:tenant:create");
    }

    @Test
    void principalWithoutPermissionClaimGrantsNothing() {
        AuthenticatedUser user = new AuthenticatedUser();
        user.setUserId("1001");
        user.setPermissions(List.of());
        holder.setCurrentUser(user);

        AuthenticatedPrincipal principal = (AuthenticatedPrincipal) capability.getCurrentPrincipal();

        assertThat(principal.getPermissions()).isEmpty();
        assertThat(principal.getTokenScope()).isNull();
    }
}
