package io.brix.platform.admin.dto;

import java.time.OffsetDateTime;

import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

public record PlatformSetupValidateResponse(
        boolean valid,

        @JsonSerialize(using = ToStringSerializer.class)
        Long identityId,
        String email,
        String username,
        String purpose,
        OffsetDateTime expiresAt
) {}
