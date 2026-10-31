/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.runtime.orchestrator.task;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.SmartLifecycle;

import io.runtime.orchestrator.invocation.RuntimeInvocation;
import io.runtime.orchestrator.invocation.RuntimeInvocations;
import io.runtime.orchestrator.persistence.OwnerPersistenceProvider;
import io.runtime.orchestrator.persistence.TenantScope;
import io.runtime.orchestrator.persistence.TenantScopes;
import io.runtime.orchestrator.plugin.PluginRuntimeDescriptor;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.error.BrixException;
import io.runtime.sdk.plugin.AccessDecision;
import io.runtime.sdk.plugin.ExecutionIdentity;
import io.runtime.sdk.plugin.IdempotencyContext;
import io.runtime.sdk.plugin.InvocationScope;
import io.runtime.sdk.plugin.TaskInvocation;
import io.runtime.sdk.plugin.TraceContext;

/**
 * The runtime's task scheduler (root BRX-RS-MANIFEST-025, MANIFEST-026, MANIFEST-029, BOOT-007, DATA-028; standard-link
 * plan L6.3 to L6.6, D57).
 *
 * <p>It starts triggering once the host's entries are published and stops with the host: first no trigger starts any
 * more, then the runs in flight have the drain timeout to end, then they are cancelled. Each declared task is due as
 * its trigger says, and a due trigger is scheduled before it runs, so a slow run does not move the schedule.</p>
 *
 * <p>A trigger runs at most once at a time per task on this host ({@code task-no-overlap}); with {@code task-trigger},
 * at most one host runs it, the one whose claim in the module's task-run table commits first. A run is the task's
 * attempts: each in the module's unit of work ({@code transaction}), in the platform scope, as the module, with its
 * own deadline, at which the runtime sets the run's cancellation signal and interrupts it. A failed attempt is tried
 * again with {@code task-transient-retry}. How the run ended is logged, measured and, for a claimed trigger,
 * recorded.</p>
 *
 * @author Runtime SDK Team
 * @since 3.3.0
 */
public final class RuntimeTaskScheduler implements SmartLifecycle {

    /** Rows per statement when the task-run tables are cleaned. */
    static final int PURGE_BATCH = 1000;
    /** Statements per table and cleaning pass. */
    static final int PURGE_BATCHES = 100;
    /** What a run ran past its deadline is recorded as. */
    static final String DEADLINE_EXCEEDED = "task.deadline_exceeded";
    /** What a failure without a stable code is recorded as. */
    static final String FAILED = "task.failed";

    private static final Logger log = LoggerFactory.getLogger(RuntimeTaskScheduler.class);

    private final TaskProperties settings;
    private final Supplier<TaskProviders> providers;
    private final OwnerPersistenceProvider owners;
    private final Supplier<TaskRunClaims> claims;
    private final TaskMetrics metrics;
    private final Clock clock;
    private final String instanceId;
    private final Map<String, Scheduled> scheduled = new ConcurrentHashMap<>();
    private final Set<AtomicBoolean> inFlight = ConcurrentHashMap.newKeySet();
    private ScheduledThreadPoolExecutor executor;
    private ScheduledThreadPoolExecutor deadlines;
    private Thread supervisor;
    private volatile TaskProviders current;
    private volatile boolean draining;
    private volatile boolean running;

