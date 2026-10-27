/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.runtime.sdk.capability;

import io.runtime.sdk.annotation.Since;

/**
 * Names the single tenant a platform operation acts on.
 *
 * <p>An internal contract request that touches tenant data implements this type. The runtime
 * binds the Data Owner's unit of work to that one tenant before the provider runs, so the
 * persistence adapter keeps every statement and write inside it (multi-tenant §6.4, platform
 * targeted operations; root BRX-RS-DATA-040).</p>
 *
 * @since 3.3.0
 */
@Since("3.3.0")
public interface TenantTarget {

    /**
     * Returns the tenant the request acts on.
     *
     * @return the target tenant id, never {@code null}
     */
    Long targetTenantId();
}
