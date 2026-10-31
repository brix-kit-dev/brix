/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.shipping.api;

/**
 * The request of {@code POST /api/sample-shipping/shipments/{orderId}/refuse}, as
 * {@code sample-shipping/refuse-shipment-request} describes it: its path variable.
 *
 * @param orderId the order whose shipment is refused
 */
public record RefuseShipmentRequest(String orderId) {
}
