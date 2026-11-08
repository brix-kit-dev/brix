/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;

import io.runtime.orchestrator.command.CommandSender;
import io.runtime.orchestrator.command.PluginCommandCapability;
import io.runtime.orchestrator.invocation.RuntimeInvocation;
import io.runtime.orchestrator.invocation.RuntimeInvocations;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.persistence.RecordingPersistence;
import io.runtime.orchestrator.persistence.TenantScope;
import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.sdk.capability.CommandCapability;
import io.runtime.sdk.capability.CommandReceipt;
import io.runtime.sdk.capability.CommandSubmission;
import io.runtime.sdk.command.CommandClient;
import io.runtime.sdk.command.CommandRequest;
import io.runtime.sdk.command.CommandType;
import io.runtime.sdk.plugin.ExecutionIdentity;
import io.runtime.sdk.plugin.InvocationScope;
import io.runtime.sdk.plugin.ManagedTask;
import io.runtime.sdk.plugin.TaskInvocation;

/**
 * The runtime's task scheduler (root BRX-RS-MANIFEST-025, MANIFEST-028, DATA-028; standard-link plan L6.3 to L6.5).
 */
class RuntimeTaskSchedulerTest {

    private static final String MODULE = "sample-orders";
    private static final Instant DUE = Instant.parse("2026-10-31T09:00:00Z");

    private final RecordingPersistence persistence = new RecordingPersistence();
    private final Claims claims = new Claims();
    private final List<TaskInvocation> runs = new CopyOnWriteArrayList<>();
    private final List<RuntimeInvocation> callers = new CopyOnWriteArrayList<>();
    private final List<String> skipped = new CopyOnWriteArrayList<>();
    private final AtomicInteger failuresLeft = new AtomicInteger();
    private volatile Duration sleep = Duration.ZERO;
    private volatile CountDownLatch release;
    private final AtomicBoolean cancellationSeen = new AtomicBoolean();
    private final List<TaskProvider> started = new CopyOnWriteArrayList<>();

    private final ManagedTask task = new ManagedTask() {
        @Override
        public CompletionStage<Void> execute(TaskInvocation invocation) {
            runs.add(invocation);
            RuntimeInvocations.current().ifPresent(callers::add);
            if (release != null) {
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            }
            if (!sleep.isZero()) {
                try {
                    Thread.sleep(sleep.toMillis());
                } catch (InterruptedException interrupted) {
                    cancellationSeen.set(invocation.cancellation().isCancelled());
                    Thread.currentThread().interrupt();
                }
            }
            if (failuresLeft.getAndDecrement() > 0) {
                return CompletableFuture.failedFuture(new IllegalStateException("the warehouse did not answer"));
            }
            return CompletableFuture.completedFuture(null);
        }
    };

    @Test
    void aClaimedTriggerRunsOnceInTheModulesUnitOfWorkInThePlatformScopeAsTheModule() {
        TaskProvider heartbeat = provide(task(TaskPolicies.RETRY_NONE, TaskPolicies.IDEMPOTENCY_TRIGGER, null));

        assertEquals(Optional.of(TaskRunClaims.Outcome.SUCCEEDED), scheduler("host-a").runNow(heartbeat, DUE));

        TaskInvocation invocation = runs.get(0);
        assertEquals("sample-orders.heartbeat", invocation.taskId());
        assertEquals(DUE.toString(), invocation.triggerId());
        assertEquals(DUE, invocation.scheduledAt());
        assertEquals(ExecutionIdentity.service(MODULE), invocation.executionIdentity());
        assertEquals(InvocationScope.platform(), invocation.scope());
        assertEquals(1, invocation.attempt());
        assertEquals(DUE.toString(), invocation.idempotency().key());
        assertEquals(List.of("required"), persistence.modes());
        assertEquals(List.of(TenantScope.none()), persistence.scopes());
        RuntimeInvocation caller = callers.get(0);
        assertEquals(RuntimeInvocation.Kind.TASK, caller.kind());
        assertEquals(MODULE, caller.caller().pluginId());
        assertEquals(InvocationScope.platform(), caller.scope());
        assertEquals("host-a|SUCCEEDED|1|null", claims.finished.get(MODULE + "|sample-orders.heartbeat|" + DUE));
    }

