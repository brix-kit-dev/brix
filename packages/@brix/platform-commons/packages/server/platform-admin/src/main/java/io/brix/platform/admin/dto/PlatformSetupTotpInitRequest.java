package io.brix.platform.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record PlatformSetupTotpInitRequest(
        @JsonProperty("token")
        @NotBlank(message = "token must not be blank")
        String setupToken
) {}
