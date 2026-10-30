/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.api;

/**
 * The payload of {@code SampleOrderActivity}, as {@code brix://schemas/sample-orders/sample-order-activity/1.0.0}
 * describes it.
 *
 * @param tenantId the tenant
 * @param orderId the order
 * @param activity what happened, such as {@code placed}
 * @author Brix Platform Authors
 * @since 3.2.0
 */
public record SampleOrderActivityPayload(long tenantId, String orderId, String activity) {
}
