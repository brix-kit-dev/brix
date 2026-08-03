/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.tenant.endpoint;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import io.brix.platform.tenant.internal.FirstOwnerAcceptanceResult;

/** Runtime-safe FIRST_OWNER acceptance result. */
public record FirstOwnerAcceptanceDto(
        @JsonSerialize(using = ToStringSerializer.class)
        Long tenantId,
        @JsonSerialize(using = ToStringSerializer.class)
        Long memberId,
        @JsonSerialize(using = ToStringSerializer.class)
        Long profileId,
        String tenantStatus) {

    static FirstOwnerAcceptanceDto from(FirstOwnerAcceptanceResult result) {
        return new FirstOwnerAcceptanceDto(
            result.tenantId(),
            result.memberId(),
            result.profileId(),
            result.tenantStatus());
    }
}
