/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.auth.endpoint;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import io.runtime.sdk.capability.AuthFlowCapability.TenantOption;

/**
 * Tenant context option returned only during login context selection.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenantOptionDto(
        @JsonSerialize(using = ToStringSerializer.class)
        Long tenantId,
        String tenantCode,
        String tenantName,
        String roleType,
        String role,
        String lastAccessAt,
        String selectionTicket) {

    static TenantOptionDto from(TenantOption option) {
        return new TenantOptionDto(
            option.tenantId(),
            option.tenantCode(),
            option.tenantName(),
            option.roleType(),
            option.role(),
            option.lastAccessAt(),
            option.selectionTicket());
    }
}
