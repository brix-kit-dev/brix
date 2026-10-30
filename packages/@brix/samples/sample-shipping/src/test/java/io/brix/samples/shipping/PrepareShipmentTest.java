/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.samples.shipping;

import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import org.springframework.core.ResolvableType;

import io.runtime.sdk.plugin.EventHandler;

/**
 * The runtime decodes each delivery into the handler's type argument, so the handler must name it.
 */
class PrepareShipmentTest {

    @Test
    void theHandlerNamesThePayloadItReads() {
        assertSame(OrderPlaced.class, ResolvableType.forClass(PrepareShipment.class).as(EventHandler.class)
            .getGeneric(0).resolve());
    }
}
