/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import java.util.List;

import io.brix.platform.admin.dto.PlatformLoginResponse;
import io.runtime.sdk.capability.AuthFlowCapability.LoginResult;
import io.runtime.sdk.capability.AuthFlowCapability.LoginStatus;

final class PlatformAuthLoginResponseMapper {

    private PlatformAuthLoginResponseMapper() {
    }

    static PlatformLoginResponse toResponse(LoginResult result) {
        if (result == null || result.status() == null) {
            throw new IllegalArgumentException("login result is required");
        }
        return new PlatformLoginResponse(
                result.status().name(),
                result.accessToken(),
                result.refreshToken(),
                result.expiresIn() == null ? 0L : result.expiresIn(),
                result.primaryRole(),
                nullSafe(result.permissions()),
                result.identityId(),
                result.email(),
                result.displayName(),
                result.mustChangePassword(),
                result.status() == LoginStatus.MFA_REQUIRED ? result.identityToken() : null);
    }

    private static <T> List<T> nullSafe(List<T> source) {
        return source == null ? List.of() : List.copyOf(source);
    }
}
