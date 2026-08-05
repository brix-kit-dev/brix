/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.auth.endpoint;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import io.runtime.sdk.capability.AuthFlowCapability.LoginResult;
import io.runtime.sdk.capability.AuthFlowCapability.TenantOption;

/**
 * Public login response DTO for Runtime Shell auth endpoints.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponseDto(
        boolean success,
        String status,
        String accessToken,
        String refreshToken,
        long expiresIn,
        String identityToken,
        List<TenantOptionDto> tenantOptions,
        @JsonSerialize(using = ToStringSerializer.class)
        Long identityId,
        String displayName,
        String email,
        String primaryRole,
        List<String> roles,
        List<String> permissions,
        boolean mustChangePassword,
        boolean mfaRequired) {

    public static LoginResponseDto from(LoginResult result) {
        if (result == null || result.status() == null) {
            throw new IllegalArgumentException("login result is required");
        }
        return new LoginResponseDto(
            true,
            result.status().name(),
            result.accessToken(),
            result.refreshToken(),
            result.expiresIn() == null ? 0L : result.expiresIn(),
            result.identityToken(),
            tenantOptions(result.tenantOptions()),
            result.identityId(),
            result.displayName(),
            result.email(),
            result.primaryRole(),
            nullSafe(result.roles()),
            nullSafe(result.permissions()),
            result.mustChangePassword(),
            result.mfaRequired());
    }

    private static List<TenantOptionDto> tenantOptions(List<TenantOption> options) {
        return nullSafe(options).stream()
            .map(TenantOptionDto::from)
            .toList();
    }

    private static <T> List<T> nullSafe(List<T> source) {
        return source == null ? List.of() : List.copyOf(source);
    }
}
