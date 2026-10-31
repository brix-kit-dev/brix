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

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.invocation.RuntimeInvocation;
import io.runtime.orchestrator.invocation.RuntimeInvocations;
import io.runtime.sdk.capability.BrixExternalHttpException;
import io.runtime.sdk.capability.ExternalHttpCapability;
import io.runtime.sdk.capability.ExternalHttpRequest;
import io.runtime.sdk.capability.ExternalHttpResponse;
import io.runtime.sdk.plugin.AccessDecision;
import io.runtime.sdk.plugin.CallerIdentity;
import io.runtime.sdk.plugin.CancellationSignal;
import io.runtime.sdk.plugin.InvocationScope;
import io.runtime.sdk.plugin.TraceContext;

/**
 * External calls under the runtime's policies (root BRX-RS-ERROR-014, RESILIENCE-001; standard-link plan L8.3, L8.4).
 */
class ExternalHttpGatewayTest {

    private static final URI ADDRESS = URI.create("https://keys.example.test");
    private static final InetAddress PUBLIC = address(203, 0, 113, 10);
    private static final String CALLER_TRACEPARENT = "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01";

    private final ScriptedTransport transport = new ScriptedTransport();
    private final RecordingMetrics metrics = new RecordingMetrics();
    private final ExecutorService pool = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() {
        pool.shutdownNow();
    }

    @Test
    void aDeclaredServiceIsCalledAtItsAddressWithThePathAndQuery() {
        transport.answer(200, "{\"keys\":[]}");
        ExternalHttpCapability calls = runtime(limits(), Set.of(), "keys.example.test", PUBLIC).forModule("plugin-a");

        ExternalHttpResponse answer = calls.service("keys").send(ExternalHttpRequest.get("/oauth2/v3/certs")
            .withQuery("kid", "a b"));

        assertEquals(200, answer.status());
        assertEquals("{\"keys\":[]}", answer.text());
        assertEquals(URI.create("https://keys.example.test/oauth2/v3/certs?kid=a+b"), transport.targets.get(0));
        assertEquals(List.of("plugin-a|keys|2xx"), metrics.outcomes);
    }

    @Test
    void aServiceTheModuleDoesNotDeclareOrTheHostDoesNotListIsUndeclared() {
        ExternalHttpRuntime runtime = runtime(limits(), Set.of(), "keys.example.test", PUBLIC);

        assertCode(BrixExternalHttpException.Code.UNDECLARED_SERVICE, () -> runtime.forModule("plugin-a").service("other"));
        assertCode(BrixExternalHttpException.Code.UNDECLARED_SERVICE, () -> runtime.forModule("plugin-b").service("keys"));
        runtime.register("plugin-c", List.of(declaration("unlisted", "nowhere", ExternalHttpPolicies.RETRY_NONE)));
        assertCode(BrixExternalHttpException.Code.UNDECLARED_SERVICE, () -> runtime.forModule("plugin-c").service("unlisted"));
    }

    @Test
    void anAddressOnThisHostStopsTheModuleAtStartAndIsRefusedAtEveryCall() {
        for (InetAddress target : List.of(address(127, 0, 0, 1), address(169, 254, 169, 254), address(10, 0, 0, 7))) {
            ExternalHttpRuntime runtime = runtime(limits(), Set.of(address(10, 0, 0, 7)), "keys.example.test",
                PUBLIC);
            ExternalHttpCapability.ExternalService keys = runtime.forModule("plugin-a").service("keys");
            ExternalHttpRuntime rebound = runtime(limits(), Set.of(address(10, 0, 0, 7)), "keys.example.test", target);

            assertThrows(IllegalStateException.class, () -> rebound.register("plugin-z",
                List.of(declaration("keys", "keys", ExternalHttpPolicies.RETRY_NONE))), target.toString());
            transport.answer(200, "");
            resolveAs(target);
            assertCode(BrixExternalHttpException.Code.TARGET_FORBIDDEN, () -> keys.send(ExternalHttpRequest.get("/x")));
            resolveAs(PUBLIC);
        }
        assertTrue(transport.targets.isEmpty(), "nothing was sent");
    }

    @Test
    void anIdempotentCallIsRetriedOnAGatewayErrorAndAWriteIsNot() {
        ExternalHttpCapability calls = runtime(limits(), Set.of(), "keys.example.test", PUBLIC).forModule("plugin-a");
        transport.answer(503, "busy");
        transport.answer(200, "ok");

        assertEquals(200, calls.service("keys").send(ExternalHttpRequest.get("/x")).status());
        assertEquals(2, transport.targets.size());

        transport.answer(503, "busy");
        assertEquals(503, calls.service("keys").send(ExternalHttpRequest.postJson("/x", "{}")).status(),
            "a write is sent once and its answer returned");
        assertEquals(3, transport.targets.size());
    }