    @Test
    void aTriggerAnotherHostClaimedIsNotRunHere() {
        TaskProvider heartbeat = provide(task(TaskPolicies.RETRY_NONE, TaskPolicies.IDEMPOTENCY_TRIGGER, null));
        RuntimeTaskScheduler first = scheduler("host-a");
        RuntimeTaskScheduler second = scheduler("host-b");

        assertTrue(first.runNow(heartbeat, DUE).isPresent());
        assertEquals(Optional.empty(), second.runNow(heartbeat, DUE));
        assertTrue(second.runNow(heartbeat, DUE.plusSeconds(3600)).isPresent());

        assertEquals(2, runs.size());
        assertEquals(List.of("claimed"), skipped);
    }

    @Test
    void anInstanceLocalTaskRunsOnEveryHostWithoutAClaim() {
        TaskProvider refresh = provide(task(TaskPolicies.RETRY_NONE, TaskPolicies.IDEMPOTENCY_LOCAL, null));

        scheduler("host-a").runNow(refresh, DUE);
        scheduler("host-b").runNow(refresh, DUE);

        assertEquals(2, runs.size());
        assertTrue(claims.claimed.isEmpty());
    }

    @Test
    void aTriggerDueWhileThePreviousRunIsRunningIsSkipped() throws Exception {
        TaskProvider heartbeat = provide(task(TaskPolicies.RETRY_NONE, TaskPolicies.IDEMPOTENCY_LOCAL, null));
        RuntimeTaskScheduler scheduler = scheduler("host-a");
        release = new CountDownLatch(1);
        Thread first = new Thread(() -> scheduler.runNow(heartbeat, DUE));
        first.start();
        await(() -> runs.size() == 1);

        assertEquals(Optional.empty(), scheduler.runNow(heartbeat, DUE.plusSeconds(3600)));

        release.countDown();
        first.join(5_000);
        assertEquals(List.of("overlap"), skipped);
        assertEquals(1, runs.size());
    }

    @Test
    void aFailedRunIsTriedAgainForTheSameTriggerWithTransientRetry() {
        TaskProvider heartbeat = provide(task(TaskPolicies.RETRY_TRANSIENT, TaskPolicies.IDEMPOTENCY_TRIGGER, null));
        failuresLeft.set(2);

        assertEquals(Optional.of(TaskRunClaims.Outcome.SUCCEEDED), scheduler("host-a").runNow(heartbeat, DUE));

        assertEquals(List.of(1, 2, 3), runs.stream().map(TaskInvocation::attempt).toList());
        assertEquals(1, runs.stream().map(TaskInvocation::triggerId).distinct().count());
        assertEquals("host-a|SUCCEEDED|3|null", claims.finished.get(MODULE + "|sample-orders.heartbeat|" + DUE));
    }

    @Test
    void withoutRetryAFailedRunFailsAfterOneAttempt() {
        TaskProvider heartbeat = provide(task(TaskPolicies.RETRY_NONE, TaskPolicies.IDEMPOTENCY_TRIGGER, null));
        failuresLeft.set(5);

        assertEquals(Optional.of(TaskRunClaims.Outcome.FAILED), scheduler("host-a").runNow(heartbeat, DUE));

        assertEquals(1, runs.size());
        assertEquals("host-a|FAILED|1|" + RuntimeTaskScheduler.FAILED,
            claims.finished.get(MODULE + "|sample-orders.heartbeat|" + DUE));
    }

    @Test
    void aRunPastItsDeadlineIsCancelledAndFails() {
        TaskProvider heartbeat = provide(task(TaskPolicies.RETRY_NONE, TaskPolicies.IDEMPOTENCY_TRIGGER, null));
        sleep = Duration.ofSeconds(5);
        TaskProperties settings = settings("host-a");
        settings.setTimeout(Duration.ofMillis(300));
        long started = System.nanoTime();

        assertEquals(Optional.of(TaskRunClaims.Outcome.FAILED), scheduler(settings).runNow(heartbeat, DUE));

        assertTrue(Duration.ofNanos(System.nanoTime() - started).compareTo(Duration.ofSeconds(3)) < 0);
        assertTrue(cancellationSeen.get(), "the task saw its cancellation signal");
        assertEquals("host-a|FAILED|1|" + RuntimeTaskScheduler.DEADLINE_EXCEEDED,
            claims.finished.get(MODULE + "|sample-orders.heartbeat|" + DUE));
        assertFalse(Thread.currentThread().isInterrupted());
    }

