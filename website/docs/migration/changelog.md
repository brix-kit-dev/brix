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
  - A platform operational module's endpoints can use it too: `platform-operational.yaml` takes `response.status`, and
    a typed handler is held to its schemas and status. A handler of the old shape works as before.
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
- **Published contracts are pinned.** `contracts.api` holds the SHA-256 of every schema version a module ships,
  whatever uses it (a query, a command, an event, an endpoint's request or response, an entrusted record), so a
  published file cannot change: a change is a new version (a patch for documentation). platform-admin,
  platform-tenant and platform-audit-api run `ContractSourcesTest` for their own schemas, and the samples' api
  listings pin their endpoint schemas too. A new module locks its contracts from the start: the governed plugin
  scaffold puts `ContractSourcesTest` in its api module, and `RepositoryContractLocksTest` fails the build when a
  module ships a schema, query, command or event contract without a listing and that test.
  Events get contract files, `META-INF/brix/events/<eventType>/<version>.json`, which `EventSchemaContract` requires
  next to each published schema; their payloads are checked against the contract subset and against their earlier
  minor versions. platform-tenant's `TenantFirstOwnerAccepted` schema moves to `platform-tenant-api`, and
  platform-identity checks its two event schemas in its own build.
- **Typed commands.** The owner of a command publishes its contract in its api artifact
  (`META-INF/brix/commands/<commandType>/<version>.json`); the generator writes the request record and a
  `CommandType` constant. A sender declares the command under `commands.consumes` with its version range and whether
  it requires it, and names no policies: the command is delivered under its provider's, and a sender's timeout, retry
  or idempotency policy stops the plugin at startup. The sender submits the generated type through
  `CommandCapability.client(...)`: the runtime takes the tenant from the current call and records the sender and the
  version. The owner applies a command once per business key of the sender and tenant. An access policy can
  require a user holding a permission (`on-behalf-of:<permission>`). See
  [Commands](../guides/events-and-commands.md#commands).
- **Command status.** A sender reads where each command it sent stands with `CommandClient.status(commandId)`:
  `QUEUED`, `SUCCEEDED`, `DUPLICATE` (an earlier command with the same key took effect; the status names it),
  `PARKED`, `DISCARDED` or `UNKNOWN`. Only the sender reads it, in the tenant it sent the command in. The provider
  records each command that took no key in the new `<storageId>_command_outcome` table, and an operator's discard of a
  command the provider parked on Kafka is recorded there before the command is let go. See
  [Reading a command's status](../guides/events-and-commands.md#reading-a-commands-status).
- **A key reused with other content is a conflict.** The provider keeps a fingerprint of the payload with each key
  (`request_fingerprint`, a second migration of `<storageId>_command_idempotency`). A later command with the same key
  and another fingerprint does not run and is not retried or parked: its sender reads `CONFLICT` with
  `command.idempotency_conflict`, and `brix_command_idempotency_conflicts_total` counts it. The same content written
  another way is still a duplicate. Two commands with the same key at once no longer fail one delivery: the second
  waits for the first's transaction, then is a duplicate or a conflict. The sample's `sample-orders.cancel-order` 1.1.0
  adds the reason `DAMAGED`, and a refusal can name it.
- **A command handler can reject a command.** It throws a `BrixException` whose code's category is `INVALID`,
  `DENIED`, `NOT_FOUND` or `CONFLICT`. Its unit of work rolls back, and the provider records the rejection with the
  code in a short transaction of its own. The sender reads `REJECTED` with the code. The command is not retried or
  parked, and it takes no key, so a later command with the key runs. Any other failure is retried and then parked, as
  before. `sample-orders` rejects the cancellation of an order it does not have with `sample-orders.order_not_found`.
- **Repeating a call.** An endpoint that declares `idempotency.mode: required` replays its results (root
  BRX-RS-MANIFEST-018). The caller names a key in the `Idempotency-Key` header. The first call keeps its status and
  body with its writes, in the new `<storageId>_endpoint_idempotency`; a later call with the same content gets them
  again with `Idempotent-Replayed: true`, and the handler does not run. A call that fails keeps nothing. Without a key
  the call is answered 400 `endpoint.idempotency_key_required`, with other content 422
  `endpoint.idempotency_conflict`, and while the first has not finished 409 `endpoint.idempotency_in_progress`.
  `EndpointInvocation.idempotency()` gives the handler its key, and `brix.endpoint.calls` has the outcome `replayed`.
  `sample-orders`' `POST /api/sample-orders/orders` now requires a key. A platform operational endpoint replays too:
  it names the internal contract it writes through, the owner keeps the contract's result with its write, and a call
  that stopped after the owner committed goes on from there. See
  [Repeating a call](../guides/endpoints.md#repeating-a-call).
- **BEST_EFFORT from the same bus.** `publishIntegration` sends a BEST_EFFORT event directly after the unit of work
  commits.
- **Messaging tables from the platform.** The platform creates `<storageId>_outbox`, `<storageId>_inbox`,
  `<storageId>_command_idempotency`, `<storageId>_command_outcome` and `<storageId>_endpoint_idempotency` from its own
  versioned migrations. Plugins write no messaging DDL.
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

- **Platform code and plugins authorize on permissions, not role names.** `NoRoleNameAuthorizationRule` refuses a
  call to `hasRole` or `hasAnyRole`, and a method reference to them, whatever declares the method; declaring one is no
  call. The commons profile, the plugin profile and platform-admin's architecture test run it (super-admin
  BRX-SA-AUTH-006, multi-tenant T-29). In UI code the ESLint rule `@brix-architecture/no-role-authorization` refuses a
  call to `hasRole`, `hasAnyRole` or `hasAllRoles`, `useRole` from `@brix-sdk/platform-auth-ui-web`, and the `role`
  and `anyRoles` props of `PermissionGate` (frontend BRX-FE-ROUTE-002), in every package whose ESLint config parses
  its TypeScript.
- **Platform role and permission IDs in `platform-auth-api`** (super-admin 4.0.1 §4). The new artifact holds
  `io.brix.platform.auth.api.RoleCode` (`PLATFORM_SUPER_ADMIN`) and `PermissionCode` (the catalog's ten `platform:*`
  IDs), with no implementation and no dependency. platform-admin names them from there: an operational module may
  depend on a contract, never on platform-auth itself. A test pins every ID: once published, an ID never changes and
  is never reused.
- **An internal contract publishes through its plugin's own outbox** (root BRX-RS-HOST-022, DATA-008, MESSAGE-022).
  A contract factory that asks `requireOwnerCapability(EventBusCapability.class)` gets the event bus the plugin's
  `PluginContext` gives its handlers, when the manifest declares `EventBusCapability`: CRITICAL and STANDARD events go
  to the plugin's `data.outbox` in the contract call's unit of work and commit or roll back with its writes;
  BEST_EFFORT events leave after the commit. The request used to fail with
  `internal_contract.owner_capability_undeclared`, so a contract could announce nothing it changed. A plugin that does
  not declare the bus is refused as before, a host without owner persistence answers
  `internal_contract.owner_event_bus_missing`, and the host's direct bus is never handed to a contract. An operational
  module's contracts get no bus. See
  [Publish from an internal contract](../guides/events-and-commands.md#5-publish-from-an-internal-contract).

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
  access, timeout, concurrency, retry and idempotency policies, by names of their own: `command-envelope`,
  `declared-consumers` or `on-behalf-of:<permission>`, `command-standard`, `partition-serial`,
  `command-transient-retry` and `command-key`. An event policy name on a command, such as `event-standard`, stops the
  plugin at startup. A consumed command names `required` and `versionRange`, and no policies, which the runtime
  refuses there because the provider's apply. A sender declares `CommandCapability` and `data.outbox`, a provider
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
  `setup-token` entry still sees `Authorization`: its handler hands the token to the token's data owner, which verifies
  and spends it.
- **The first platform administrator is created for a session the runtime verified** (super-admin 4.0.1 §6,
  BRX-SA-BOOT-004). `POST /api/platform/bootstrap/create-first-admin` declares `authentication.mode: required` with
  `policyRef: bootstrap-setup`.
  - The host verifies the BOOTSTRAP_SETUP token before the handler: signature, issuer, audience, expiry, type, role,
    scope and `jti`. The session is the caller, named by its `jti`; no identity stands behind it, so the token-version
    check does not apply to it.
  - The handler gets the session as `actorId()` and never the token. The identity owner checks that it is the session
    it opened last, unused and unexpired, and spends it in the same transaction.
  - A call without a session, or with an expired one, answers 401 `ENDPOINT_AUTHENTICATION_REQUIRED` before the
    handler, where it answered 400 `PLATFORM_BOOTSTRAP_INVALID_REQUEST`.
  - `bootstrap-setup` accepts the bootstrap session only, for an operational module's entries under
    `/api/platform/bootstrap/`. Every other token policy refuses the session, including an endpoint that names none.
  - The internal contract `PlatformBootstrapAdministration` is 2.0.0: `CreateFirstPlatformAdminCommand` names the
    verified session (`bootstrapSessionId`) instead of carrying its token.
- **Endpoint idempotency `optional` stops the plugin, and `required` has rules** (root BRX-RS-MANIFEST-018). `required`
  needs a typed write endpoint of an authenticated caller, with its schemas, in a plugin with persistence. An
  operational endpoint also names its write contract, and its owner declares `replayableWrites`. The runtime never
  acted on the `required` some platform-admin endpoints declared; three of them still declare `not-applicable`:
  `POST /api/platform/bootstrap/session`, whose answer carries the session token, and the old setup's
  `/api/platform/auth/setup/totp/init` and `/complete`, which the hosted setup page replaces. `POST /api/platform/tenants`,
  `POST /api/platform/tenants/{tenantId}/first-owner-invitations`, its `/resend` and
  `DELETE /api/platform/tenants/{tenantId}/first-owner-invitations/{invitationId}` are typed and replay their answers:
  a caller must send an `Idempotency-Key`, and the platform console does. So does
  `POST /api/platform/bootstrap/create-first-admin`, under the bootstrap session: a retry needs the same session and
  key, which the console keeps, and gets the first answer; a spent session writes nothing more under another key, and
  an expired one gets 401 instead of the answer. A request its schema refuses answers 400 `endpoint.invalid_request`. The revoke answers 204 with no body, where it
  answered 200 with an empty one, and takes no query parameters; a retry under its key hears 204 again, not that the
  invitation is no longer revokable. platform-tenant's FIRST_OWNER acceptance replays its answer, under the hash of the invitation token.
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
- `AuthCapability.hasRole` and `AuthContextCapability.hasRole`, for removal in 4.0.0: authorize on a permission code
  with `hasPermission` or `hasAnyPermission`. The plugin profile already refuses a call.
- In TypeScript, for removal in 4.0.0: `AuthCapability.hasRole`, `hasAnyRole` and `hasAllRoles` (web; `hasRole` on
  mobile), `useAuth()`'s `hasRole` and `hasAnyRole`, `PermissionGate`'s `role` and `anyRoles` props, and `useRole`.
  Use `hasPermission`, `hasAnyPermission`, `hasAllPermissions`, the gate's permission props and `usePermission`.

### 🗑️ Removed

- `authentication.mode: bootstrap-setup`: declare `required` with `policyRef: bootstrap-setup`. A declaration that
  still names the mode stops its module and names the replacement.
- `NoStringRoleLiteralRule`: it never checked anything. `noClasses()` inverted its condition, and it looked for
  literals in a source location, which is only a file name and line. In bytecode a role literal cannot be told from
  a `RoleCode` constant anyway. `NoRoleNameAuthorizationRule` takes its place.
- Architecture Guard rules that refused packages no module has, `io.runtime.sdk.impl`, `io.runtime.sdk.internal`
  and `io.runtime.sdk.api`: `OnlyRuntimeSdkApiRule.noRuntimeSdkImpl()` and `noRuntimeSdkInternal()`,
  `NoCyclicDependencyRule.sdkApiNotDependOnImpl()` and `SdkProfile.apiNotDependOnImpl`. The runtime's
  implementation, `io.runtime.orchestrator`, stays refused. `OnlyRuntimeSdkApiRule.rule()` is now
  `noRuntimeOrchestrator()`.
- `io.brix.platform.auth.RoleCode`: use `io.brix.platform.auth.api.RoleCode`. Bootstrap is no platform role; the
  bootstrap setup token keeps `BOOTSTRAP` as its role claim, now `PlatformPermissions.BOOTSTRAP_ROLE`.
- The ten catalog constants of `PlatformPermissions` (`ADMIN_READ`, `TENANT_READ` and the others): use
  `PermissionCode`. Tokens carry the same codes as before.
- platform-auth's role checks, which nothing used: the `@RequireRole` annotation and its branch of
  `PermissionAspect`, `AuthenticatedUser.hasRole` and `hasAnyRole`, and `SecurityContextHolder.hasRole`. Authorize
  with `@RequirePermission` and a permission code.

### 🐛 Fixed

- **A typed endpoint reads the numbers the client wrote.** The runtime read a request body with the host's JSON
  mapper, which rounds a decimal to the nearest `double`: an amount of `12345678901234567.89` reached the handler as
  `12345678901234568`, an `integer` property took `2.0000000000000001` for 2, and two requests that differed past the
  17th digit had one idempotency fingerprint, so the second got the first one's answer. A typed endpoint now reads the
  body's text with every decimal digit (root BRX-RS-MANIFEST-022): its schema check, its fingerprint and its `B` see
  the number sent, and a repeated call gets every digit of the first answer. A number whose exponent would make it
  longer, written out, than the host's parser takes one (1000 digits by default) is refused with
  `endpoint.invalid_request`, rule `type`, before anything works on it. A handler of the old shape still gets the body
  as the host's mapper reads it. A host that provides its own `PluginEndpointDispatcher` receives the exact body
  through a new `invoke` overload, whose default passes the call on as before.
- **An endpoint handler that answers after its deadline no longer commits.** A handler that did its work synchronously
  and returned after the deadline was committed and answered with success. The runtime now checks the deadline once
  more before the commit: the work rolls back, the cancellation signal is set and the call answers 504
  `endpoint.deadline_exceeded`, for typed handlers and those of the old shape alike (root BRX-RS-DATA-011, DATA-028).
  An operational module's endpoint answers 504 too; its owners commit their writes themselves, so a repeat with the
  same `Idempotency-Key` gets the kept result.
- **What `CommandCapability` promises.** Its documentation said a command is delivered exactly once per idempotency
  key, and that the provider reports the outcome through its own events. Brix promises at-least-once delivery
  (root BRX-RS-MESSAGE-020); the provider applies a command at most once per key of the sender and tenant, and the
  sender reads the outcome with `CommandClient.status(commandId)`.
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
- **Hosts no longer keep parked payloads in memory.** Every host read every parked record into its heap, so a burst of
  parked events could fill the heap of all hosts at once, and again after each restart. Now:
  - a host keeps what it lists and where each record is in Kafka, whatever the size of the payload;
  - a replay or a discard reads the record back. When Kafka does not give it back, a replay answers `503` with
    `EVENT_PARKED_UNREADABLE` and the event stays parked;
  - `/actuator/events` also shows each event's `producerPluginId` and `tenantId`, and a sender asking after its parked
    command is answered from them, without reading Kafka;
  - a host that has not read the parking topics yet says so: `/actuator/events`, a replay and a discard answer `503`
    until it has, so an empty list always means that nothing is parked.
- **Only the owner is waited for.** A failure counted as "owner unavailable" whenever a connection failure appeared
  anywhere in its chain, so a service a handler calls could hold a partition back for good, spending no attempt. That is
  now a failed attempt. A delivery waits only when the plugin has not started, or when the persistence layer says that the
  database cannot be reached. Another store failing with a Spring data access exception no longer reads as the database.
- **A wait has a limit.** A message the receiving side cannot take no longer waits for ever, at a fixed pace:
  - the pauses between its tries double, with jitter, up to a minute;
  - after `brix.outbox.relay.max-wait` (24 hours by default) it is parked with the code it waited for, and the later
    messages of its key go on. This holds for the relay and for a subscription's consumer;
  - the host does not start when the limit is not below the retention of the event topics;
  - while Kafka cannot be reached at all, the relay still pauses without trying anything, and that time counts against
    no wait.
- **The relay knows why Kafka did not take a message.** `KafkaTemplate` wraps what the producer reports, or throws it
  from `send()`, so the Kafka transport recognized no real failure: each one spent an attempt, and brokers that refused
  writes had the messages parked after about three minutes. Now:
  - a record Kafka can never take, such as one too large or one whose topic name the brokers refuse, is parked at once;
  - brokers that cannot take writes now (too few replicas, no leader, a send that timed out, a topic that cannot be
    prepared) or that refuse the host (an ACL, its identity) are no fault of the message: it waits without spending an
    attempt and leaves by itself once the cause is gone;
  - while a type waits, the relay tries one of its messages every few seconds and sends the other types;
  - a wait parks nothing, so it is counted in `brix_outbox_waits_total{outbox,type,code}`.
- **Sending a notification has a time limit** (root BRX-RS-RESILIENCE-001). JavaMail waits for ever on each step
  unless told otherwise, and the SMTP adapter told it nothing: a mail server that took the connection and never
  answered held the caller's request thread, its transaction and its locks, and every mail health check, for good.
  Now:
  - each connect, read and write has a timeout: `brix.infra.email.smtp.connect-timeout`, `read-timeout` and
    `write-timeout`, 5, 10 and 10 seconds unless the host sets others. They are set on the mail sender, so the mail
    health check's connections are bounded too;
  - a whole delivery ends at the deadline of the call it runs in, or after `brix.infra.email.smtp.send-timeout`
    (30 seconds) outside one. Then the adapter closes the connection: the server never accepts a message whose end it
    has not acknowledged, and the delivery fails as a timeout, so the caller's write rolls back with no email sent.
    Once the deadline has passed, it does not connect;
  - a failure while saying goodbye after the server accepted the message no longer fails the delivery;
  - the adapter refuses to start when `spring.mail.properties` sets any of these timeouts, a socket factory, its
    fallback or a proxy itself, when the mail sender is not a `JavaMailSenderImpl` with a host, when its protocol is
    neither `smtp` nor `smtps`, or when one of the four limits is not positive;
  - a failed delivery is logged once at WARN as `event=notification.delivery_failed`, with the outcome, the template,
    the masked recipient, the trace id and a safe detail such as the reply code; never the address, a link or the
    server's reply text. The caller still gets `notification.delivery.failed`;
  - meter `brix.notification.sends{template,outcome}`, outcome `sent`, `rejected`, `timeout` or `failed`. The
    platform's four templates start at zero, so the first failure after a start shows as an increase.

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
