---
id: changelog
title: Changelog
sidebar_label: Changelog
sidebar_position: 2
---

# Changelog

All notable changes to Brix Framework are documented here.

## [Unreleased] - the standard event chain

### ✨ Added

- **One event and command chain for every plugin.** Integration events and commands travel the same path: the
  publisher's outbox, the relay, Kafka (or in-process), and the subscriber's inbox. Everything is declared in
  `META-INF/brix/plugin-manifest.yaml`. See [Events and Commands](../guides/events-and-commands.md).
- **Typed endpoints.** A handler implements `TypedEndpointHandler<B, R>`, the root's
  `CompletionStage<R> handle(EndpointInvocation<B>)`.
  - The runtime builds `B` from the body or the query parameters and the path variables, checks it against
    `request.schemaRef`, and answers 400 `endpoint.invalid_request` with the broken paths and rules.
  - It answers with `response.status` (`200`, `201`, `202` or `204`) and checks `R` against `response.schemaRef`.
  - A handler fails with a `BrixException` whose code names an `ErrorCategory`, which the runtime maps to a status.
    `ErrorCode.category()` is new, and `INTERNAL` unless a code says otherwise.
  - `EndpointInvocation` carries a cancellation signal, set at the deadline; a late handler answers 504
    `endpoint.deadline_exceeded`.
  - Lists use `page`, `size` (at most 100) and `sort` and answer `Page` (`io.runtime.sdk.page`).
  - The samples' endpoints, and platform-tenant's FIRST_OWNER acceptance and tenant session, use the new shape.
    See [Endpoints](../guides/endpoints.md).
- **Calls to external services** (root BRX-RS-ERROR-014, A-11). A plugin declares each service under
  `externalServices` (`id`, `baseUrlRef` and its timeout, retry and concurrency policies) and calls it through
  `ExternalHttpCapability.service(id)` with a path within the service.
  - The host's directory `brix.external-services.<baseUrlRef>.base-url` gives the address, `https` only under a
    production profile.
  - An address that resolves to this host, a loopback, link-local or multicast address stops the plugin, and is
    refused again before every attempt.
  - Each call gets the earlier of its own deadline and the deadline of the call the plugin is handling, a limit of
    calls at once and a circuit breaker per plugin and service, bounded retries of idempotent methods, a size limit
    on the answer, and the trace. Redirects are not followed.
  - Failures are `external_http.*` codes; any answer is a response. Meter `brix.external_http.calls`.
  - platform-auth reads Google's signing keys through its declared service `google-oauth-jwks`. See
    [External services](../guides/external-services.md).
- **Endpoint deadlines, limits and traces.**
  - `timeout.policyRef` sets the deadline: `endpoint-default` (the host's endpoint deadline, 30 s), `interactive-read`
    (10 s) or `interactive-write` (15 s), set under `brix.runtime-shell.endpoints`. The plugin's transaction ends at
    the deadline.
  - Each endpoint runs at most 64 calls at once (`brix.runtime-shell.endpoints.concurrency`); one more answers 503
    `endpoint.rejected`.
  - Each call is a server span in a new trace, linked to a well-formed caller `traceparent`. Error bodies carry its
    `traceId`.
  - Meters `brix.endpoint.calls` and `brix.endpoint.in_flight`.
- **Tasks run.** A plugin declares its tasks under `tasks.provides` and binds a `ManagedTask`. The runtime's scheduler
  runs them:
  - triggers: `every:<duration>` aligned to the epoch, or `cron:<expression>` in UTC;
  - with `task-trigger`, one host per trigger, which claims it in the plugin's `<storageId>_task_run` table (created by
    the platform);
  - without overlapping runs, with a deadline, with optional retries, in the plugin's unit of work.

  Metrics `brix_task_*`. Platform operational modules' tasks use the same scheduler. Host settings `brix.tasks.*`; a
  trigger more frequent than `brix.tasks.min-interval` (default one minute) stops the plugin. See
  [Tasks](../guides/tasks.md).
