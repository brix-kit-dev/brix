package io.brix.platform.admin.dto;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

public record PlatformSetupValidateResponse(
        boolean valid,

        @JsonSerialize(using = ToStringSerializer.class)
        Long identityId,
        String email,
        String username,
        String purpose,
        OffsetDateTime expiresAt
) {}