    @Test
    void aTaskRunsInTheUnitOfWorkItDeclares() {
        scheduler("host-a").runNow(provide(task(TaskPolicies.RETRY_NONE, TaskPolicies.IDEMPOTENCY_LOCAL,
            PluginRuntimeDescriptor.ProvidedTask.TRANSACTION_NONE)), DUE);
        assertEquals(List.of(), persistence.modes());

        started.clear();
        scheduler("host-a").runNow(provide(task(TaskPolicies.RETRY_NONE, TaskPolicies.IDEMPOTENCY_LOCAL,
            PluginRuntimeDescriptor.ProvidedTask.TRANSACTION_READ_ONLY)), DUE);
        assertEquals(List.of("readOnly"), persistence.modes());
    }

    @Test
    void theTaskOfAModuleThatStoppedIsNotRun() {
        TaskProvider heartbeat = provide(task(TaskPolicies.RETRY_NONE, TaskPolicies.IDEMPOTENCY_TRIGGER, null));
        RuntimeTaskScheduler scheduler = scheduler("host-a");
        started.clear();

        assertEquals(Optional.empty(), scheduler.runNow(heartbeat, DUE));

        assertEquals(List.of("not_started"), skipped);
        assertTrue(claims.claimed.isEmpty());
    }

    @Test
    void aTaskMoreFrequentThanTheHostAllowsIsRefused() {
        TaskProperties settings = settings("host-a");
        settings.setMinInterval(Duration.ofHours(2));

        assertThrows(IllegalArgumentException.class, () -> scheduler(settings)
            .runNow(provide(task(TaskPolicies.RETRY_NONE, TaskPolicies.IDEMPOTENCY_LOCAL, null)), DUE));
    }

    @Test
    void triggersRunOnTheirScheduleOnceTheHostIsReadyAndStopWithIt() throws Exception {
        provide(new PluginRuntimeDescriptor.ProvidedTask("sample-orders.heartbeat", "sample-orders.heartbeat.v1",
            TaskPolicies.SCOPE_PLATFORM, TaskPolicies.IDENTITY_SERVICE, "every:PT1S", TaskPolicies.TIMEOUT_STANDARD,
            TaskPolicies.CONCURRENCY_NO_OVERLAP, TaskPolicies.RETRY_NONE, TaskPolicies.IDEMPOTENCY_TRIGGER, null));
        AtomicBoolean ready = new AtomicBoolean();
        RuntimeTaskScheduler scheduler = new RuntimeTaskScheduler(settings("host-a"), () -> providers(ready::get),
            owners(), () -> claims, new Recorder(), Clock.systemUTC());

        scheduler.start();
        Thread.sleep(1_300);
        assertTrue(runs.isEmpty(), "nothing runs before the host's entries are published");
        ready.set(true);
        await(() -> runs.size() >= 2);
        scheduler.stop();
        int ran = runs.size();
        Thread.sleep(1_500);

        assertEquals(ran, runs.size(), "nothing runs after the host stopped");
        assertFalse(scheduler.isRunning());
        assertTrue(runs.stream().allMatch(run -> Instant.parse(run.triggerId()).toEpochMilli() % 1_000 == 0));
    }

    @Test
    void aTaskSendsACommandAsAPlatformCommandOfItsModule() {
        // Standard-link plan L6.5: the run's invocation is bound, so a task's clients act for the module, in the
        // platform scope.
        List<CommandRequest<?>> sent = new CopyOnWriteArrayList<>();
        CommandType<Cancel> cancel = CommandType.of("sample-shipping.recall", "1.0.0", Cancel.class);
        CommandClient<Cancel> recalls = new PluginCommandCapability(MODULE, new Bus(sent),
            Map.of(cancel.commandType(), new PluginRuntimeDescriptor.ConsumedCommand(cancel.commandType(),
                ">=1.0.0 <2.0.0", false)), null).client(cancel);
        TaskProvider sender = new TaskProvider(MODULE, task(TaskPolicies.RETRY_NONE, TaskPolicies.IDEMPOTENCY_LOCAL, null),
            new ManagedTask() {
                @Override
                public CompletionStage<Void> execute(TaskInvocation invocation) {
                    recalls.submit(new Cancel("o-7"), "o-7");
                    return CompletableFuture.completedFuture(null);
                }
            });
        started.add(sender);

        assertEquals(Optional.of(TaskRunClaims.Outcome.SUCCEEDED), scheduler("host-a").runNow(sender, DUE));

        assertEquals(1, sent.size());
        assertEquals(null, sent.get(0).tenantId());
        assertEquals("sample-shipping.recall", sent.get(0).commandType());
    }

