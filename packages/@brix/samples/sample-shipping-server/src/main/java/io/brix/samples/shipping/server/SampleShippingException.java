/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.shipping.server;

import java.util.Map;

import io.runtime.sdk.error.BrixException;
import io.runtime.sdk.error.ErrorCategory;
import io.runtime.sdk.error.ErrorCode;

/**
 * An expected failure of a sample-shipping endpoint (root BRX-RS-MANIFEST-022; standard-link plan L7.3). Each code
 * names its category; the runtime answers with the category's status.
 */
final class SampleShippingException extends BrixException {

    private static final long serialVersionUID = 1L;

    /** sample-shipping's stable codes. */
    enum Code implements ErrorCode {
        /** The order has no shipment in the caller's tenant. */
        SHIPMENT_NOT_FOUND("sample-shipping.shipment_not_found", ErrorCategory.NOT_FOUND);

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

    SampleShippingException(Code code) {
        super(code, Map.of(), null);
    }
}
