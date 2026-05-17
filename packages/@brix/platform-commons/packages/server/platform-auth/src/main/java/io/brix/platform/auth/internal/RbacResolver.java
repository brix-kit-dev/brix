/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.brix.platform.auth.internal;

import java.util.List;

/**
 * <h2>RBAC Resolver — Internal SPI</h2>
 *
 * <p>Bridges {@code platform-auth} to a downstream RBAC store (e.g. plugin-side
 * {@code RbacRepository}, or a future {@code RbacCapability}).</p>
 *
 * <h3>Why "Internal" not Layer 2A Capability</h3>
 * <p>RBAC data ownership is still being consolidated (currently sits in
 * {@code app-identity} plugin). Once RBAC tables migrate to
 * {@code platform-rbac}, this SPI is promoted to {@code RbacCapability} in
 * {@code runtime-sdk-api}. Until then, this internal SPI keeps
 * {@code AuthFlowCapabilityImpl} dependency-clean while still allowing real
 * RBAC data to flow into Actor tokens.</p>
 *
 * <h3>Default Behaviour</h3>
 * <p>If no bean implementing this SPI is present, {@code AuthFlowCapabilityImpl}
 * falls back to using the membership type as the sole role and an empty
 * permission list — equivalent to the pre-D2 fallback in {@code AuthServiceImpl}.</p>
 *
 * @since 3.2.0
 */
public interface RbacResolver {

    /**
     * Resolve role codes for the given identity in the given tenant.
     *
     * @param identityId identity id ({@code sys_identity.id} as string)
     * @param tenantId   tenant id ({@code sys_tenant.id} as string)
     * @return role codes (never {@code null}; empty list means no roles)
     */
    List<String> findRoles(String identityId, String tenantId);

    /**
     * Resolve permission codes for the given identity in the given tenant.
     *
     * @param identityId identity id
     * @param tenantId   tenant id
     * @return permission codes (never {@code null}; empty list means no permissions)
     */
    List<String> findPermissions(String identityId, String tenantId);
}
