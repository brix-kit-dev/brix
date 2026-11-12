/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder;
import org.springframework.web.bind.annotation.RequestBody;

import tools.jackson.databind.json.JsonMapper;

import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.TypedEndpointHandler;

/**
 * The runtime's controller as Spring MVC reads its requests (standard-link plan queue item 7, D123): with the body
 * advice, a typed endpoint gets every decimal digit of a JSON body, and every other request is answered as Spring
 * answers it without the advice.
 */
class EndpointJsonBodyAdviceTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String TYPED = "/api/test/typed";
    private static final String LEGACY = "/api/test/legacy";

    record Payment(BigDecimal amount, String name) {
    }

    private final AtomicReference<Object> typedBody = new AtomicReference<>();
    private final AtomicReference<Object> legacyBody = new AtomicReference<>();

    final class Typed implements TypedEndpointHandler<Payment, Map<String, Object>> {
        @Override
        public CompletionStage<Map<String, Object>> handle(EndpointInvocation<Payment> invocation) {
            typedBody.set(invocation.body());
            return CompletableFuture.completedFuture(Map.of("ok", true));
        }
    }

    @Test
    void aTypedEndpointGetsEveryDecimalDigitOfAJsonBody() throws Exception {
        MockHttpServletResponse answer = send(mvc(true), json(TYPED, "{\"amount\": 12345678901234567.89}"));

        assertEquals(200, answer.getStatus());
        assertEquals(new Payment(new BigDecimal("12345678901234567.89"), null), typedBody.get());
    }

    @Test
    void aHandlerOfTheOldShapeGetsTheBodyAsSpringReadsIt() throws Exception {
        send(mvc(true), json(LEGACY, "{\"amount\": 12345678901234567.89, \"items\": [1, 2.5]}"));
        Object withAdvice = legacyBody.getAndSet(null);
        send(mvc(false), json(LEGACY, "{\"amount\": 12345678901234567.89, \"items\": [1, 2.5]}"));

        assertEquals(Map.of("amount", 1.2345678901234568E16, "items", List.of(1, 2.5)), withAdvice);
        assertEquals(legacyBody.get(), withAdvice);
    }

    @Test
    void everyOtherRequestIsAnsweredAsWithoutTheAdvice() throws Exception {
        List<MockHttpServletRequestBuilder> requests = List.of(
            post(TYPED).contentType(MediaType.TEXT_PLAIN).content("{\"amount\": 1}"),
            post(TYPED).contentType(MediaType.APPLICATION_XML).content("<amount>1</amount>"),
            post(TYPED).content("{\"amount\": 1}"),
            json(TYPED, "{\"amount\": "),
            json(TYPED, "{\"amount\": 1} {"),
            json(TYPED, ""),
            json(TYPED, "null"),
            json(TYPED, "[1, 2.5]"),
            json(TYPED, "\"text\""),
            post(TYPED),
            json(LEGACY, "null"),
            json(LEGACY, "[1, 2.5]"));
        List<String> without = new ArrayList<>();
        List<String> with = new ArrayList<>();

        for (MockHttpServletRequestBuilder request : requests) {
            without.add(outcome(mvc(false), request));
            with.add(outcome(mvc(true), request));
        }

        assertEquals(without, with);
        assertTrue(without.get(0).startsWith("415"), without.get(0));
        assertTrue(without.get(3).startsWith("400"), without.get(3));
    }

    @Test
    void aBodyInAnotherCharsetIsDecodedAsSpringDecodesIt() throws Exception {
        String text = "{\"amount\": 0.10, \"name\": \"café\"}";

        send(mvc(true), post(TYPED).contentType("application/json;charset=ISO-8859-1")
            .content(text.getBytes(StandardCharsets.ISO_8859_1)));
        Object latin = typedBody.getAndSet(null);
        send(mvc(true), post(TYPED).contentType("application/json;charset=UTF-16")
            .content(text.getBytes(StandardCharsets.UTF_16)));

        assertEquals(new Payment(new BigDecimal("0.10"), "café"), latin);
        assertEquals(new Payment(new BigDecimal("0.10"), "café"), typedBody.get());
    }

    @Test
    void theAdviceKeepsOnlyWhatTheRuntimesControllerReadsAsJson() throws Exception {
        EndpointJsonBodyAdvice advice = new EndpointJsonBodyAdvice(JSON);
        MethodParameter runtime = new MethodParameter(RuntimeShellEndpointController.class.getMethod("dispatch",
            Object.class, HttpServletRequest.class), 0);
        MethodParameter other = new MethodParameter(OtherController.class.getMethod("take", Object.class), 0);

        assertTrue(advice.supports(runtime, Object.class, JacksonJsonHttpMessageConverter.class));
        assertFalse(advice.supports(runtime, Object.class, StringHttpMessageConverter.class));
        assertFalse(advice.supports(other, Object.class, JacksonJsonHttpMessageConverter.class));
    }

    /** Another controller of the host, which the advice leaves alone. */
    static final class OtherController {
        public Object take(@RequestBody Object body) {
            return body;
        }
    }

    private String outcome(MockMvc mvc, MockHttpServletRequestBuilder request) throws Exception {
        typedBody.set(null);
        legacyBody.set(null);
        MockHttpServletResponse answer = send(mvc, request);
        // Each call has a trace id of its own.
        String body = answer.getContentAsString().replaceAll("\"traceId\":\"[0-9a-f]+\"", "\"traceId\":\"…\"");
        return answer.getStatus() + " " + body + " typed=" + typedBody.get() + " legacy=" + legacyBody.get();
    }

    private static MockHttpServletResponse send(MockMvc mvc, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse();
    }

    private static MockHttpServletRequestBuilder json(String path, String text) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(text);
    }

    private MockMvc mvc(boolean withAdvice) {
        DefaultPluginEndpointDispatcher dispatcher = new DefaultPluginEndpointDispatcher(Duration.ofSeconds(5),
            EndpointInvocationBoundary.direct(), JSON);
        EndpointHandler<EndpointInvocation<Object>, Object> legacy = invocation -> {
            legacyBody.set(invocation.body());
            return Map.of("ok", true);
        };
        EndpointAccessPolicy anyone = TestEndpointAccessPolicies.anonymous("test-public");
        dispatcher.replaceSnapshot(List.of(
            new EndpointRoute("test-plugin", "test.typed.v1", "POST", TYPED, anyone, new Typed(),
                new EndpointContract("", "", 200)),
            new EndpointRoute("test-plugin", "test.legacy.v1", "POST", LEGACY, anyone, legacy,
                EndpointContract.none())));
        StandaloneMockMvcBuilder builder = MockMvcBuilders.standaloneSetup(new RuntimeShellEndpointController(dispatcher));
        if (withAdvice) {
            builder.setControllerAdvice(new EndpointJsonBodyAdvice(JSON));
        }
        return builder.build();
    }
}
