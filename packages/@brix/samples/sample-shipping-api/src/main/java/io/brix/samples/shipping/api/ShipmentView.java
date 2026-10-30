/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.shipping.api;

/**
 * A shipment as sample-shipping shows it, the answer of {@code GET /api/sample-shipping/shipments/{orderId}}
 * ({@code brix://schemas/sample-shipping/shipment/1.0.0}).
 *
 * @param orderId the order it ships
 * @param sku what it ships
 * @param quantity how many
 * @param orderVersion the version of the order it reflects
 * @author Brix Platform Authors
 * @since 3.2.0
 */
public record ShipmentView(String orderId, String sku, int quantity, long orderVersion) {
}