    @Test
    void aFailedCallWithoutRetryFailsAndATimeoutIsADeadline() {
        ExternalHttpRuntime runtime = runtime(limits(), Set.of(), "keys.example.test", PUBLIC);
        runtime.register("plugin-b", List.of(declaration("keys", "keys", ExternalHttpPolicies.RETRY_NONE)));
        ExternalHttpCapability.ExternalService keys = runtime.forModule("plugin-b").service("keys");

        transport.fail(new IOException("connection reset"));
        assertCode(BrixExternalHttpException.Code.FAILED, () -> keys.send(ExternalHttpRequest.get("/x")));
        transport.fail(new HttpTimeoutException("slow"));
        assertCode(BrixExternalHttpException.Code.DEADLINE_EXCEEDED, () -> keys.send(ExternalHttpRequest.get("/x")));
        assertEquals(List.of("plugin-b|keys|failed", "plugin-b|keys|deadline_exceeded"), metrics.outcomes);
    }

    @Test
    void theCallEndsAtTheDeadlineOfTheCallThePluginIsHandlingAndCarriesItsTrace() {
        ExternalHttpCapability calls = runtime(limits(), Set.of(), "keys.example.test", PUBLIC).forModule("plugin-a");
        transport.answer(200, "");
        RuntimeInvocation handling = new RuntimeInvocation(CallerIdentity.service("plugin-a"),
            RuntimeInvocation.Kind.HTTP, Set.of(), InvocationScope.platform(), AccessDecision.none(),
            Optional.of(new TraceContext(CALLER_TRACEPARENT, null)), Instant.now().plusMillis(800),
            CancellationSignal.none(), 0);

        RuntimeInvocations.within(handling, () -> calls.service("keys").send(ExternalHttpRequest.get("/x")));

        assertTrue(transport.timeouts.get(0).compareTo(Duration.ofMillis(800)) <= 0, transport.timeouts.toString());
        assertEquals(List.of(CALLER_TRACEPARENT), transport.headers.get(0).get("traceparent"));
        RuntimeInvocation late = new RuntimeInvocation(CallerIdentity.service("plugin-a"),
            RuntimeInvocation.Kind.HTTP, Set.of(), InvocationScope.platform(), AccessDecision.none(),
            Optional.empty(), Instant.now().minusMillis(1), CancellationSignal.none(), 0);
        assertCode(BrixExternalHttpException.Code.DEADLINE_EXCEEDED, () -> RuntimeInvocations.within(late,
            () -> calls.service("keys").send(ExternalHttpRequest.get("/x"))));
    }

    @Test
    void aCallOverTheModulesLimitIsRejectedAtOnce() throws Exception {
        ExternalHttpCapability calls = runtime(new ExternalHttpPolicies.Limits(Duration.ofSeconds(5), 1, 3,
            Duration.ZERO, 1024), Set.of(), "keys.example.test", PUBLIC).forModule("plugin-a");
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        transport.block(entered, release);

        Future<ExternalHttpResponse> first = pool.submit(() -> calls.service("keys").send(ExternalHttpRequest.get("/x")));
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        assertCode(BrixExternalHttpException.Code.REJECTED, () -> calls.service("keys").send(ExternalHttpRequest.get("/x")));
        release.countDown();
        assertEquals(200, first.get(5, TimeUnit.SECONDS).status());
    }

    @Test
    void aServiceThatKeepsFailingOpensItsCircuit() {
        ExternalHttpRuntime runtime = runtime(limits(), Set.of(), "keys.example.test", PUBLIC);
        runtime.register("plugin-b", List.of(declaration("keys", "keys", ExternalHttpPolicies.RETRY_NONE)));
        ExternalHttpCapability.ExternalService keys = runtime.forModule("plugin-b").service("keys");
        for (int i = 0; i < 10; i++) {
            transport.answer(500, "broken");
            keys.send(ExternalHttpRequest.get("/x"));
        }

        assertCode(BrixExternalHttpException.Code.CIRCUIT_OPEN, () -> keys.send(ExternalHttpRequest.get("/x")));
        assertEquals(10, transport.targets.size());
    }

