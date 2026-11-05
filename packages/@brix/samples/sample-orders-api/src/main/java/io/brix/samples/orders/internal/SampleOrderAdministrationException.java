/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.internal;

/**
 * The stable answer of sample-orders to an operator's request it refuses. The unit of work rolls back.
 */
public final class SampleOrderAdministrationException extends RuntimeException {

    /** The tenant has no such order. */
    public static final String ORDER_NOT_FOUND = "SAMPLE_ORDER_NOT_FOUND";

    /** The change would leave the order outside the quantities it may have. */
    public static final String QUANTITY_INVALID = "SAMPLE_ORDER_QUANTITY_INVALID";

    private static final long serialVersionUID = 1L;

    private final String code;

    /**
     * Creates the refusal.
     *
     * @param code one of the codes above
     * @param message what is wrong
     */
    public SampleOrderAdministrationException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * Returns the refusal's stable code.
     *
     * @return the code
     */
    public String code() {
        return code;
    }
}
