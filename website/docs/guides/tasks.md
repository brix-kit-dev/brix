---
id: tasks
title: Tasks
sidebar_label: Tasks
sidebar_position: 4
---

# Tasks

This chapter is for plugin authors. A task is work your plugin runs on a schedule, not in answer to a request or a
message: cleaning up, recomputing, sending a digest. You declare the task in your manifest and bind a `ManagedTask`.
The runtime's scheduler runs it at each trigger:
- on one host, when several run your plugin;
- in your plugin's unit of work;
- with a deadline, retries if you ask for them, and metrics.

A task comes only from your manifest. Do not create schedulers, timers or threads, and do not use Spring
`@Scheduled`, Quartz or `SchedulingCapability`, which is deprecated.

`sample-orders` has a task, `sample-orders.heartbeat`, and `sample-e2e` runs it end to end on two hosts.

## What the platform guarantees

| Topic | Guarantee |
|---|---|
| When | A trigger is due at the same instants on every host. Runs start once the host has published its entries, and stop with the host. |
| Once | With `task-trigger`, each trigger runs on one host: the host whose claim of `(taskId, triggerId)` commits first in your plugin's `<storageId>_task_run` table. The others skip it. |
| Overlap | A trigger that comes due while the previous run is still running on that host is skipped and counted. The next trigger keeps its time. |
| Deadline | A run that passes its deadline is cancelled: its cancellation signal is set and its thread interrupted. It fails, and its unit of work rolls back. |
| Failure | A run that throws fails. With `task-transient-retry` it is tried again for the same trigger, after a backoff that doubles. |
| Stopping | When the host stops, no trigger starts any more. Runs in flight get the drain timeout to end, and are then cancelled. |

## Declaring a task

```yaml title="sample-orders-server/src/main/resources/META-INF/brix/plugin-manifest.yaml"
tasks:
  provides:
    - taskId: sample-orders.heartbeat
      handlerId: sample-orders.heartbeat.v1
      scopePolicyRef: task-platform
      executionIdentityPolicyRef: plugin-service
      triggerPolicyRef: "every:PT2S"
      timeoutPolicyRef: task-standard
      concurrencyPolicyRef: task-no-overlap
      retryPolicyRef: task-no-retry
      idempotencyPolicyRef: task-trigger
      transaction: none
```

The sample's trigger is short for its test. A real task is due far less often, such as `every:PT1H` or
`"cron:0 30 3 * * *"`. Quote a cron expression in YAML.

