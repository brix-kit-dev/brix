/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.core;

/**
 * An order breaks a rule: it is not placed and nothing is published.
 *
 * @author Brix Platform Authors
 * @since 3.2.0
 */
public final class InvalidOrderException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    InvalidOrderException(String message) {
        super(message);
    }
}
