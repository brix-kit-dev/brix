/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import io.runtime.orchestrator.persistence.OwnerBoundaryProxies;
import io.runtime.sdk.capability.UnitOfWork;
import io.runtime.sdk.plugin.EndpointHandlingException;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.EndpointResponse;
import io.runtime.sdk.plugin.TypedEndpointHandler;

/**
 * An operational endpoint that replays its results (root BRX-RS-HOST-020, MANIFEST-018, MANIFEST-022; idempotency design
 * §5.6, D79 to D84). Its handler runs in no unit of work: the one write through the owner's contract takes the key and
 * keeps the contract's result, and the answer is kept afterwards.
 */
class ContractReplayTest {

    private static final String PATH = "/api/platform/things";
    private static final String OWNER = "things-owner";
    private static final Map<String, Object> LAMP = Map.of("name", "lamp");
    private static final OwnerEndpointIdempotency.Key KEY = new OwnerEndpointIdempotency.Key(
        "ops-module/ops.things.create", "platform|u-1", "k-1");

    public record CreateThing(String name) {
    }

    public record Thing(String id, String name) {
    }

    /** The owner's internal contract. */
    public interface Things {
        Thing create(CreateThing request);

        void remove(CreateThing request);

        List<Thing> split(CreateThing request);

        Thing find(CreateThing request);
    }

    private final Records records = new Records();
    private final OwnerWork ownerWork = new OwnerWork();
    private final Owner owner = new Owner();
    private final Things things = OwnerBoundaryProxies.wrap(Things.class, owner, ownerWork, OWNER,
        Set.of("create", "remove", "split"), new ContractWrites(() -> records, JsonMapper.builder().build()));
    private final List<String> outcomes = new ArrayList<>();
    private final EndpointMetrics metrics = new EndpointMetrics() {
        @Override
        public void finished(String pluginId, String endpointId, String outcome, Duration took) {
            outcomes.add(outcome);
        }
    };
    /** What the handler does with the contract; by default one write. */
    private Function<CreateThing, Object> work = request -> things.create(request);
    /** What fails after the owner committed, as platform-admin's audit can. */
    private RuntimeException afterTheWrite;
    private int handled;

    final class Handler implements TypedEndpointHandler<CreateThing, Object> {
        @Override
        public CompletionStage<Object> handle(EndpointInvocation<CreateThing> invocation) {
            handled++;
            Object result = work.apply(invocation.body());
            if (afterTheWrite != null) {
                throw afterTheWrite;
            }
            return CompletableFuture.completedFuture(result);
        }
    }

    @Test
    void theWriteTakesTheKeyAndTheAnswerIsKeptAfterIt() {
        EndpointResponse answer = (EndpointResponse) call("k-1");

        assertEquals(201, answer.status());
        assertEquals("t-1", ((JsonNode) answer.body()).get("id").asString());
        OwnerEndpointIdempotency.Held kept = records.committed.get(KEY);
        assertEquals("{\"id\":\"t-1\",\"name\":\"lamp\"}", kept.writeResult());
        assertEquals(201, kept.status());
        assertEquals("{\"id\":\"t-1\",\"name\":\"lamp\"}", kept.body());
        assertEquals(OWNER, records.ownerId);
        assertEquals(Duration.ofHours(24), records.retention);
        assertEquals(List.of("ok"), outcomes);
    }

    @Test
    void aRepeatedCallGetsTheAnswerBeforeTheHandler() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher();
        call(dispatcher, LAMP, "k-1");

        EndpointReplay.Replayed again = assertInstanceOf(EndpointReplay.Replayed.class, call(dispatcher, LAMP, "k-1"));