    @Test
    void aPathTheUrlSyntaxRefusesFailsBeforeTheCallIsAdmitted() {
        ExternalHttpCapability calls = runtime(new ExternalHttpPolicies.Limits(Duration.ofSeconds(5), 1, 1,
            Duration.ZERO, 1024), Set.of(), "keys.example.test", PUBLIC).forModule("plugin-a");

        assertThrows(IllegalArgumentException.class,
            () -> calls.service("keys").send(ExternalHttpRequest.get("/a b")));
        transport.answer(200, "");
        assertEquals(200, calls.service("keys").send(ExternalHttpRequest.get("/a%20b")).status(),
            "the refused request took no permit");
    }

    private ExternalHttpRuntime runtime(ExternalHttpPolicies.Limits limits, Set<InetAddress> local, String host,
            InetAddress resolved) {
        resolveAs(resolved);
        ExternalTargetPolicy targets = new ExternalTargetPolicy(List.of(), () -> local, name -> {
            if (name.equals(host)) {
                return new InetAddress[] {current};
            }
            throw new UnknownHostException(name);
        });
        ExternalHttpRuntime runtime = new ExternalHttpRuntime(
            ExternalServiceDirectory.of(Map.of("keys", ADDRESS.toString()), true),
            new ExternalHttpGateway(limits, transport, targets, ExternalHttpTracing.NOOP, metrics));
        if (resolved == PUBLIC) {
            runtime.register("plugin-a", List.of(declaration("keys", "keys", ExternalHttpPolicies.RETRY_IDEMPOTENT)));
        }
        return runtime;
    }

    private InetAddress current;

    private void resolveAs(InetAddress address) {
        current = address;
    }

    private static ExternalHttpPolicies.Limits limits() {
        return new ExternalHttpPolicies.Limits(Duration.ofSeconds(5), 16, 3, Duration.ZERO, 1024 * 1024);
    }

    private static ExternalServiceDeclaration declaration(String id, String baseUrlRef, String retry) {
        return new ExternalServiceDeclaration(id, baseUrlRef, null, ExternalHttpPolicies.TIMEOUT_DEFAULT, retry,
            ExternalHttpPolicies.CONCURRENCY_STANDARD);
    }

    private static InetAddress address(int a, int b, int c, int d) {
        try {
            return InetAddress.getByAddress(new byte[] {(byte) a, (byte) b, (byte) c, (byte) d});
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void assertCode(BrixExternalHttpException.Code code, Runnable call) {
        BrixExternalHttpException failure = assertThrows(BrixExternalHttpException.class, call::run);
        assertEquals(code, failure.code());
    }

    /** Answers from a script, and records what it was asked to send. */
    private static final class ScriptedTransport implements ExternalHttpTransport {

        final List<URI> targets = new CopyOnWriteArrayList<>();
        final List<Map<String, List<String>>> headers = new CopyOnWriteArrayList<>();
        final List<Duration> timeouts = new CopyOnWriteArrayList<>();
        private final List<Object> script = new ArrayList<>();
        private CountDownLatch entered;
        private CountDownLatch release;

        synchronized void answer(int status, String body) {
            script.add(new ExternalHttpResponse(status, Map.of(), body.getBytes()));
        }

        synchronized void fail(IOException failure) {
            script.add(failure);
        }

        synchronized void block(CountDownLatch entered, CountDownLatch release) {
            this.entered = entered;
            this.release = release;
        }

        @Override
        public ExternalHttpResponse send(String method, URI target, Map<String, List<String>> sent, byte[] body,
                Duration timeout, long maxResponseBytes) throws IOException, InterruptedException {
            CountDownLatch waitFor;
            synchronized (this) {
                waitFor = release;
                if (entered != null) {
                    entered.countDown();
                    entered = null;
                    release = null;
                } else {
                    waitFor = null;
                }
            }
            if (waitFor != null) {
                waitFor.await(5, TimeUnit.SECONDS);
                return new ExternalHttpResponse(200, Map.of(), null);
            }
            targets.add(target);
            headers.add(sent);
            timeouts.add(timeout);
            Object next;
            synchronized (this) {
                next = script.isEmpty() ? new ExternalHttpResponse(200, Map.of(), null) : script.remove(0);
            }
            if (next instanceof IOException failure) {
                throw failure;
            }
            return (ExternalHttpResponse) next;
        }
    }

    private static final class RecordingMetrics implements ExternalHttpMetrics {

        final List<String> outcomes = new CopyOnWriteArrayList<>();

        @Override
        public void finished(String pluginId, String serviceId, String outcome, Duration took) {
            outcomes.add(pluginId + "|" + serviceId + "|" + outcome);
        }
    }
}
