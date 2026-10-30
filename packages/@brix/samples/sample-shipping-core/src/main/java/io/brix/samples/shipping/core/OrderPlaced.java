/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.shipping.core;

/**
 * {@code SampleOrderPlaced} as this subscriber reads it: the fields of the published schema
 * {@code brix://schemas/sample-orders/sample-order-placed/1.0.0}. A subscriber keeps its own type rather than
 * depending on the publisher's artifacts, so the two plugins share only the schema; the runtime decodes each delivery
 * into it.
 *
 * @param tenantId the tenant
 * @param orderId the order
 * @param sku what was ordered
 * @param quantity how many
 * @param version the order's version, to order events of several types for one order (Kafka design D31)
 * @author Brix Platform Authors
 * @since 3.2.0
 */
public record OrderPlaced(long tenantId, String orderId, String sku, int quantity, long version) {
}
