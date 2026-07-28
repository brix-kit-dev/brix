package io.brix.platform.tenant.internal;

/** Command to create a pending tenant from the platform operational surface. */
public record CreatePendingTenantCommand(String code, String name, Long platformAdminIdentityId) {
    public CreatePendingTenantCommand {
        requireText(code, "code");
        requireText(name, "name");
        requirePositive(platformAdminIdentityId, "platformAdminIdentityId");
    }

    static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    static void requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
