/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.starter.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

    @Test
    void responseStatusExceptionPreservesNotFoundInsteadOfInternalError() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/platform/admins");

        var response = handler.handleResponseStatusException(
            new ResponseStatusException(HttpStatus.NOT_FOUND, "No published Runtime Shell endpoint"),
            request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertEquals("PLATFORM-B-001", response.getBody().getCode());
    }
}
