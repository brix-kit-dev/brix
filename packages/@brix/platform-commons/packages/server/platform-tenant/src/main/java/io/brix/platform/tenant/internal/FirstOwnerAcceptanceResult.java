package io.brix.platform.tenant.internal;

/** Result of accepting a FIRST_OWNER invitation. */
public record FirstOwnerAcceptanceResult(Long tenantId, Long memberId, Long profileId, String tenantStatus) {
}