A task with `task-trigger` needs `data.storageId`. The platform creates `<storageId>_task_run` from its own
migrations. A task that runs in a unit of work needs `PersistenceCapability`. The claims are kept under the `taskId`
and the `storageId`, so keep both once the task runs in production; see
[Identifiers you cannot change after go-live](./events-and-commands.md#identifiers-you-cannot-change-after-go-live).

## Writing the task

```java title="Heartbeat.java"
public final class Heartbeat implements ManagedTask {
    @Override
    public CompletionStage<Void> execute(TaskInvocation invocation) {
        // invocation.triggerId(): the same on every host; invocation.deadline(), invocation.cancellation()
        return CompletableFuture.completedFuture(null);
    }
}
```

```java
bootstrap.bindTaskFactory("sample-orders.heartbeat.v1", context -> new Heartbeat());
```

- `TaskInvocation` carries the task id, the trigger id (the due instant), when the trigger was due, the identity the
  task runs as, the scope, the deadline, the cancellation signal and the attempt.
- Do long work in steps and check `invocation.cancellation().isCancelled()` between them.
- To fail, throw or complete the stage exceptionally.
- A task written before 3.3.0 implements `run()`, which is deprecated. The runtime refuses, at startup, a task that
  implements neither method. `ManagedTask` is no longer a functional interface, so implement it in a class.

A task runs in the platform scope, as your plugin. It reaches your plugin's global tables and no tenant's data. It can
call queries that are valid in the platform scope, publish events and send commands. Tasks that run once per tenant
come later. Until then, a task that must touch tenants' data sends commands or publishes events that run in their
tenants.

## Policies

| Policy | Value | Effect |
|---|---|---|
| `scopePolicyRef` | `task-platform` | The platform scope |
| `executionIdentityPolicyRef` | `plugin-service` | The task runs as its plugin, with no user |
| `triggerPolicyRef` | `every:<ISO-8601 duration>` | At every multiple of the duration since 1970-01-01T00:00Z, in whole seconds |
| | `cron:<second minute hour day month weekday>` | When the expression matches, in UTC |
| `timeoutPolicyRef` | `task-standard` | The host's task deadline, `brix.tasks.timeout` |
| `concurrencyPolicyRef` | `task-no-overlap` | A trigger due while the previous run is running is skipped |
| `retryPolicyRef` | `task-no-retry` | A failed run is not tried again |
| | `task-transient-retry` | A failed run is tried again for the same trigger, up to `brix.tasks.attempts` |
| `idempotencyPolicyRef` | `task-trigger` | One host runs each trigger |
| | `task-instance-local` | Every host runs every trigger, for work local to the host |
| `transaction` (optional) | `required` (the default), `read-only`, `none` | The unit of work the run is in (root BRX-RS-DATA-028) |

A name the runtime does not know stops the plugin at startup, and so does a trigger due more often than
`brix.tasks.min-interval`. A platform operational module's tasks use the same policies. They must be
`task-instance-local`, and they run without a unit of work.

## Host settings

| Setting | Default | Meaning |
|---|---|---|
| `brix.tasks.enabled` | `true` | Whether the host runs tasks |
| `brix.tasks.threads` | `4` | Runs that may go on at once |
| `brix.tasks.min-interval` | `PT1M` | The shortest interval a trigger may have |
| `brix.tasks.timeout` | `PT5M` | The deadline of `task-standard` |
| `brix.tasks.attempts` | `3` | The attempts of `task-transient-retry` |
| `brix.tasks.backoff` | `PT1S` | The wait before the second attempt; it doubles |
| `brix.tasks.drain-timeout` | `PT30S` | How long stopping waits for runs in flight |
| `brix.tasks.run-retention` | `P30D` | How long finished runs stay in `<storageId>_task_run` |
| `brix.tasks.instance-id` | made at startup | This host's name in the task-run tables |

## Testing

| Level | How |
|---|---|
| Task | A plain unit test: build a `TaskInvocation` and call `execute`. |
| End to end | Start your plugin in a host made of configuration, as `sample-e2e` does, then wait for its run in `<storageId>_task_run`. To check that a trigger runs once across hosts, start a second `RuntimeTaskScheduler` on the same database, as the sample does. |

## Operating

**Metrics**, labelled by `module` and `task`:

| Metric | Meaning |
|---|---|
| `brix_task_runs_seconds_count{outcome}` | Runs, by `succeeded`, `failed` or `cancelled`, and how long they took |
| `brix_task_skipped_total{reason}` | Triggers not run here: `overlap`, `claimed`, `not_started`, `claim_failed` |
| `brix_task_running` | Runs going on |
| `brix_task_consecutive_failures` | Runs that failed on this host since the last success |
| `brix_task_last_success_seconds` | When a run last succeeded on this host |
| `brix_task_interval_seconds` | The trigger's shortest interval |

The enterprise host alerts when a task keeps failing, when no host has run it successfully for three intervals, and
when triggers cannot be claimed. Logs carry `event=task.run` with the trigger, outcome, attempts and error code, and
`event=task.attempt_failed` with the exception.

**`<storageId>_task_run`** holds one row per claimed trigger:
- the task and trigger;
- the host that claimed it;
- its status (`RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`);
- the attempts and the error code.

Finished runs are deleted after the run retention.

## Do and don't

- Do declare every task in the manifest and bind its factory in `configure`.
- Do keep a run within its deadline, and check its cancellation signal in long work.
- Don't start threads, timers or schedulers, and don't use `SchedulingCapability`.
- Don't rely on a run being on a particular host. With `task-trigger` any host may run a trigger.
