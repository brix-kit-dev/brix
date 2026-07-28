package io.brix.platform.tenant.internal;

/** Stable tenant view returned by the TenantAdministration internal contract. */
public record TenantAdministrationTenant(Long id, String code, String name, String status) {
}
