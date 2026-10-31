/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * Route templates and request paths (root BRX-RS-MANIFEST-023; standard-link plan L7.4).
 */
class EndpointPathTemplateTest {

    @Test
    void formsThatDifferOnlyInVariableNamesSlashesCaseOrEncodingAreOneRoute() {
        String identity = EndpointPathTemplate.parse("/api/v1/tenants/{tenantId}/members").identity();

        assertEquals("/api/v1/tenants/{}/members", identity);
        assertEquals(identity, EndpointPathTemplate.parse("/api/v1/tenants/{id}/members/").identity());
        assertEquals(identity, EndpointPathTemplate.parse("//api/v1//tenants/{id}/members").identity());
        assertEquals(identity, EndpointPathTemplate.parse("/API/V1/Tenants/{id}/Members").identity());
        assertEquals(identity, EndpointPathTemplate.parse("/api/v1/%74enants/{id}/members").identity());
    }

    @Test
    void aTemplateAVariableCannotTakeWholeOrARequestCannotMatchIsRejected() {
        for (String path : List.of("/api/{id}.json", "/api/{a}/x/{a}", "/api/{1st}", "/api/{}", "/api/a%2Fb",
                "/api/%zz", "/api/../x", "/api/a%3Fb", "api/x")) {
            assertThrows(IllegalArgumentException.class, () -> EndpointPathTemplate.parse(path), path);
        }
    }

    @Test
    void aRequestMatchesLiteralsExactlyAndVariablesDecoded() {
        EndpointPathTemplate template = EndpointPathTemplate.parse("/api/v1/tenants/by-code/{code}");

        assertEquals(Optional.of(Map.of("code", "a b/é")), template.match(segments("/api/v1/tenants/by-code/a%20b%2F%C3%A9")));
        assertEquals(Optional.of(Map.of("code", "x")), template.match(segments("//api/v1/tenants//by-code/x/")));
        assertTrue(template.match(segments("/api/v1/Tenants/by-code/x")).isEmpty(), "literals match exactly");
        assertTrue(template.match(segments("/api/v1/tenants/by-code")).isEmpty());
    }

    @Test
    void aRequestPathWithABrokenEncodingOrADotSegmentMatchesNothing() {
        assertTrue(EndpointPathTemplate.requestSegments("/api/%zz").isEmpty());
        assertTrue(EndpointPathTemplate.requestSegments("/api/%C3").isEmpty(), "not UTF-8");
        assertTrue(EndpointPathTemplate.requestSegments("/api/../platform/admins").isEmpty());
        assertTrue(EndpointPathTemplate.requestSegments("/api/%2e/x").isEmpty());
        assertEquals(Optional.of(List.of()), EndpointPathTemplate.requestSegments("/"));
    }

    @Test
    void theTemplateWithALiteralWhereTheOtherHasAVariableWins() {
        EndpointPathTemplate byCode = EndpointPathTemplate.parse("/api/v1/tenants/by-code/{code}");
        EndpointPathTemplate members = EndpointPathTemplate.parse("/api/v1/tenants/{tenantId}/members");

        assertTrue(EndpointPathTemplate.compare(byCode, members) < 0);
        assertTrue(EndpointPathTemplate.compare(members, byCode) > 0);
        assertEquals(0, EndpointPathTemplate.compare(members, EndpointPathTemplate.parse("/api/v1/tenants/{id}/x")));
    }

    @Test
    void reservedPrefixesAreFoundWhateverTheCase() {
        assertTrue(EndpointPathTemplate.parse("/API/Platform/tenants").startsWith("api", "platform"));
        assertTrue(EndpointPathTemplate.parse("/.well-known/openid-configuration").startsWith(".well-known"));
        assertTrue(!EndpointPathTemplate.parse("/api/{area}/tenants").startsWith("api", "platform"));
    }

    private static List<String> segments(String path) {
        return EndpointPathTemplate.requestSegments(path).orElseThrow();
    }
}
