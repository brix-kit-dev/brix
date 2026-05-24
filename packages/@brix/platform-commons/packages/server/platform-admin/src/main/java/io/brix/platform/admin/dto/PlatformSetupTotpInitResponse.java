package io.brix.platform.admin.dto;

public record PlatformSetupTotpInitResponse(
        String challengeId,
        String otpauthUri
) {}