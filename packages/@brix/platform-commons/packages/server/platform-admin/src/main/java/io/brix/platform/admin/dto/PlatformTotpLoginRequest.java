package io.brix.platform.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PlatformTotpLoginRequest(
        @NotBlank(message = "mfaChallengeToken must not be blank")
        String mfaChallengeToken,

        @NotBlank(message = "totpCode must not be blank")
        @Pattern(regexp = "\\d{6}", message = "totpCode must be 6 digits")
        String totpCode
) {}