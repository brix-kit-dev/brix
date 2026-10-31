/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.shipping.api;

/**
 * A refused shipment, as {@code sample-shipping/refused-shipment} describes it: the cancellation sample-shipping asked
 * sample-orders for.
 *
 * @param orderId the order
 * @param commandId the queued {@code sample-orders.cancel-order} command
 */
public record RefusedShipment(String orderId, String commandId) {
}
