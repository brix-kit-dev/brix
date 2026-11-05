/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.internal;

/**
 * What the platform's operators may do to an order: sample-orders' internal contract (root BRX-RS-HOST-019 to
 * HOST-023). Only a platform operational module reaches it, through its descriptor. The runtime runs each call in
 * sample-orders' own unit of work, bound to the tenant the command names (root BRX-RS-HOST-020).
 */
public interface SampleOrderAdministration {

    /** Stable contract identifier, as the manifest declares it. */
    String CONTRACT_ID = "brix.internal.sample-orders.administration";

    /**
     * Changes how many items an order has.
     *
     * @param command the order and the change
     * @return the order as it is now
     * @throws SampleOrderAdministrationException when the tenant has no such order, or the quantity would break a rule
     */
    AdjustedOrder adjustQuantity(AdjustOrderQuantity command);
}