    /**
     * Creates the scheduler.
     *
     * @param settings {@code brix.tasks.*}
     * @param providers supplies the host's started tasks; may supply {@code null} when the host runs no modules
     * @param owners the modules' persistence, for their units of work
     * @param claims supplies the task-run tables
     * @param metrics where runs are reported
     * @param clock scheduling clock
     */
    public RuntimeTaskScheduler(TaskProperties settings, Supplier<TaskProviders> providers, OwnerPersistenceProvider owners,
            Supplier<TaskRunClaims> claims, TaskMetrics metrics, Clock clock) {
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.providers = Objects.requireNonNull(providers, "providers must not be null");
        this.owners = Objects.requireNonNull(owners, "owners must not be null");
        this.claims = Objects.requireNonNull(claims, "claims must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.instanceId = settings.getInstanceId() == null || settings.getInstanceId().isBlank()
            ? hostName() + "-" + HexFormat.of().formatHex(randomBytes(4))
            : settings.getInstanceId();
        settings.limits();
        if (settings.getThreads() < 1) {
            throw new IllegalStateException("brix.tasks.threads must be at least 1");
        }
    }

    /**
     * Returns this host's name in the task-run tables.
     *
     * @return the instance id
     */
    public String instanceId() {
        return instanceId;
    }

    @Override
    public synchronized void start() {
        if (running || !settings.isEnabled()) {
            return;
        }
        TaskProviders known = providers.get();
        if (known == null) {
            return;
        }
        current = known;
        draining = false;
        executor = executor();
        supervisor = new Thread(this::publishWhenReady, "brix-task-scheduler");
        supervisor.setDaemon(true);
        supervisor.start();
        running = true;
    }

    @Override
    public void stop() {
        ScheduledThreadPoolExecutor stopping;
        Thread waiting;
        synchronized (this) {
            if (!running) {
                return;
            }
            draining = true;
            stopping = executor;
            waiting = supervisor;
        }
        waiting.interrupt();
        stopping.shutdown();
        try {
            if (!stopping.awaitTermination(settings.getDrainTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
                inFlight.forEach(cancelled -> cancelled.set(true));
                stopping.shutdownNow();
                stopping.awaitTermination(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        log.info("event=task.scheduler_stopped tasks={} cancelled={}", scheduled.size(), inFlight.size());
        synchronized (this) {
            deadlines().shutdownNow();
            deadlines = null;
            executor = null;
            scheduled.clear();
            running = false;
        }
    }

    private synchronized ScheduledThreadPoolExecutor executor() {
        if (executor == null) {
            executor = pool(settings.getThreads(), "brix-task-");
            executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
            executor.setRemoveOnCancelPolicy(true);
        }
        return executor;
    }

    /** The deadlines run apart from the tasks, so a deadline passes even while every task thread is busy. */
    private synchronized ScheduledThreadPoolExecutor deadlines() {
        if (deadlines == null) {
            deadlines = pool(1, "brix-task-deadline-");
            deadlines.setRemoveOnCancelPolicy(true);
        }
        return deadlines;
    }

    private static ScheduledThreadPoolExecutor pool(int threads, String prefix) {
        return new ScheduledThreadPoolExecutor(threads, runnable -> {
            Thread thread = new Thread(runnable, prefix + THREADS.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /**
     * Returns the tasks this host schedules.
     *
     * @return {@code moduleId/handlerId} of each
     */
    public synchronized Set<String> scheduledTasks() {
        return Set.copyOf(scheduled.keySet());
    }

    private void publishWhenReady() {
        try {
            while (!current.ready()) {
                Thread.sleep(100);
            }
            publish(current.tasks());
            long interval = settings.getRetentionInterval().toMillis();
            executor().scheduleWithFixedDelay(this::purge, interval, interval, TimeUnit.MILLISECONDS);
        } catch (InterruptedException stopped) {
            Thread.currentThread().interrupt();
        } catch (RejectedExecutionException stopping) {
            // The host stopped while the tasks were published.
        }
    }

    private void publish(List<TaskProvider> tasks) {
        Instant now = clock.instant();
        for (TaskProvider provider : tasks) {
            Optional<Scheduled> task = resolve(provider);
            if (task.isEmpty() || scheduled.putIfAbsent(provider.key(), task.get()) != null) {
                continue;
            }
            Scheduled published = task.get();
            metrics.published(published.moduleId(), published.taskId(), published.policies().trigger().shortestInterval(),
                now);
            log.info("event=task.published module={} task={} trigger={} idempotency={} instance={}",
                published.moduleId(), published.taskId(), published.policies().trigger().reference(),
                published.declaration().idempotencyPolicyRef(), instanceId);
            schedule(published, now);
        }
    }

    private Optional<Scheduled> resolve(TaskProvider provider) {
        try {
            return Optional.of(new Scheduled(provider.moduleId(), provider.declaration(),
                TaskPolicies.resolve(provider.declaration(), settings.limits()), new AtomicBoolean()));
        } catch (IllegalArgumentException refused) {
            log.error("event=task.refused module={} task={} reason={}", provider.moduleId(),
                provider.declaration().taskId(), refused.getMessage());
            return Optional.empty();
        }
    }

    private void schedule(Scheduled task, Instant after) {
        if (draining) {
            return;
        }
        Instant due = task.policies().trigger().next(after);
        long delay = Math.max(0, Duration.between(clock.instant(), due).toMillis());
        try {
            executor().schedule(() -> fire(task, due), delay, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException stopping) {
            // The host is stopping.
        }
    }

    private void fire(Scheduled task, Instant due) {
        if (draining) {
            return;
        }
        schedule(task, due);
        run(task, due);
    }

    /**
     * Runs one trigger of a started task on the calling thread, as a due trigger runs.
     *
     * @param provider the task
     * @param due when the trigger was due
     * @return how the run ended, or empty when this host did not run it
     */
    Optional<TaskRunClaims.Outcome> runNow(TaskProvider provider, Instant due) {
        Scheduled task = scheduled.computeIfAbsent(provider.key(), key -> resolve(provider)
            .orElseThrow(() -> new IllegalArgumentException(provider.key() + " has policies this host refuses")));
        current = current != null ? current : providers.get();
        return run(task, due);
    }

    private Optional<TaskRunClaims.Outcome> run(Scheduled task, Instant due) {
        String moduleId = task.moduleId();
        String taskId = task.taskId();
        if (!task.running().compareAndSet(false, true)) {
            metrics.skipped(moduleId, taskId, "overlap");
            log.warn("event=task.skipped module={} task={} trigger={} reason=overlap", moduleId, taskId, due);
            return Optional.empty();
        }
        try {
            Optional<TaskProvider> live = live(task);
            if (live.isEmpty()) {
                metrics.skipped(moduleId, taskId, "not_started");
                log.info("event=task.skipped module={} task={} trigger={} reason=not_started", moduleId, taskId, due);
                return Optional.empty();
            }
            String triggerId = due.toString();
            boolean claimed = task.policies().claimed();
            if (claimed) {
                try {
                    if (!claims.get().claim(moduleId, taskId, triggerId, due, instanceId)) {
                        metrics.skipped(moduleId, taskId, "claimed");
                        log.debug("event=task.skipped module={} task={} trigger={} reason=claimed", moduleId, taskId,
                            due);
                        return Optional.empty();
                    }
                } catch (RuntimeException unavailable) {
                    metrics.skipped(moduleId, taskId, "claim_failed");
                    log.warn("event=task.skipped module={} task={} trigger={} reason=claim_failed", moduleId, taskId,
                        due, unavailable);
                    return Optional.empty();
                }
            }
            Instant started = clock.instant();
            metrics.started(moduleId, taskId);
            AtomicBoolean cancelled = new AtomicBoolean();
            inFlight.add(cancelled);
            Result result;
            try {
                result = attempts(task, live.get(), due, triggerId, cancelled);
            } finally {
                inFlight.remove(cancelled);
            }
            Instant ended = clock.instant();
            metrics.finished(moduleId, taskId, result.outcome().name().toLowerCase(Locale.ROOT),
                Duration.between(started, ended), ended);
            if (claimed) {
                try {
                    claims.get().finish(moduleId, taskId, triggerId, result.outcome(), result.attempts(),
                        result.errorCode());
                } catch (RuntimeException unrecorded) {
                    log.warn("event=task.unrecorded module={} task={} trigger={}", moduleId, taskId, triggerId,
                        unrecorded);
                }
            }
            log.info("event=task.run module={} task={} trigger={} outcome={} attempts={} took_ms={} error_code={} "
                + "instance={}", moduleId, taskId, triggerId, result.outcome(), result.attempts(),
                Duration.between(started, ended).toMillis(), result.errorCode(), instanceId);
            return Optional.of(result.outcome());
        } finally {
            task.running().set(false);
        }
    }

    private Optional<TaskProvider> live(Scheduled task) {
        TaskProviders known = current;
        if (known == null) {
            return Optional.empty();
        }
        return known.tasks().stream()
            .filter(provider -> provider.moduleId().equals(task.moduleId())
                && provider.declaration().handlerId().equals(task.declaration().handlerId()))
            .findFirst();
    }

    private Result attempts(Scheduled task, TaskProvider live, Instant due, String triggerId, AtomicBoolean cancelled) {
        int attempts = task.policies().attempts();
        String errorCode = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            Instant deadline = clock.instant().plus(task.policies().timeout());
            AtomicBoolean late = new AtomicBoolean();
            TaskInvocation invocation = new TaskInvocation(task.taskId(), triggerId, due,
                ExecutionIdentity.service(task.moduleId()), InvocationScope.platform(), AccessDecision.none(),
                new TraceContext(newTraceparent(), null), deadline, () -> late.get() || cancelled.get(),
                new IdempotencyContext(task.taskId(), triggerId), attempt);
            try {
                execute(task, live, invocation, late);
                return new Result(TaskRunClaims.Outcome.SUCCEEDED, attempt, null);
            } catch (DeadlineExceeded pastDeadline) {
                errorCode = DEADLINE_EXCEEDED;
                log.warn("event=task.attempt_failed module={} task={} trigger={} attempt={} error_code={}",
                    task.moduleId(), task.taskId(), triggerId, attempt, errorCode);
            } catch (RuntimeException | Error failure) {
                errorCode = failure instanceof BrixException brix ? brix.errorCode().wireCode() : FAILED;
                log.warn("event=task.attempt_failed module={} task={} trigger={} attempt={} error_code={}",
                    task.moduleId(), task.taskId(), triggerId, attempt, errorCode, failure);
            }
            if (draining || cancelled.get() || Thread.currentThread().isInterrupted()) {
                return new Result(TaskRunClaims.Outcome.CANCELLED, attempt, errorCode);
            }
            if (attempt < attempts && !pause(settings.getBackoff().multipliedBy(1L << (attempt - 1)))) {
                return new Result(TaskRunClaims.Outcome.CANCELLED, attempt, errorCode);
            }
        }
        return new Result(TaskRunClaims.Outcome.FAILED, attempts, errorCode);
    }

    /**
     * One attempt: in the module's unit of work, the platform scope and the run's invocation. At the deadline the
     * runtime sets {@code late}, which the invocation's cancellation signal reports, and interrupts the attempt, which
     * then fails and rolls back.
     */
    private void execute(Scheduled task, TaskProvider live, TaskInvocation invocation, AtomicBoolean late) {
        Thread attempt = Thread.currentThread();
        long timeout = Math.max(1, Duration.between(clock.instant(), invocation.deadline()).toMillis());
        ScheduledFuture<?> watchdog = deadlines().schedule(() -> {
            late.set(true);
            attempt.interrupt();
        }, timeout, TimeUnit.MILLISECONDS);
        String traceparent = MDC.get("traceparent");
        MDC.put("traceparent", invocation.trace().traceparent());
        try {
            Supplier<Void> work = () -> {
                await(live.task().execute(invocation), invocation.deadline(), late);
                if (late.get()) {
                    throw new DeadlineExceeded();
                }
                return null;
            };
            TenantScopes.within(TenantScope.none(), () -> RuntimeInvocations.within(
                RuntimeInvocation.forTask(task.moduleId(), invocation), () -> inUnitOfWork(task, work)));
        } catch (RuntimeException failure) {
            throw late.get() ? new DeadlineExceeded() : failure;
        } finally {
            watchdog.cancel(false);
            if (late.get()) {
                Thread.interrupted();
            }
            if (traceparent == null) {
                MDC.remove("traceparent");
            } else {
                MDC.put("traceparent", traceparent);
            }
        }
    }

    private Void inUnitOfWork(Scheduled task, Supplier<Void> work) {
        String transaction = task.policies().transaction();
        if (PluginRuntimeDescriptor.ProvidedTask.TRANSACTION_NONE.equals(transaction)) {
            return work.get();
        }
        PersistenceCapability persistence = owners.forOwner(task.moduleId()).orElseThrow(() ->
            new IllegalStateException(task.moduleId() + " has no persistence unit for task " + task.taskId()));
        return PluginRuntimeDescriptor.ProvidedTask.TRANSACTION_READ_ONLY.equals(transaction)
            ? persistence.unitOfWork().readOnly(work)
            : persistence.unitOfWork().required(work);
    }

    private void await(java.util.concurrent.CompletionStage<Void> stage, Instant deadline, AtomicBoolean late) {
        CompletableFuture<Void> future = stage.toCompletableFuture();
        long remaining = Math.max(1, Duration.between(clock.instant(), deadline).toMillis());
        try {
            future.get(remaining, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | InterruptedException stopped) {
            late.set(true);
            future.cancel(true);
            throw new DeadlineExceeded();
        } catch (ExecutionException failed) {
            Throwable cause = failed.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new CompletionException(cause);
        }
    }

    private boolean pause(Duration backoff) {
        try {
            Thread.sleep(backoff.toMillis());
            return !draining;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void purge() {
        TaskRunClaims store = claims.get();
        OffsetDateTime cutoff = OffsetDateTime.now(clock).minus(settings.getRunRetention());
        for (String table : store.tables()) {
            try {
                int total = 0;
                int deleted;
                int batches = 0;
                do {
                    deleted = store.purge(table, cutoff, PURGE_BATCH);
                    total += deleted;
                } while (deleted == PURGE_BATCH && ++batches < PURGE_BATCHES);
                if (total > 0) {
                    log.info("event=task.retention_pass table={} deleted={}", table, total);
                }
            } catch (RuntimeException failure) {
                log.warn("event=task.retention_failed table={}", table, failure);
            }
        }
    }

    private static String newTraceparent() {
        return "00-" + HexFormat.of().formatHex(randomBytes(16)) + "-" + HexFormat.of().formatHex(randomBytes(8)) + "-01";
    }

    private static byte[] randomBytes(int length) {
        byte[] bytes = new byte[length];
        ThreadLocalRandom.current().nextBytes(bytes);
        bytes[0] |= 1;
        return bytes;
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (java.io.IOException | RuntimeException unknown) {
            return "host";
        }
    }

    private static final AtomicInteger THREADS = new AtomicInteger();

    private record Scheduled(String moduleId, PluginRuntimeDescriptor.ProvidedTask declaration,
            TaskPolicies.Resolved policies, AtomicBoolean running) {

        String taskId() {
            return declaration.taskId();
        }
    }

    private record Result(TaskRunClaims.Outcome outcome, int attempts, String errorCode) {
    }

    /** An attempt ran past its deadline. */
    private static final class DeadlineExceeded extends RuntimeException {

        private static final long serialVersionUID = 1L;

        DeadlineExceeded() {
            super(DEADLINE_EXCEEDED, null, false, false);
        }
    }
}
