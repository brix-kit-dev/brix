/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import io.runtime.sdk.capability.AuthCapability;
import io.runtime.sdk.capability.AuthenticatedPrincipal;
import io.runtime.sdk.capability.DataScope;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.TraceContext;
import io.runtime.sdk.plugin.TypedEndpointHandler;

/**
 * The controller hands the body to the dispatcher unread, which reads it only once the call has passed its access
 * checks (root BRX-RS-MANIFEST-022, 3.4.0; standard-link plan queue item 10, Q10b, D130 to D132). A JSON body is read
 * as Spring's JSON converter read it before, which a reference controller with {@code @RequestBody} shows; a body
 * that is not JSON text is an invalid request; a body of another content type is answered 415 as Spring answers it,
 * YAML now among them.
 */
class EndpointRequestBodyTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String TYPED = "/api/test/typed";
    private static final String LEGACY = "/api/test/legacy";
    private static final String GUARDED = "/api/platform/guarded";
    private static final String PERMISSION = "platform:thing:create";
    private static final List<Map<String, String>> NOT_JSON = List.of(Map.of("path", "$", "rule", "json"));

    record Payment(BigDecimal amount, String name) {
    }

    private final AtomicReference<Object> typedBody = new AtomicReference<>();
    private final AtomicReference<Object> legacyBody = new AtomicReference<>();
    private final AtomicInteger handled = new AtomicInteger();
    private final List<String> spans = new CopyOnWriteArrayList<>();
    private final List<String> outcomes = new CopyOnWriteArrayList<>();

    final class Typed implements TypedEndpointHandler<Payment, Map<String, Object>> {
        @Override
        public CompletionStage<Map<String, Object>> handle(EndpointInvocation<Payment> invocation) {
            handled.incrementAndGet();
            typedBody.set(invocation.body());
            return CompletableFuture.completedFuture(Map.of("ok", true));
        }
    }

    @Test
    void aJsonBodyIsReadAsSpringsJsonConverterReadIt() throws Exception {
        List<String> texts = List.of(
            "{\"amount\": 12345678901234567.89, \"items\": [1, 2.5, -0.0], \"name\": \"caf\\u00e9\"}",
            "[1, 2.5, \"x\"]", "\"text\"", "12", "true", "{\"a\": {\"b\": [true, null, {}]}}", "null");
        for (String text : texts) {
            for (String type : List.of("application/json", "application/json;charset=UTF-8",
                    "application/merge-patch+json")) {
                Object spring = springReads(post(LEGACY).contentType(type).content(text));
                send(runtime(null), post(LEGACY).contentType(type).content(text));

                assertEquals(spring, legacyBody.getAndSet(null), type + " " + text);
            }
        }
        String accented = "{\"amount\": 0.10, \"name\": \"café\"}";
        for (var charset : List.of(StandardCharsets.ISO_8859_1, StandardCharsets.UTF_16, StandardCharsets.UTF_8)) {
            MockHttpServletRequestBuilder request = post(LEGACY).contentType("application/json;charset=" + charset.name())
                .content(accented.getBytes(charset));
            Object spring = springReads(request);
            send(runtime(null), post(LEGACY).contentType("application/json;charset=" + charset.name())
                .content(accented.getBytes(charset)));

            assertEquals(Map.of("amount", 0.1, "name", "café"), spring, charset.name());
            assertEquals(spring, legacyBody.getAndSet(null), charset.name());
        }
    }

    @Test
    void aTypedEndpointStillGetsEveryDecimalDigitTheClientWrote() throws Exception {
        String text = "{\"amount\": 12345678901234567.89, \"name\": \"café\"}";
        for (var charset : List.of(StandardCharsets.UTF_8, StandardCharsets.ISO_8859_1, StandardCharsets.UTF_16)) {
            MockHttpServletResponse answer = send(runtime(null), post(TYPED)
                .contentType("application/json;charset=" + charset.name()).content(text.getBytes(charset)));

            assertEquals(200, answer.getStatus(), answer.getContentAsString());
            assertEquals(new Payment(new BigDecimal("12345678901234567.89"), "café"), typedBody.getAndSet(null),
                charset.name());
        }
    }

    @Test
    void aBodyThatIsNotJsonTextIsAnInvalidRequestWhereSpringFailedBeforeTheDispatcher() throws Exception {
        // D131: the call's own envelope, one violation for the whole body, nothing of what it held.
        for (String text : List.of("{\"amount\": ", "{\"amount\": 1} {", "   ", "{\"amount\": tru}", "{'amount': 1}")) {
            for (String path : List.of(TYPED, LEGACY)) {
                assertEquals(400, springStatus(post(path).contentType(MediaType.APPLICATION_JSON).content(text)), text);
                MockHttpServletResponse answer = send(runtime(null),
                    post(path).contentType(MediaType.APPLICATION_JSON).content(text));

                assertInvalid(answer, path + " " + text);
                assertFalse(answer.getContentAsString().contains("amount"), answer.getContentAsString());
            }
        }
        assertEquals(0, handled.get());
    }

    @Test
    void aBodyOfAnotherContentTypeIsAnswered415AsSpringAnswersItAndYamlNoLongerReads() throws Exception {
        // D132: answered as Spring answers a content type it does not read; D130: JSON only.
        List<MockHttpServletRequestBuilder> refused = List.of(
            post(LEGACY).contentType(MediaType.TEXT_PLAIN).content("{\"amount\": 1}"),
            post(LEGACY).contentType(MediaType.APPLICATION_XML).content("<amount>1</amount>"),
            post(LEGACY).contentType(MediaType.APPLICATION_FORM_URLENCODED).content("amount=1"),
            post(LEGACY).contentType(MediaType.ALL).content("{\"amount\": 1}"),
            post(LEGACY).content("{\"amount\": 1}"),
            post(LEGACY).contentType(MediaType.TEXT_PLAIN),
            post(TYPED).contentType(MediaType.TEXT_PLAIN).content("{\"amount\": 1}"));
        for (MockHttpServletRequestBuilder request : refused) {
            assertEquals(415, springStatus(request));
            assertEquals(415, send(runtime(null), request).getStatus());
        }
        MockHttpServletResponse invalidType = send(runtime(null), post(LEGACY).header("Content-Type", "not a type")
            .content("{\"amount\": 1}"));
        assertEquals(415, invalidType.getStatus());
        assertEquals(415, springStatus(post(LEGACY).header("Content-Type", "not a type").content("{\"amount\": 1}")));
        // Spring reads YAML when the host has the YAML module, as hosts do; the runtime does not.
        MockHttpServletRequestBuilder yaml = post(LEGACY).contentType("application/yaml").content("amount: 1\n");
        assertEquals(200, springStatus(yaml));
        assertEquals(415, send(runtime(null), yaml).getStatus());
        assertEquals(0, handled.get());
    }

    @Test
    void whatSpringIgnoredIsStillIgnored() throws Exception {
        // No body and no content type is no body; a GET's body is read only when it is JSON, as Spring read it.
        assertEquals(200, send(runtime(null), post(LEGACY)).getStatus());
        assertNull(legacyBody.get());
        assertEquals(200, send(runtime(null), post(LEGACY).contentType(MediaType.APPLICATION_JSON).content("")).getStatus());
        assertNull(legacyBody.get());
        assertEquals(200, send(runtime(null), get(LEGACY).contentType(MediaType.TEXT_PLAIN).content("x")).getStatus());
        assertNull(legacyBody.getAndSet(null));
        assertEquals(200, send(runtime(null), get(LEGACY).content("x")).getStatus());
        assertNull(legacyBody.getAndSet(null));
        assertEquals(200, send(runtime(null), get(LEGACY).contentType(MediaType.APPLICATION_JSON)
            .content("{\"a\": 1}")).getStatus());
        assertEquals(Map.of("a", 1), legacyBody.getAndSet(null));
        assertEquals(springReads(get(LEGACY).contentType(MediaType.APPLICATION_JSON).content("{\"a\": 1}")),
            Map.of("a", 1));
        assertInvalid(send(runtime(null), get(LEGACY).contentType(MediaType.APPLICATION_JSON).content("{\"a\": ")),
            "a GET's JSON body that is not JSON text");
    }

    @Test
    void aCallTheAccessChecksRefuseIsNeverToldAboutItsBody() throws Exception {
        List<MockHttpServletRequestBuilder> bodies = List.of(
            post(GUARDED).contentType(MediaType.APPLICATION_JSON).content("{\"amount\": "),
            post(GUARDED).contentType(MediaType.TEXT_PLAIN).content("{\"amount\": 1}"),
            post(GUARDED).contentType("application/yaml").content("amount: 1\n"),
            post(GUARDED).contentType(MediaType.APPLICATION_FORM_URLENCODED).content("amount=1"),
            post(GUARDED).header("Content-Type", "not a type").content("{\"amount\": 1}"),
            post(GUARDED).content("{\"amount\": 1}"));
        for (MockHttpServletRequestBuilder request : bodies) {
            assertDenied(send(runtime(null), request), 401, EndpointAccessDeniedException.AUTHENTICATION_REQUIRED);
            assertDenied(send(runtime(platformAdmin(Set.of("platform:thing:read"))), request), 403,
                EndpointAccessDeniedException.PERMISSION_DENIED);
        }
        assertEquals(0, handled.get());

        // A call that passes them has its body read.
        assertInvalid(send(runtime(platformAdmin(Set.of(PERMISSION))), bodies.get(0)), "bad JSON");
        assertEquals(415, send(runtime(platformAdmin(Set.of(PERMISSION))), bodies.get(1)).getStatus());
        assertEquals(415, send(runtime(platformAdmin(Set.of(PERMISSION))), bodies.get(2)).getStatus());
        assertEquals(200, send(runtime(platformAdmin(Set.of(PERMISSION))),
            post(GUARDED).contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 1}")).getStatus());
        assertEquals(1, handled.get());
    }

    @Test
    void theControllerReadsNoPartOfARefusedCallsBody() throws Exception {
        for (String type : List.of("application/json", "application/x-www-form-urlencoded", "multipart/form-data",
                "text/plain")) {
            HttpServletRequest request = servletPost(GUARDED, type);

            ResponseEntity<Object> answer = controller(null).dispatch(request);

            assertEquals(401, answer.getStatusCode().value(), type);
            verify(request, never()).getInputStream();
            verify(request, never()).getReader();
            verify(request, never()).getParts();
            if (!"application/json".equals(type) && !"text/plain".equals(type)) {
                // Reading a form's parameters would parse its body.
                verify(request, never()).getParameterMap();
                verify(request, never()).getParameter(any());
            }
        }
        // Nor a form's, once the call has passed its checks: its call ends in 415.
        HttpServletRequest form = servletPost(GUARDED, "application/x-www-form-urlencoded");
        assertThrows(HttpMediaTypeNotSupportedException.class,
            () -> controller(platformAdmin(Set.of(PERMISSION))).dispatch(form));
        verify(form, never()).getParameterMap();
        verify(form, never()).getInputStream();
    }

    @Test
    void aDispatcherOfAHostsOwnGetsTheBodyReadFirstAsBefore() throws Exception {
        // A host that provides its own PluginEndpointDispatcher keeps working (root BRX-RS-CAP-019).
        AtomicReference<Object> received = new AtomicReference<>();
        PluginEndpointDispatcher own = new PluginEndpointDispatcher() {
            @Override
            public void replaceSnapshot(java.util.Collection<EndpointRoute> routes) {
            }

            @Override
            public Object invoke(String method, String path, Object body, Map<String, List<String>> queryParameters,
                    Map<String, List<String>> headers) {
                received.set(body);
                return Map.of("ok", true);
            }

            @Override
            public List<EndpointRoute> routes() {
                return List.of();
            }
        };
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new RuntimeShellEndpointController(own, () -> null, JSON)).build();

        assertEquals(200, send(mvc, post(LEGACY).contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\": 2.5}")).getStatus());
        assertEquals(Map.of("amount", 2.5), received.get());
        assertEquals(400, send(mvc, post(LEGACY).contentType(MediaType.APPLICATION_JSON).content("{")).getStatus());
        assertEquals(415, send(mvc, post(LEGACY).contentType(MediaType.TEXT_PLAIN).content("x")).getStatus());
    }

    @Test
    void theSpanAndTheMetersRecordTheAnswer() throws Exception {
        MockMvc granted = runtime(platformAdmin(Set.of(PERMISSION)));

        send(granted, post(GUARDED).contentType(MediaType.TEXT_PLAIN).content("x"));
        send(granted, post(GUARDED).contentType(MediaType.APPLICATION_JSON).content("{"));
        send(runtime(null), post(GUARDED).contentType(MediaType.APPLICATION_JSON).content("{"));

        assertEquals(List.of("415 " + EndpointMediaTypeUnsupportedException.ERROR_TYPE,
            "400 " + EndpointRequestInvalidException.CODE,
            "401 " + EndpointAccessDeniedException.AUTHENTICATION_REQUIRED), spans);
        assertEquals(List.of("invalid", "invalid", "denied"), outcomes);
    }

    private void assertInvalid(MockHttpServletResponse answer, String what) throws Exception {
        assertEquals(400, answer.getStatus(), what);
        JsonNode body = JSON.readTree(answer.getContentAsString());
        assertEquals(List.of("errorCode", "message", "violations", "traceId"), List.copyOf(body.propertyNames()), what);
        assertEquals(EndpointRequestInvalidException.CODE, body.get("errorCode").asString(), what);
        assertEquals(NOT_JSON, JSON.convertValue(body.get("violations"), List.class), what);
    }

    private static void assertDenied(MockHttpServletResponse answer, int status, String code) throws Exception {
        assertEquals(status, answer.getStatus(), answer.getContentAsString());
        JsonNode body = JSON.readTree(answer.getContentAsString());
        assertEquals(code, body.get("errorCode").asString());
        assertFalse(body.has("violations"), answer.getContentAsString());
    }

    /** What Spring read with {@code @RequestBody} before, through the same MockMvc. */
    private Object springReads(MockHttpServletRequestBuilder request) throws Exception {
        SpringReading reading = new SpringReading();
        MockHttpServletResponse answer = MockMvcBuilders.standaloneSetup(reading).build().perform(request)
            .andReturn().getResponse();
        assertEquals(200, answer.getStatus(), answer.getContentAsString());
        return reading.body.get();
    }

    private static int springStatus(MockHttpServletRequestBuilder request) throws Exception {
        return MockMvcBuilders.standaloneSetup(new SpringReading()).build().perform(request).andReturn().getResponse()
            .getStatus();
    }

    /** The old controller's way: Spring reads the body before the method runs. */
    @RestController
    static final class SpringReading {

        private final AtomicReference<Object> body = new AtomicReference<>();

        @RequestMapping(path = "/**", method = {RequestMethod.GET, RequestMethod.POST})
        public ResponseEntity<Object> read(@RequestBody(required = false) Object read) {
            body.set(read);
            return ResponseEntity.ok(Map.of("ok", true));
        }
    }

    private static MockHttpServletResponse send(MockMvc mvc, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse();
    }

    private MockMvc runtime(AuthCapability auth) {
        return MockMvcBuilders.standaloneSetup(controller(auth)).build();
    }

    private RuntimeShellEndpointController controller(AuthCapability auth) {
        EndpointTracing tracing = (route, caller) -> new EndpointTracing.EndpointSpan() {
            private final TraceContext trace = EndpointTracing.Traceparents.random();

            @Override
            public TraceContext trace() {
                return trace;
            }

            @Override
            public void finished(int status, String errorCode) {
                spans.add(status + " " + errorCode);
            }
        };
        EndpointMetrics metrics = new EndpointMetrics() {
            @Override
            public void finished(String pluginId, String endpointId, String outcome, Duration took) {
                outcomes.add(outcome);
            }
        };
        DefaultPluginEndpointDispatcher dispatcher = new DefaultPluginEndpointDispatcher(
            EndpointPolicies.Limits.defaults(Duration.ofSeconds(5)), EndpointInvocationBoundary.direct(), JSON, tracing,
            metrics);
        EndpointHandler<EndpointInvocation<Object>, Object> legacy = invocation -> {
            handled.incrementAndGet();
            legacyBody.set(invocation.body());
            return Map.of("ok", true);
        };
        EndpointAccessPolicy anyone = TestEndpointAccessPolicies.anonymous("test-public");
        dispatcher.replaceSnapshot(List.of(
            new EndpointRoute("test-plugin", "test.typed.v1", "POST", TYPED, anyone, new Typed(),
                new EndpointContract("", "", 200)),
            new EndpointRoute("test-plugin", "test.legacy.v1", "POST", LEGACY, anyone, legacy,
                EndpointContract.none()),
            new EndpointRoute("test-plugin", "test.legacy-read.v1", "GET", LEGACY, anyone, legacy,
                EndpointContract.none()),
            new EndpointRoute("test-plugin", "test.guarded.v1", "POST", GUARDED,
                TestEndpointAccessPolicies.platform(PERMISSION), legacy, EndpointContract.none())));
        return new RuntimeShellEndpointController(dispatcher, () -> auth, JSON);
    }

    /** A servlet request whose body and parameters fail the test when anything reads them. */
    private static HttpServletRequest servletPost(String path, String contentType) throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getMethod()).thenReturn("POST");
        when(request.getRequestURI()).thenReturn(path);
        when(request.getContextPath()).thenReturn("");
        when(request.getContentType()).thenReturn(contentType);
        when(request.getHeaderNames()).thenReturn(Collections.enumeration(List.of("content-type")));
        when(request.getHeaders("content-type")).thenReturn(Collections.enumeration(List.of(contentType)));
        when(request.getParameterMap()).thenReturn(Map.of("amount", new String[] {"1"}));
        when(request.getInputStream()).thenThrow(new AssertionError("the body was read"));
        when(request.getReader()).thenThrow(new AssertionError("the body was read"));
        return request;
    }

    private static AuthCapability platformAdmin(Set<String> permissions) {
        return new AuthCapability() {
            @Override
            public Principal getCurrentPrincipal() {
                return new Admin(permissions);
            }

            @Override
            public boolean hasPermission(String permission) {
                return false;
            }

            @Override
            @SuppressWarnings("removal")
            public boolean hasRole(String role) {
                return false;
            }

            @Override
            public Set<DataScope> getAuthorizedScopes() {
                return Set.of();
            }

            @Override
            public String getTenantId() {
                return null;
            }
        };
    }

    private record Admin(Set<String> permissions) implements AuthenticatedPrincipal {

        @Override
        public String getName() {
            return "1001";
        }

        @Override
        public String getUserId() {
            return "1001";
        }

        @Override
        public String getTenantId() {
            return null;
        }

        @Override
        public String getEmail() {
            return null;
        }

        @Override
        public String getTokenRole() {
            return "platform-admin";
        }

        @Override
        public String getTokenType() {
            return "access";
        }

        @Override
        public String getTokenScope() {
            return "PLATFORM";
        }

        @Override
        public Set<String> getPermissions() {
            return permissions;
        }
    }
}