        assertEquals(201, again.status());
        assertEquals("t-1", ((JsonNode) again.body()).get("id").asString());
        assertEquals(1, handled);
        assertEquals(1, owner.writes);
        assertEquals(List.of("ok", "replayed"), outcomes);
    }

    @Test
    void otherContentUnderAKeyIsRefusedBeforeTheHandler() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher();
        call(dispatcher, LAMP, "k-1");

        EndpointHandlingException refused = assertThrows(EndpointHandlingException.class,
            () -> call(dispatcher, Map.of("name", "desk"), "k-1"));

        assertEquals(422, refused.status());
        assertEquals("endpoint.idempotency_conflict", refused.errorCode());
        assertEquals(1, handled);
        assertEquals(1, owner.writes);
    }

    @Test
    void aCallWithoutAKeyIsRefusedBeforeTheHandler() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher();

        EndpointHandlingException refused = assertThrows(EndpointHandlingException.class,
            () -> call(dispatcher, LAMP, null));

        assertEquals(400, refused.status());
        assertEquals(0, handled);
    }

    @Test
    void aCallThatStoppedAfterTheOwnerCommittedGoesOnWithoutWritingAgain() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher();
        afterTheWrite = new EndpointHandlingException(503, "PLATFORM_AUDIT_WRITE_FAILED", "not recorded");
        assertThrows(EndpointHandlingException.class, () -> call(dispatcher, LAMP, "k-1"));
        assertEquals(1, owner.writes);
        assertNull(records.committed.get(KEY).status());

        afterTheWrite = null;
        EndpointReplay.Replayed again = assertInstanceOf(EndpointReplay.Replayed.class, call(dispatcher, LAMP, "k-1"));

        assertEquals(201, again.status());
        assertEquals("t-1", ((JsonNode) again.body()).get("id").asString());
        assertEquals(2, handled);
        assertEquals(1, owner.writes);
        assertEquals(201, records.committed.get(KEY).status());
        assertEquals("replayed", outcomes.get(outcomes.size() - 1));
    }

    @Test
    void aCallWhoseHandlerAnswersAfterTheDeadlineIs504AndARepeatGoesOnWithoutWritingAgain() {
        // Standard-link plan queue item 6 (D122): the owner committed its write in time; the late answer is not given.
        DefaultPluginEndpointDispatcher dispatcher = dispatcher(201, Duration.ofMillis(200));
        work = request -> {
            Object result = things.create(request);
            pause(Duration.ofMillis(300));
            return result;
        };
        assertThrows(EndpointDeadlineExceededException.class, () -> call(dispatcher, LAMP, "k-1"));
        assertEquals(1, owner.writes);
        assertNull(records.committed.get(KEY).status());
        assertEquals(List.of("deadline_exceeded"), outcomes);

        work = request -> things.create(request);
        EndpointReplay.Replayed again = assertInstanceOf(EndpointReplay.Replayed.class, call(dispatcher, LAMP, "k-1"));

        assertEquals(201, again.status());
        assertEquals("t-1", ((JsonNode) again.body()).get("id").asString());
        assertEquals(2, handled);
        assertEquals(1, owner.writes);
    }

    @Test
    void aCallAfterThatGetsTheKeptAnswerBeforeTheHandler() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher();
        afterTheWrite = new EndpointHandlingException(503, "PLATFORM_AUDIT_WRITE_FAILED", "not recorded");
        assertThrows(EndpointHandlingException.class, () -> call(dispatcher, LAMP, "k-1"));
        afterTheWrite = null;
        call(dispatcher, LAMP, "k-1");

        assertInstanceOf(EndpointReplay.Replayed.class, call(dispatcher, LAMP, "k-1"));

        assertEquals(2, handled);
    }

    @Test
    void otherContentAfterAStoppedCallIsRefusedBeforeTheHandler() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher();
        afterTheWrite = new EndpointHandlingException(503, "PLATFORM_AUDIT_WRITE_FAILED", "not recorded");
        assertThrows(EndpointHandlingException.class, () -> call(dispatcher, LAMP, "k-1"));
        afterTheWrite = null;

        EndpointHandlingException refused = assertThrows(EndpointHandlingException.class,
            () -> call(dispatcher, Map.of("name", "desk"), "k-1"));

        assertEquals(422, refused.status());
        assertEquals(1, handled);
    }

    @Test
    void aCallTheOwnerRefusesLeavesNoKey() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher();
        owner.refusal = new IllegalStateException("refused");
        assertThrows(RuntimeException.class, () -> call(dispatcher, LAMP, "k-1"));
        assertTrue(records.committed.isEmpty());

        owner.refusal = null;
        EndpointResponse answer = (EndpointResponse) call(dispatcher, LAMP, "k-1");

        assertEquals(201, answer.status());
        assertEquals(1, owner.writes);
    }

    @Test
    void anAnswerThatCannotBeKeptStillGoesBack() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher();
        records.answersFail = true;

        EndpointResponse answer = (EndpointResponse) call(dispatcher, LAMP, "k-1");

        assertEquals(201, answer.status());
        assertNull(records.committed.get(KEY).status());

        // The next call with the key goes on from the owner's result.
        records.answersFail = false;
        assertInstanceOf(EndpointReplay.Replayed.class, call(dispatcher, LAMP, "k-1"));
        assertEquals(1, owner.writes);
        assertEquals(2, handled);
    }

    @Test
    void aCallWhileAnotherRunsIsToldAndTheOwnerDoesNotWrite() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher();
        records.anotherCallRunning = true;

        EndpointHandlingException refused = assertThrows(EndpointHandlingException.class,
            () -> call(dispatcher, LAMP, "k-1"));

        assertEquals(409, refused.status());
        assertEquals("endpoint.idempotency_in_progress", refused.errorCode());
        assertEquals(0, owner.writes);
    }

    @Test
    void twoCallsThatBothMissTheCheckAreToldApartInTheOwnersTransaction() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher();
        call(dispatcher, LAMP, "k-1");
        // The second passed the check before the first committed, so the owner's transaction finds the key held.
        records.findsNothing = true;

        Object second = call(dispatcher, LAMP, "k-1");

        assertInstanceOf(EndpointReplay.Replayed.class, second);
        assertEquals(1, owner.writes);
    }

    @Test
    void otherContentThatMissedTheCheckIsRefusedInTheOwnersTransaction() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher();
        call(dispatcher, LAMP, "k-1");
        records.findsNothing = true;

        EndpointHandlingException refused = assertThrows(EndpointHandlingException.class,
            () -> call(dispatcher, Map.of("name", "desk"), "k-1"));

        assertEquals(422, refused.status());
        assertEquals(1, owner.writes);
    }

    @Test
    void aSecondWriteInOneCallIsADefect() {
        work = request -> {
            things.create(request);
            return things.create(request);
        };

        assertThrows(IllegalStateException.class, () -> call("k-1"));

        assertEquals(1, owner.writes);
    }

    @Test
    void aCallThatNeverReachesItsWriteKeepsNothingAndStillAnswers() {
        work = request -> new Thing("t-0", request.name());

        EndpointResponse answer = (EndpointResponse) call("k-1");

        assertEquals(201, answer.status());
        assertTrue(records.committed.isEmpty());
    }

    @Test
    void aReadOfTheContractTakesNoKey() {
        work = request -> {
            Thing found = things.find(request);
            assertNull(records.open.get(KEY));
            assertEquals(0, owner.writes);
            return found == null ? things.create(request) : found;
        };

        call("k-1");

        assertNotNull(records.committed.get(KEY));
    }

    @Test
    void aWriteOutsideACallThatReplaysItsResultsTakesNoKey() {
        assertEquals("t-1", things.create(new CreateThing("lamp")).id());

        assertTrue(records.committed.isEmpty());
    }

    @Test
    void aWriteOfAnotherOwnerTakesNoKey() {
        Owner other = new Owner();
        Things elsewhere = OwnerBoundaryProxies.wrap(Things.class, other, ownerWork, "other-owner", Set.of("create"),
            new ContractWrites(() -> records, JsonMapper.builder().build()));
        work = request -> {
            elsewhere.create(request);
            return things.create(request);
        };

        call("k-1");

        assertEquals(OWNER, records.ownerId);
        assertEquals(1, other.writes);
        assertEquals(1, owner.writes);
    }

    @Test
    void aWriteWithoutAResultIsGivenAgainAsNothing() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher(204);
        work = request -> {
            things.remove(request);
            return null;
        };
        afterTheWrite = new EndpointHandlingException(503, "PLATFORM_AUDIT_WRITE_FAILED", "not recorded");
        assertThrows(EndpointHandlingException.class, () -> call(dispatcher, LAMP, "k-1"));
        afterTheWrite = null;
        assertNull(records.committed.get(KEY).writeResult());

        EndpointReplay.Replayed again = assertInstanceOf(EndpointReplay.Replayed.class, call(dispatcher, LAMP, "k-1"));

        assertEquals(204, again.status());
        assertNull(again.body());
        assertEquals(1, owner.writes);

        assertEquals(204, assertInstanceOf(EndpointReplay.Replayed.class, call(dispatcher, LAMP, "k-1")).status());
        assertEquals(2, handled);
    }

    @Test
    void aResultIsReadBackAsTheContractDeclaresIt() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher();
        List<Object> results = new ArrayList<>();
        work = request -> {
            List<Thing> split = things.split(request);
            results.add(split);
            assertEquals("lamp", split.get(0).name());
            return split.get(0);
        };
        afterTheWrite = new EndpointHandlingException(503, "PLATFORM_AUDIT_WRITE_FAILED", "not recorded");
        assertThrows(EndpointHandlingException.class, () -> call(dispatcher, LAMP, "k-1"));
        afterTheWrite = null;

        call(dispatcher, LAMP, "k-1");

        assertEquals(results.get(0), results.get(1));
        assertEquals(1, owner.writes);
    }

    @Test
    void aResultOverTheLimitRollsTheWriteBack() {
        owner.name = "x".repeat(EndpointReplay.MAX_BODY_BYTES);

        assertThrows(IllegalStateException.class, () -> call("k-1"));

        assertTrue(records.committed.isEmpty());
    }

    @Test
    void anAnswerOverTheLimitGoesBackAndIsNotKept() {
        DefaultPluginEndpointDispatcher dispatcher = dispatcher();
        // The write's result is small; the answer the handler makes of it is not.
        work = request -> {
            Thing written = things.create(request);
            return new Thing(written.id(), "x".repeat(EndpointReplay.MAX_BODY_BYTES));
        };

        EndpointResponse answer = (EndpointResponse) call(dispatcher, LAMP, "k-1");

        assertEquals(201, answer.status());
        assertNull(records.committed.get(KEY).status());
        assertEquals(1, owner.writes);
    }

    private Object call(String key) {
        return call(dispatcher(), LAMP, key);
    }

    private Object call(DefaultPluginEndpointDispatcher dispatcher, Map<String, Object> body, String key) {
        Map<String, List<String>> headers = new HashMap<>();
        headers.put("x-actor-id", List.of("u-1"));
        headers.put("x-auth-permission", List.of("things:create"));
        if (key != null) {
            headers.put("Idempotency-Key", List.of(key));
        }
        return dispatcher.invoke("POST", PATH, body, Map.of(), headers);
    }

    private DefaultPluginEndpointDispatcher dispatcher() {
        return dispatcher(201);
    }

    private DefaultPluginEndpointDispatcher dispatcher(int status) {
        return dispatcher(status, Duration.ofSeconds(5));
    }

    private DefaultPluginEndpointDispatcher dispatcher(int status, Duration deadline) {
        EndpointContract contract = new EndpointContract("", "", status, null, new EndpointContract.Replay(
            "ops.things.create", Duration.ofHours(24), "test.things", OWNER));
        DefaultPluginEndpointDispatcher dispatcher = new DefaultPluginEndpointDispatcher(
            EndpointPolicies.Limits.defaults(deadline), EndpointInvocationBoundary.direct(),
            JsonMapper.builder().build(), EndpointTracing.NOOP, metrics, () -> records);
        dispatcher.replaceSnapshot(List.of(new EndpointRoute("ops-module", "ops.things.create.v1", "POST", PATH,
            TestEndpointAccessPolicies.authenticated("things:create"), new Handler(), contract)));
        return dispatcher;
    }

    private static void pause(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    /** The owner's side of the contract. */
    private final class Owner implements Things {

        private int writes;
        private RuntimeException refusal;
        private String name;

        @Override
        public Thing create(CreateThing request) {
            if (refusal != null) {
                throw refusal;
            }
            writes++;
            return new Thing("t-" + writes, name == null ? request.name() : name);
        }

        @Override
        public void remove(CreateThing request) {
            writes++;
        }

        @Override
        public List<Thing> split(CreateThing request) {
            writes++;
            return List.of(new Thing("t-" + writes, request.name()));
        }

        @Override
        public Thing find(CreateThing request) {
            return null;
        }
    }

    /** The owner's unit of work: what it wrote to the records commits when it returns, and goes when it throws. */
    private final class OwnerWork implements UnitOfWork {

        @Override
        public <T> T required(Supplier<T> work) {
            try {
                T result = work.get();
                records.committed.putAll(records.open);
                return result;
            } finally {
                records.open.clear();
            }
        }

        @Override
        public <T> T readOnly(Supplier<T> work) {
            return work.get();
        }
    }

    /** The owner's table, with what the open unit of work has written apart from what committed. */
    private static final class Records implements OwnerEndpointIdempotency {

        private final Map<Key, Held> committed = new HashMap<>();
        private final Map<Key, Held> open = new HashMap<>();
        private boolean anotherCallRunning;
        private boolean answersFail;
        private boolean findsNothing;
        private String ownerId;
        private Duration retention;

        @Override
        public Reservation reserve(String owner, Key key, String fingerprint, Long tenant, String traceId,
                Duration kept) {
            ownerId = owner;
            retention = kept;
            if (anotherCallRunning) {
                return Reservation.IN_PROGRESS;
            }
            Held held = committed.get(key);
            if (held != null) {
                return Reservation.held(held);
            }
            open.put(key, new Held(fingerprint, null, null, traceId, null));
            return Reservation.TAKEN;
        }

        @Override
        public void keepWriteResult(String owner, Key key, String result) {
            Held taken = open.get(key);
            open.put(key, new Held(taken.fingerprint(), null, null, taken.traceId(), result));
        }

        @Override
        public void complete(String owner, Key key, int status, String body) {
            if (answersFail) {
                throw new IllegalStateException("the database went away");
            }
            Held taken = committed.get(key);
            committed.put(key, new Held(taken.fingerprint(), status, body, taken.traceId(), taken.writeResult()));
        }

        @Override
        public Optional<Held> find(String owner, Key key) {
            return findsNothing ? Optional.empty() : Optional.ofNullable(committed.get(key));
        }
    }
}
