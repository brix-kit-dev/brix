/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.internal;

import io.runtime.sdk.capability.TenantTarget;

/**
 * An operator's change to the quantity of an order.
 *
 * @param tenantId the tenant of the order, which the runtime binds the unit of work to
 * @param orderId the order
 * @param delta how many items to add, or to take away when negative
 */
public record AdjustOrderQuantity(Long tenantId, long orderId, int delta) implements TenantTarget {

    @Override
    public Long targetTenantId() {
        return tenantId;
    }
}
