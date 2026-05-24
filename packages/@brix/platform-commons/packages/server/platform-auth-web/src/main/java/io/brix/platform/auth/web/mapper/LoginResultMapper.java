/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License").
 */
package io.brix.platform.auth.web.mapper;

import java.util.Collections;
import java.util.List;

import io.brix.platform.auth.web.dto.LoginResponseDto;
import io.brix.platform.auth.web.dto.LoginResponseDto.UserView;
import io.brix.platform.auth.web.dto.TenantOptionDto;
import io.runtime.sdk.capability.AuthFlowCapability.LoginResult;
import io.runtime.sdk.capability.AuthFlowCapability.LoginStatus;
import io.runtime.sdk.capability.AuthFlowCapability.TenantOption;

/**
 * Maps the capability-layer {@link LoginResult} record to the HTTP-facing
 * {@link LoginResponseDto}.
 *
 * <p>This mapper is intentionally pure / static — no Spring dependency — so
 * controllers can call it without injection overhead.</p>
 *
 * @since 3.2.0
 */
public final class LoginResultMapper {

    private LoginResultMapper() { /* no instances */ }

    public static LoginResponseDto toDto(LoginResult result, String provider) {
        if (result == null) {
            return new LoginResponseDto(false, null, null, null, null, null,
                    null, null, false, false);
        }
        boolean selectTenant = result.status() == LoginStatus.SELECT_TENANT;

        UserView user = selectTenant ? null : new UserView(
                result.identityId(),
                /* username derived from email if not set elsewhere */ null,
                result.email(),
                result.displayName(),
                result.primaryRole(),
                nullSafe(result.roles()),
                nullSafe(result.permissions()),
                provider != null ? provider : "local"
        );

        List<TenantOptionDto> tenants = selectTenant && result.tenantOptions() != null
                ? result.tenantOptions().stream().map(LoginResultMapper::toTenantDto).toList()
                : null;

        return new LoginResponseDto(
                /* success */ true,
                /* step */ result.status() == null ? null : result.status().name(),
                /* accessToken */ result.accessToken(),
                /* refreshToken */ result.refreshToken(),
                /* expiresIn */ result.expiresIn(),
                /* user */ user,
                /* identityToken */ result.identityToken(),
                /* tenants */ tenants,
                /* mustChangePassword */ result.mustChangePassword(),
                /* mfaRequired */ result.mfaRequired()
        );
    }

    private static TenantOptionDto toTenantDto(TenantOption opt) {
        return new TenantOptionDto(
                opt.tenantId(),
                opt.tenantCode(),
                opt.tenantName(),
                opt.roleType(),
                opt.role(),
                opt.lastAccessAt());
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : Collections.emptyList();
    }
}
