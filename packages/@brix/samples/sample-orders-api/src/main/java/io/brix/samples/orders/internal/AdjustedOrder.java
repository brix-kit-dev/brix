/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.internal;

/**
 * An order after an operator's change.
 *
 * @param orderId the order
 * @param quantity how many items it has now
 * @param version its version now
 */
public record AdjustedOrder(String orderId, int quantity, long version) {
}
