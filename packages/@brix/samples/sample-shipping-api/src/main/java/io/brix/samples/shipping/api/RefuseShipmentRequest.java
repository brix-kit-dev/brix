/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.shipping.api;

/**
 * The request of {@code POST /api/sample-shipping/shipments/{orderId}/refuse}, as
 * {@code sample-shipping/refuse-shipment-request} 1.1.0 describes it: its path variable and an optional reason.
 *
 * @param orderId the order whose shipment is refused
 * @param reason {@code REFUSED} or {@code DAMAGED}; {@code null} reads as {@code REFUSED}
 */
public record RefuseShipmentRequest(String orderId, String reason) {

    /**
     * Creates the request of 1.0.0, without a reason.
     *
     * @param orderId the order whose shipment is refused
     */
    public RefuseShipmentRequest(String orderId) {
        this(orderId, null);
    }
}