    record Cancel(String orderId) {
    }

    /** An outbox that records what it is given. */
    private record Bus(List<CommandRequest<?>> sent) implements CommandCapability, CommandSender {

        @Override
        @SuppressWarnings("removal")
        public <C> CommandSubmission submit(CommandRequest<C> request) {
            return send(request, "1.0.0", null);
        }

        @Override
        public CommandSubmission send(CommandRequest<?> request, String schemaVersion, String actorId) {
            sent.add(request);
            return new CommandSubmission("c-1", CompletableFuture.completedFuture(new CommandReceipt("c-1",
                request.commandType(), Instant.EPOCH, CommandReceipt.Status.QUEUED)));
        }
    }

    private TaskProvider provide(PluginRuntimeDescriptor.ProvidedTask declaration) {
        TaskProvider provider = new TaskProvider(MODULE, declaration, task);
        started.add(provider);
        return provider;
    }

    private static PluginRuntimeDescriptor.ProvidedTask task(String retry, String idempotency, String transaction) {
        return new PluginRuntimeDescriptor.ProvidedTask("sample-orders.heartbeat", "sample-orders.heartbeat.v1",
            TaskPolicies.SCOPE_PLATFORM, TaskPolicies.IDENTITY_SERVICE, "every:PT1H", TaskPolicies.TIMEOUT_STANDARD,
            TaskPolicies.CONCURRENCY_NO_OVERLAP, retry, idempotency, transaction);
    }

    private RuntimeTaskScheduler scheduler(String instanceId) {
        return scheduler(settings(instanceId));
    }

    private RuntimeTaskScheduler scheduler(TaskProperties settings) {
        return new RuntimeTaskScheduler(settings, () -> providers(() -> true), owners(), () -> claims, new Recorder(),
            Clock.systemUTC());
    }

    private static TaskProperties settings(String instanceId) {
        TaskProperties settings = new TaskProperties();
        settings.setInstanceId(instanceId);
        settings.setMinInterval(Duration.ofSeconds(1));
        settings.setTimeout(Duration.ofSeconds(2));
        settings.setBackoff(Duration.ofMillis(10));
        settings.setDrainTimeout(Duration.ofSeconds(2));
        return settings;
    }

    private TaskProviders providers(BooleanSupplier ready) {
        return new TaskProviders() {
            @Override
            public boolean ready() {
                return ready.getAsBoolean();
            }

            @Override
            public List<TaskProvider> tasks() {
                return List.copyOf(started);
            }
        };
    }

    private OwnerPersistenceProvider owners() {
        return owner -> MODULE.equals(owner) ? Optional.of(persistence) : Optional.empty();
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                fail("timed out");
            }
            Thread.sleep(20);
        }
    }

    /** Claims shared by the hosts of a test, as a task-run table is. */
    private static final class Claims implements TaskRunClaims {

        final Map<String, String> claimed = new ConcurrentHashMap<>();
        final Map<String, String> finished = new ConcurrentHashMap<>();

        @Override
        public boolean claim(String moduleId, String taskId, String triggerId, Instant scheduledAt, String instanceId) {
            return claimed.putIfAbsent(moduleId + "|" + taskId + "|" + triggerId, instanceId) == null;
        }

        @Override
        public void finish(String moduleId, String taskId, String triggerId, Outcome outcome, int attempts,
                String errorCode) {
            String key = moduleId + "|" + taskId + "|" + triggerId;
            finished.put(key, claimed.get(key) + "|" + outcome + "|" + attempts + "|" + errorCode);
        }

        @Override
        public List<String> tables() {
            return List.of();
        }

        @Override
        public int purge(String table, OffsetDateTime finishedBefore, int limit) {
            return 0;
        }
    }

    private final class Recorder implements TaskMetrics {

        @Override
        public void skipped(String moduleId, String taskId, String reason) {
            skipped.add(reason);
        }
    }
}
