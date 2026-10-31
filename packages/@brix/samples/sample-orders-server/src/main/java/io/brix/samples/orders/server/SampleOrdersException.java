/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.orders.server;

import java.util.Map;

import io.runtime.sdk.error.BrixException;
import io.runtime.sdk.error.ErrorCategory;
import io.runtime.sdk.error.ErrorCode;

/**
 * An expected failure of a sample-orders endpoint (root BRX-RS-MANIFEST-022; standard-link plan L7.3). Each code names
 * its category; the runtime answers with the category's status.
 */
final class SampleOrdersException extends BrixException {

    private static final long serialVersionUID = 1L;

    /** sample-orders' stable codes. */
    enum Code implements ErrorCode {
        /** The request is not an order the plugin accepts. */
        ORDER_INVALID("sample-orders.order_invalid", ErrorCategory.INVALID),
        /** The caller's tenant has no such order. */
        ORDER_NOT_FOUND("sample-orders.order_not_found", ErrorCategory.NOT_FOUND);

        private final String wireCode;
        private final ErrorCategory category;

        Code(String wireCode, ErrorCategory category) {
            this.wireCode = wireCode;
            this.category = category;
        }

        @Override
        public String wireCode() {
            return wireCode;
        }

        @Override
        public ErrorCategory category() {
            return category;
        }
    }

    SampleOrdersException(Code code) {
        super(code, Map.of(), null);
    }
}
