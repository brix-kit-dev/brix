/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.external;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * The host's directory and the targets it refuses (root BRX-RS-ERROR-014; standard-link plan L8.3).
 */
class ExternalServiceDirectoryTest {

    @Test
    void anAddressIsAnAbsoluteUrlWhosePathPrefixesEveryRequest() {
        ExternalServiceDirectory directory = ExternalServiceDirectory.of(
            Map.of("keys", "https://www.googleapis.com/", "billing", "https://billing.example.test:8443/api/v2/"),
            true);

        assertEquals(Optional.of(URI.create("https://www.googleapis.com")), directory.address("keys"));
        assertEquals(Optional.of(URI.create("https://billing.example.test:8443/api/v2")), directory.address("billing"));
        assertTrue(directory.address("other").isEmpty());
    }

    @Test
    void aProductionHostTakesHttpsOnlyAndNoAddressNamesAUserQueryOrFragment() {
        assertThrows(IllegalArgumentException.class,
            () -> ExternalServiceDirectory.of(Map.of("keys", "http://keys.example.test"), true));
        assertEquals(Optional.of(URI.create("http://keys.example.test")),
            ExternalServiceDirectory.of(Map.of("keys", "http://keys.example.test"), false).address("keys"));
        for (String url : List.of("ftp://keys.example.test", "https://user:secret@keys.example.test",
                "https://keys.example.test/?a=1", "https://keys.example.test/#x", "keys.example.test", "https://")) {
            assertThrows(IllegalArgumentException.class,
                () -> ExternalServiceDirectory.of(Map.of("keys", url), false), url);
        }
    }

    @Test
    void aNameTheHostListsAsItsOwnIsRefused() throws Exception {
        InetAddress publicAddress = InetAddress.getByAddress(new byte[] {(byte) 198, 51, 100, 7});
        ExternalTargetPolicy policy = new ExternalTargetPolicy(List.of("host.example.test"), Set::of, name -> {
            if ("host.example.test".equals(name) || "alias.example.test".equals(name)) {
                return new InetAddress[] {publicAddress};
            }
            if ("elsewhere.example.test".equals(name)) {
                return new InetAddress[] {InetAddress.getByAddress(new byte[] {(byte) 203, 0, 113, 10})};
            }
            throw new UnknownHostException(name);
        });

        assertEquals(Optional.of("this-host"), policy.refusal("alias.example.test"));
        assertEquals(Optional.empty(), policy.refusal("elsewhere.example.test"));
        assertThrows(UnknownHostException.class, () -> policy.refusal("missing.example.test"));
    }
}
