/*
 * Copyright 2026 Brix Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.sdk.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class RequestMetadataMaskingTest {

    @Test
    void anIpv4AddressKeepsItsSlash24() {
        assertEquals("203.0.113.0/24", RequestMetadataMasking.maskAddress("203.0.113.77"));
        assertEquals("203.0.113.0/24", RequestMetadataMasking.maskAddress(" 203.0.113.77 "));
    }

    @Test
    void anIpv6AddressKeepsItsSlash48() {
        assertEquals("2001:db8:85a3::/48", RequestMetadataMasking.maskAddress("2001:db8:85a3:8d3:1319:8a2e:370:7348"));
        assertEquals("2001:db8:0::/48", RequestMetadataMasking.maskAddress("[2001:db8::1]"));
        assertEquals("fe80:0:0::/48", RequestMetadataMasking.maskAddress("fe80::1%eth0"));
    }

    @Test
    void anIpv4MappedAddressIsMaskedAsIpv4() {
        assertEquals("198.51.100.0/24", RequestMetadataMasking.maskAddress("::ffff:198.51.100.9"));
    }

    @Test
    void anythingThatIsNotALiteralAddressIsDropped() {
        assertNull(RequestMetadataMasking.maskAddress(null));
        assertNull(RequestMetadataMasking.maskAddress(""));
        assertNull(RequestMetadataMasking.maskAddress("localhost"));
        assertNull(RequestMetadataMasking.maskAddress("example.com"));
        assertNull(RequestMetadataMasking.maskAddress("999.1.1.1"));
        assertNull(RequestMetadataMasking.maskAddress("203.0.113.77, 10.0.0.1"));
    }

    @Test
    void aUserAgentLosesControlCharactersAndIsCut() {
        assertEquals("Mozilla/5.0 test", RequestMetadataMasking.truncateUserAgent("Mozilla/5.0\n test\u0007"));
        assertEquals(RequestMetadataMasking.USER_AGENT_MAX_LENGTH,
            RequestMetadataMasking.truncateUserAgent("x".repeat(1000)).length());
        assertNull(RequestMetadataMasking.truncateUserAgent(" \t "));
        assertNull(RequestMetadataMasking.truncateUserAgent(null));
    }
}