- **Published contracts are pinned.** `contracts.api` holds the SHA-256 of every published schema version of a
  query, command or event, so a published file cannot change: a change is a new version (a patch for documentation).
  Events get contract files, `META-INF/brix/events/<eventType>/<version>.json`, which `EventSchemaContract` requires
  next to each published schema; their payloads are checked against the contract subset and against their earlier
  minor versions. platform-tenant's `TenantFirstOwnerAccepted` schema moves to `platform-tenant-api`, and
  platform-identity checks its two event schemas in its own build.
- **Typed commands.** The owner of a command publishes its contract in its api artifact
  (`META-INF/brix/commands/<commandType>/<version>.json`); the generator writes the request record and a
  `CommandType` constant. A sender declares the command under `commands.consumes` and submits the generated type
  through `CommandCapability.client(...)`: the runtime takes the tenant from the current call and records the sender
  and the version. The owner applies a command once per business key of the sender and tenant. An access policy can
  require a user holding a permission (`on-behalf-of:<permission>`). See
  [Commands](../guides/events-and-commands.md#commands).
- **BEST_EFFORT from the same bus.** `publishIntegration` sends a BEST_EFFORT event directly after the unit of work
  commits.
- **Messaging tables from the platform.** The platform creates `<storageId>_outbox`, `<storageId>_inbox` and
  `<storageId>_command_idempotency` from its own versioned migrations. Plugins write no messaging DDL.
- **Operations.**
  - Parked messages can be replayed, discarded (also a whole subscription's) or republished within retention. Each
    action is recorded in the platform audit with an operator and a reason.
  - Outbox, inbox and idempotency records are cleaned up after 14 and 30 days.
  - New metrics and alerts.
- **Production Kafka.** Under the `prod`/`production` profile the host requires TLS with client certificates, three
  replicas, two in-sync replicas and at least 12 partitions per event and command topic. An event or command topic
  that already exists with fewer partitions is refused: nothing is sent to it or consumed from it until its
  partitions are raised, the outbox keeps its messages without spending attempts, and the host's health is DOWN
  meanwhile. Elsewhere such a topic is only reported.
- **Sample plugins** (`packages/@brix/samples`): a publisher and a subscriber run end to end on PostgreSQL and Kafka.
  See the [Event-Driven Plugin Example](../examples/event-driven-plugin.md).
- **Typed queries.** A plugin reads another plugin's data through a query that plugin publishes: the contract and its
  generated types live in the provider's api artifact, the caller declares the query under `queries.consumes` and
  calls `QueryCapability.client(...)`. The runtime resolves the provider, passes on the caller, user, tenant and
  deadline, applies the access policy, a circuit breaker and a concurrency limit, and runs the provider read-only.
  platform-tenant publishes `TenantReferenceQuery` in the new `platform-tenant-api` artifact. See
  [Queries](../guides/queries.md).

- **Platform code authorizes on permissions, not role names.** `NoRoleNameAuthorizationRule` refuses
  `@RequireRole` and calls to `hasRole` or `hasAnyRole` outside the classes that implement them. The commons profile
  and platform-admin's architecture test run it (super-admin 4.0.1 permission model).

### 🔄 Changed

- **Handlers are bound through factories.** Plugins bind endpoint, query, command, event and task handlers with
  `bindEndpointHandlerFactory`, `bindEventHandlerFactory` and the like. The runtime creates them after the plugin
  started, with the capabilities the manifest requires.
- **Subscriptions start when they are first registered.** A new or renamed subscription does not receive older
  events.
- **The manifest is checked at startup.** Unknown retry, timeout and concurrency policies, a missing schema file, an
  event type with two publishers and an unbound handler all stop the plugin at startup.
- **`QueryHandler.handle` takes a `QueryInvocation` and returns a `CompletionStage`** (root BRX-RS-MANIFEST-025): the
  request, the caller, the tenant scope, the access decision, the trace and the deadline. A host whose plugins provide
  or call queries must set `brix.query.max-deadline`.
- **Command declarations name their policies** (root BRX-RS-MANIFEST-025). A provided command names its scope,
  access, timeout, concurrency, retry and idempotency policies; a consumed command names `required`, `versionRange`
  and its timeout, retry and idempotency policies. A sender declares `CommandCapability` and `data.outbox`, a provider
  `data.inbox`. Unknown names stop the plugin at startup. `CommandInvocation` carries the access decision and the
  business key.
- **An endpoint failure the handler does not map answers `platform.unknown`** with
  `{"errorCode", "message", "traceId"}`; the cause stays in the log.
- **Routes are compared after normalizing** (root BRX-RS-MANIFEST-023). Routes of one method that differ only in
  variable names, slashes, letter case or percent-encoding stop the host. Where two routes match a request, the one
  with a literal segment where the other has a variable answers; before, such a request answered 404. Path variables
  arrive decoded. A plugin route under `/api/platform/**`, or any route under `/oauth2/**`, `/.well-known/**`,
  `/auth/**` or `/bff/**`, stops the host.
- **Handlers no longer see `Authorization`, `Proxy-Authorization`, `traceparent`, `tracestate`, `baggage` or
  `x-trace-id`.** `EndpointInvocation.traceId()` is the runtime's trace id, never the caller's `x-trace-id`. A
  `setup-token` or `bootstrap-setup` entry still sees `Authorization`: its handler hands the token to the token's data
  owner, which verifies and spends it.
- **Endpoint idempotency `required` and `optional` stop the plugin** (root BRX-RS-MANIFEST-018). They need the
  runtime to replay results, which it does not do yet. The nine platform endpoints that declared `required`, eight in
  platform-admin and one in platform-tenant, now declare `not-applicable`: the runtime never acted on the
  declaration, and their handlers are idempotent by their business keys.
- **`ManagedTask.execute(TaskInvocation)`** (root BRX-RS-MANIFEST-027). The run receives its trigger, deadline,
  cancellation and attempt. `ManagedTask` is no longer a functional interface: implement `execute` in a class. A
  compiled task that implements `run()` still runs, and a task that implements neither method is refused at startup.
  `tasks.provides[].transaction` (`required`, `read-only`, `none`) picks the unit of work.
- **Enums are open** (root BRX-RS-CAP-021). A minor version may add enum values at the end. When a command or event
  arrives with a value the receiver's type does not know, the runtime reads it as null: an optional property is
  empty, and a payload that requires it is invalid. A type list `["<type>", "null"]` is accepted for an optional
  property.
- **A command's business key is unique per sender and tenant.** Two senders, or two tenants, using the same key no
  longer meet at the owner.
- **Connections are taken lazily.** A unit of work takes a pooled connection only for its first statement, and a
  query's provider waits for one no longer than the query's deadline. The host warns at startup when its pool is
  smaller than the work that holds connections. `brix.persistence.lazy-connections: false` turns it off.

### 🗑️ Deprecated

- `io.runtime.sdk.annotation.EventHandler`: nothing reads it. Declare the subscription in the manifest and bind an
  `io.runtime.sdk.plugin.EventHandler` with `bindEventHandlerFactory`.
- The instance binds `bindEndpoint`, `bindQueryHandler`, `bindCommandHandler` and `bindTask`: use the factory binds.
- `ManagedTask.run()`: implement `execute(TaskInvocation)`.
- `SchedulingCapability` and `RuntimeContext.getScheduling()`: a task comes only from the manifest (root
  BRX-RS-MANIFEST-026). Declare it under `tasks.provides`.
- `CommandCapability.submit(CommandRequest)`: it takes a string command type and a tenant the caller names. Submit
  the owner's generated `CommandType` through `CommandCapability.client(...)`.
- `TenantDirectoryCapability`: no runtime implements it. Ask the typed query `platform-tenant.tenant-reference` from
  `platform-tenant-api` whether a member or principal belongs to the caller's tenant.
- `HttpCapability`: it takes any URL. Declare the service under `externalServices` and call it through
  `ExternalHttpCapability`.

### 🗑️ Removed

- `NoStringRoleLiteralRule`: it never checked anything. `noClasses()` inverted its condition, and it looked for
  literals in a source location, which is only a file name and line. In bytecode a role literal cannot be told from
  a `RoleCode` constant anyway. `NoRoleNameAuthorizationRule` takes its place.
- Architecture Guard rules that refused packages no module has, `io.runtime.sdk.impl`, `io.runtime.sdk.internal`
  and `io.runtime.sdk.api`: `OnlyRuntimeSdkApiRule.noRuntimeSdkImpl()` and `noRuntimeSdkInternal()`,
  `NoCyclicDependencyRule.sdkApiNotDependOnImpl()` and `SdkProfile.apiNotDependOnImpl`. The runtime's
  implementation, `io.runtime.orchestrator`, stays refused. `OnlyRuntimeSdkApiRule.rule()` is now
  `noRuntimeOrchestrator()`.

### 🐛 Fixed

- **The Architecture Guard checks the plugins and platform modules that exist.** Several rules named package roots
  that no code uses, or only one of the plugin roots, and passed without checking anything:
  - The commons profile looked for `io.brix.commons`; platform-commons is `io.brix.platform`. It now checks that
    root, and a module that runs it without a class under the root fails instead of passing.
  - The plugin rules knew only `io.brix.app`, where `create-brix phase7 plugin` puts a plugin. They now also cover
    `io.brix.enterprise.app` and `io.brix.samples`, from one definition, `ArchitectureLayers.PLUGIN_ROOTS`.
  - The rule against using another plugin never fired: besides the roots, `noClasses()` inverted the violations
    its own condition reported. A plugin may use another plugin's `api` package and nothing else of it.
  - The sample plugins run the plugin profile.
  - `ConfigViaCapabilityRule.noSystemEnvAccess` never fired: `noClasses()` inverted the calls its condition
    reported. It now refuses `System.getenv()` and `System.getProperty()` in every plugin and host that runs the
    plugin or host profile.
  - Six platform classes already use Redis directly. They are listed in
    `META-INF/brix/guard/platform-redis-clients.txt` of their modules under exemption EX-2026-004, and the lists
    only shrink.
- **Event and command handlers no longer commit after their deadline** (root BRX-RS-DATA-011, DATA-028). The
  deadline applied only to a handler that returned an unfinished stage. A handler that did its work before it
  returned, as handlers do, ran past the deadline and still committed. Now:
  - the handler's unit of work lasts no longer than the time left before the delivery's deadline (half the relay
    lease), so its statements stop, and its waits for a pooled connection end, at the deadline;
  - a handler that returns after the deadline fails the delivery before the commit, so its writes, the inbox
    receipt and a command's business key roll back, and the message is retried.
- **The documented ordering guarantee names its exception.** A parked message, in the publisher's outbox or at a
  subscription, holds nothing back: the later messages of its key go on, and a replayed one arrives after them. The
  events guide also lists the identifiers that cannot change once a plugin runs in production (`pluginId`,
  `storageId`, event and command types, handler ids, published schema versions and task ids), with what a change
  does and what to do instead.

---

## [3.0.0] - 2024-01-15

### 🚀 Major Release - Runtime Shell Architecture

This release introduces the **Runtime Shell Architecture**, a fundamental shift in how plugins interact with infrastructure.

### ✨ Added

- **Capability Contracts** - All infrastructure accessed through interfaces
  - `DataAccessCapability` - Database operations
  - `CacheCapability` - Caching operations
  - `EventBusCapability` - Event publishing/subscribing
  - `HttpCapability` - HTTP client
  - `FileStorageCapability` - Object storage
  - `LockCapability` - Distributed locks
  - `MessageQueueCapability` - Message queues
  - `SchedulerCapability` - Job scheduling

- **Architecture Guard** - ArchUnit-based enforcement of 13 red-line rules
  - Adapter isolation rule
  - No cross-plugin dependencies rule
  - Ultra-thin host rule
  - And 10 more...

- **Shared Runtime** - Single React instance across all plugins
  - `@brix/shared-runtime-web` package
  - Prevents multiple React instances error

- **Event-Driven Communication** - Cross-plugin events
  - Domain events (within plugin)
  - Integration events (across plugins)
  - Idempotent event handlers

- **Plugin Archetype** - Maven archetype for new plugins
  ```bash
  mvn archetype:generate -DarchetypeGroupId=io.brix ...
  ```

### 🔄 Changed

- **Java 17 required** (was Java 11)
- **Spring Boot 3.2** (was 2.7)
- Package structure: `io.brix.runtime.sdk.api.*`
- Frontend imports from shared-runtime

### 🗑️ Removed

- Direct JPA access in plugins
- Direct Kafka/Redis access in plugins
- `@Repository` annotation in plugin code
- `@KafkaListener` annotation (use `@EventHandler`)

### 📦 Migration

See [Migration Guide](./from-v2) for detailed instructions.

---

## [2.7.0] - 2023-10-01

### ✨ Added

- Spring Boot 2.7 support
- Improved plugin discovery
- Better error messages

### 🐛 Fixed

- Memory leak in event processing
- Connection pool exhaustion under load

---

## [2.6.0] - 2023-07-15

### ✨ Added

- Multi-tenant support
- Async event processing
- Health check endpoints

### 🔄 Changed

- Upgraded Kafka client to 3.4
- Improved startup time by 30%

---

## [2.5.0] - 2023-04-01

### ✨ Added

- Plugin hot-reload in development
- OpenAPI 3.0 documentation generation
- Metrics export to Prometheus

### 🐛 Fixed

- Race condition in cache invalidation
- Timezone handling in date serialization

---

## [2.4.0] - 2023-01-15

### ✨ Added

- GraphQL support
- Batch processing utilities
- Rate limiting

### 🔄 Changed

- PostgreSQL 15 support
- Redis 7 support

---

## [2.3.0] - 2022-10-01

### ✨ Added

- Plugin dependency management
- Circular dependency detection
- Plugin versioning

### 🐛 Fixed

- Transaction rollback on event failure
- Memory usage optimization

---

## [2.2.0] - 2022-07-01

### ✨ Added

- Event sourcing support
- Saga pattern helper
- Outbox pattern implementation

---

## [2.1.0] - 2022-04-01

### ✨ Added

- Distributed tracing
- Log correlation
- Performance profiling

---

## [2.0.0] - 2022-01-01

### 🚀 Major Release

- Initial public release
- Modular plugin architecture
- Event-driven communication
- Multi-database support

---

## Version Policy

Brix follows [Semantic Versioning](https://semver.org/):

- **Major** (x.0.0): Breaking changes, architecture shifts
- **Minor** (0.x.0): New features, backward compatible
- **Patch** (0.0.x): Bug fixes, security patches

## Support Policy

| Version | Status | Support Until |
|---------|--------|---------------|
| 3.0.x   | Active | Current       |
| 2.7.x   | LTS    | Jan 2025      |
| 2.6.x   | EOL    | Jul 2024      |
| < 2.6   | EOL    | -             |

## Upgrade Recommendations

- **2.x → 3.x**: Follow [migration guide](./from-v2) carefully
- **2.6 → 2.7**: Direct upgrade, no breaking changes
- **< 2.6**: Upgrade to 2.7 first, then to 3.x
