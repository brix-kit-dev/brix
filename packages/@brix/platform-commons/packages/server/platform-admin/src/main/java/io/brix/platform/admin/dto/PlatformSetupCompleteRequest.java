package io.brix.platform.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PlatformSetupCompleteRequest(
        @JsonProperty("token")
        @NotBlank(message = "token must not be blank")
        String setupToken,

        @NotBlank(message = "challengeId must not be blank")
        String challengeId,

        @NotBlank(message = "password must not be blank")
        @Size(min = 12, max = 128, message = "password must be between 12 and 128 characters")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                message = "password must contain upper, lower, digit and symbol")
        String password,

        @NotBlank(message = "totpCode must not be blank")
        @Pattern(regexp = "\\d{6}", message = "totpCode must be 6 digits")
        String totpCode
) {}
