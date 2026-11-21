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
- **Plugins send commands only through the generated client** (root A-22, BRX-RS-MESSAGE-009).
  `GeneratedContractClientsRule.noStringCommandGateway`, in the plugin profile, refuses a class that uses
  `CommandRequest`, or calls or references `CommandCapability.submit(CommandRequest)`: the string command gateway is
  the runtime's. No plugin of either repository used it.
- **Plugins take query and command types from the generated constants** (root A-22). The SDK annotation
  `io.runtime.sdk.query.GeneratedContractConstants` (class retention) marks the constants classes the contract
  generator writes, such as `SampleShippingQueries` and `SampleOrdersCommands`.
  `GeneratedContractClientsRule.noHandMadeQueryOrCommandTypes`, in the plugin profile, refuses a class without that
  mark that calls or references `CommandType.of`, `QueryType.of` or `QueryType.optional`, whatever its package. Only
  the generated constants of either repository called them.
- **A module lists the `@Value` reads it already has, member by member.** `ConfigViaCapabilityRule.noSpringValue`
  lets through the members a module names as `<class>#<member>` in `META-INF/brix/guard/spring-value-readers.txt` of
  its test resources, and nothing else: a new `@Value` field, method or parameter fails, in a listed class too. A
  module without a list is checked as before. It is for the platform modules that are still assembled by Spring, under
  an exemption, until they read their configuration through `ConfigCapability`.
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
- **The samples publish from an internal contract.** When an operator changes an order's quantity, sample-orders'
  `OrderCorrections` publishes STANDARD `SampleOrderQuantityAdjusted` (schema
  `sample-orders/sample-order-quantity-adjusted` 1.0.0, in the module's `contracts.api` listing) through the plugin's
  outbox, with the change. sample-shipping subscribes with `sample-shipping.follow-quantity.v1`. The event carries the
  order's state and version, so the shipment keeps whichever of it and `SampleOrderPlaced` is newer, and either event
  prepares the shipment: events of different types are not ordered. A `SampleOrderPlaced` that arrives after a change
  now leaves the shipment as it is, where it used to fail on the existing shipment.
- **Every push is scanned for secrets.** A new CI workflow, Secret Scan, runs gitleaks, a pinned release checked
  by its sha256, over every commit of the pushed history and over the files as they are now, on every push to any
  branch and every pull request. A finding names the rule, the file, the line and the commit, never the value, and
  fails the run; so does a committed private key, key store or `.env` file. A test sample that has to stay carries
  `gitleaks:allow` and its reason; findings in old commits are listed by fingerprint in `.gitleaksignore`.

### 🔄 Changed

- **A command client checks its request type's schema** (root A-22). `CommandCapability.client(type)` refuses with
  `command.invalid` a request type that does not carry the contract's `@ContractSchema`: exactly the request schema the
  owner declares when the owner runs on the host, otherwise a schema of the type's version, as query clients already
  check theirs. A plugin takes its clients when it starts, so a wrong type stops its start. The generated types from
  the owner's `-api` artifact pass; the runtime's descriptor of a provided command keeps its request schema
  (`ProvidedCommand.requestSchemaRef`).
- **The contract generator marks the constants classes it writes** with `@GeneratedContractConstants`, which the
  plugin profile's `noHandMadeQueryOrCommandTypes` looks for. An api module with query or command contracts fails its
  `ContractSourcesTest` until it regenerates them: run the test with `-Dbrix.contracts.write=true`, review that only
  the import and the annotation were added, and commit. The records and `contracts.api` do not change.
- **The dispatcher checks the caller before the request** (root 3.4.0 BRX-RS-MANIFEST-022, RFC-010). The
  authentication, tenant context and permission checks now come before the request is built and checked against its
  schema. A call without a verified caller gets 401 `ENDPOINT_AUTHENTICATION_REQUIRED` and a caller the entry refuses
  403, whatever the request, and neither is told what the request breaks: before, a request that broke its schema got
  400 `endpoint.invalid_request` and its violations first. A call that passes the checks, and a call to an anonymous
  entry, is answered as before. Plugins change nothing. See [Failures](../guides/endpoints.md#failures).
- **An endpoint's body is read after the access checks, and only as JSON** (root 3.4.0 BRX-RS-MANIFEST-022). The
  runtime's controller no longer has Spring read the body before the dispatcher; the dispatcher reads it once the call
  has passed its checks, in `application/json` or `application/*+json`, as the host's JSON mapper reads it, and a
  typed endpoint still with every decimal digit. A body of another content type is answered 415 as before; so is
  `application/yaml` now, which a host with Jackson's YAML module used to read, numbers as doubles. A host that
  provides its own `PluginEndpointDispatcher` gets the body read first, as before, through the default of the new
  `invoke(..., EndpointRequestBody, ...)` overload. `EndpointJsonBodyAdvice` is gone.
- **A notification sent in a unit of work goes out at its commit** (root 3.4.0 BRX-RS-ERROR-017, RFC-012).
  `NotificationCapability.send` still checks the request and renders the template at once, but inside a unit of work
  it now hands the email to the mail server at the last moment before that unit of work commits: after all of its
  writes, the owner's pending changes flushed to the database first, and after the answer, inbox and outbox records
  the runtime writes for the owner. Emails go out in the order they were sent; the first the server refuses, fails or
  times out stops the rest and rolls the unit of work back with `notification.delivery.failed`, as before. A unit of
  work that rolls back for another reason sends nothing: before, a write after the send that failed left the email
  sent, with a link that could only fail. Outside a unit of work an email goes out at once. Returning from `send`
  therefore no longer means the email went out. An owner called by another owner sends its emails when its own unit
  of work commits, before its caller's. An event or command handler's unit of work now commits inside its call, so an
  email sent at that commit still ends at the delivery's deadline. Plugins change nothing. See
  [Notifications](../guides/backend-development.md#notifications).
- **An event or command delivery runs with its trace on the thread** (root 3.4.0 BRX-RS-ERROR-013, ERROR-017 item 7).
  While the runtime delivers an event or a command, from the inbox record through the handler to the commit and the
  emails sent at it, the logging context holds the delivery's `traceparent` and `traceId`, as it does during an
  endpoint call, and the thread gets its own values back afterwards. An email refused at the commit is now logged with
  the delivery's trace id instead of `-`, and the events, commands and external calls a handler makes continue the
  delivery's trace instead of starting new ones. A message without a well-formed W3C `traceparent` starts a new
  trace, as one without any did. Plugins change nothing. See
  [Events and Commands](../guides/events-and-commands.md).
- **Handlers are bound through factories.** Plugins bind endpoint, query, command, event and task handlers with
  `bindEndpointHandlerFactory`, `bindEventHandlerFactory` and the like. The runtime creates them after the plugin
  started, with the capabilities the manifest requires.
- **Subscriptions start when they are first registered.** A new or renamed subscription does not receive older
  events.
- **The manifest is checked at startup.** Unknown retry, timeout and concurrency policies, a missing schema file, an
  event type with two publishers and an unbound handler all stop the plugin at startup.
- **Every host checks the manifest against its JSON Schema** (root BRX-RS-MANIFEST-001, BOOT-001; standard-link
  plan D152, D153). The runtime reads `META-INF/brix/plugin-manifest.yaml` against `plugin-manifest.schema.json`, the
  file in `packages/@brix/platform-devtools/schemas`, before anything else uses it, and the error names each field.
  - Until now only a web host's plugin registry checked the schema. The runtime's own loader, which the plugin
    runtime, the JPA adapter, the outbox and the migrations use, took a field of one declaration on any other and
    ignored it.
  - These now stop the plugin in every host: `handlerId` or a scope policy on a sent command, `versionRange` on a
    provided command, `retryPolicyRef` or `idempotencyPolicyRef` on a provided query, and earlier names (`routes`;
    an endpoint's `id`, `handler` or `accessPolicy`; an event's `id` or `schema`). So do `internalContracts.requires`
    and a missing section: every section the schema requires is there, even empty.
  - The manifests of this repository's plugins and the create-brix template already pass.
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
- **The installation quota entry checks `platform:tenant:read`** (super-admin BRX-SA-AUTH-006).
  `GET /api/platform/license/quota` used `platform:license:read`, which is not in the super-admin permission
  catalog; the quota is the tenant owner's data, and the tenant list already shows it on each row under
  `platform:tenant:read`. The platform console's License / quota page, its menu entry and its dashboard card follow,
  and `PLATFORM_ADMIN_PERMISSIONS.LICENSE_READ` is gone from `@brix-sdk/platform-admin-web`. A super
  administrator's token carries both codes today, so nothing changes for one.
- **A platform super administrator's token carries the permission catalog and nothing else** (super-admin
  BRX-SA-AUTH-006). The token and the login response list the ten IDs of `PermissionCode`; the seven codes outside
  it (`platform:tenant:manage`, `platform:tenant:view`, `platform:admin:manage`, `platform:audit:view`,
  `platform:system:config`, `platform:license:read`, `platform:data:recovery`) are gone. No entry and no console page
  checked them. A token issued before keeps them until it expires.
- **The platform permission seed is the catalog** (super-admin §3.3). The forward migration platform-auth `V5` deletes
  the ten codes outside `PermissionCode` from `auth_permission`, their grants, and the `BOOTSTRAP` role with its
  grants and any user's link to it: Bootstrap is a setup session, never a database role. `V1` to `V4` are unchanged.
  Nothing reads these tables, so no behaviour changes; the tenant roles `TENANT_ADMIN` and `USER` stay.

### 🗑️ Deprecated

- `io.runtime.sdk.annotation.EventHandler`: nothing reads it. Declare the subscription in the manifest and bind an
  `io.runtime.sdk.plugin.EventHandler` with `bindEventHandlerFactory`.
- The instance binds `bindEndpoint`, `bindQueryHandler`, `bindCommandHandler` and `bindTask`: use the factory binds.
- `ManagedTask.run()`: implement `execute(TaskInvocation)`.
- `SchedulingCapability` and `RuntimeContext.getScheduling()`: a task comes only from the manifest (root
  BRX-RS-MANIFEST-026). Declare it under `tasks.provides`.
- `CommandCapability.submit(CommandRequest)`: it takes a string command type and a tenant the caller names. Submit
  the owner's generated `CommandType` through `CommandCapability.client(...)`. The plugin profile refuses it. It
  works as before until 4.0.0, and the first time a plugin uses it after it starts, the host logs one WARN
  `event=command.deprecated_submit` naming the plugin and the command type.
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

- runtime-orchestrator's `io.runtime.orchestrator.manifest.PluginManifestValidator` and its copy of
  `plugin-manifest.schema.json`, and the `UIManifestLoader(ObjectMapper, PluginManifestValidator)` constructor:
  the schema and its check are `io.runtime.manifest.validation.PluginManifestSchema` in runtime-manifest, which the
  runtime's loader and the plugin registry both use.
- The no-argument constructor of platform-identity's and platform-tenant's `SnowflakeIdGenerator`, which guessed a
  worker id from the `WORKER_ID` or `HOSTNAME` environment variable, the MAC address or at random. Nothing called it:
  both modules pass the worker id they are configured with (`brix.identity.worker-id`, `brix.tenant.worker-id`).
- platform-identity's Spring event `BootstrapCompletionListener.IdentitySetupCompletedEvent` and its
  `@EventListener`: the platform setup already closed Bootstrap with a direct call, and the event made the listener
  check again in the same transaction, where it could only find the work done. The direct call stays.
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
  `PermissionAspect`, `AuthenticatedUser.hasRole` and `hasAnyRole`, and `SecurityContextHolder.hasRole`. Entries are
  authorized by the dispatcher, from the permission IDs their descriptor declares.
- platform-auth's annotation-based authorization, which nothing used either: `@RequirePermission`, `@Anonymous` and
  `PermissionAspect`, with platform-auth-servlet's `AuthorizationExceptionAdvice` and the two beans that registered
  them. The aspect was the last code that honoured `platform:bypass`, which super-admin forbids (not seeded, issued or
  checked). platform-auth no longer depends on `spring-boot-starter-aspectj`.
- The codes of `PlatformPermissions` outside the catalog: `BYPASS_PERMISSION_CHECK`, `TENANT_MANAGE`, `TENANT_VIEW`,
  `ADMIN_MANAGE`, `AUDIT_VIEW`, `SYSTEM_CONFIG`, `LICENSE_READ`, `DATA_RECOVERY`, `BOOTSTRAP_READ` and
  `BOOTSTRAP_CREATE_FIRST_ADMIN`. Their IDs are retired and never reused. `PlatformPermissions` keeps
  `BOOTSTRAP_ROLE` and `defaultPermissionsFor`, which gives a bootstrap role nothing: a bootstrap session gets its
  permissions from the identity owner when it is opened.

### 🐛 Fixed

- **The frontend architecture lint reports what its rules mean** (standard-link plan queue item 19).
  - Red Line 8 refuses Node's `events` and `node:events` by their module names only. It used a pattern as well, which
    also refused a module's own `./events` file, where the plugin shared packages, platform-shared and
    runtime-sdk-api-web keep their event types.
  - The Page/Hook/Repository rule (`@brix-architecture/frontend-layer-boundary`) takes `HttpCapability` from the
    mobile contract, `@brix-sdk/runtime-sdk-api-mobile`, as it does from the web one: a mobile Repository that called
    the capability was reported as calling HTTP without one.
  - `@brix-sdk/eslint-config-architecture` has presets that work from a package's own directory: `configs.ui` for UI
    code of the same JavaScript permission domain (plugin packages and platform consoles) and `configs.platform` for
    every other frontend package. Each parses TypeScript with `@typescript-eslint/parser` 8, matches files relative to
    the package and reports an `eslint-disable` comment that disables nothing. The default export selected its BrixUI,
    design token, role literal, test id and accessibility blocks by repository paths, which never matched when a
    package linted its own directory, and set no parser. In `configs.ui` the BrixUI patterns are part of every
    layer's `no-restricted-imports`, where a separate block used to replace the layer patterns. The package's peer
    range for ESLint is now `^8.57.0 || ^9.0.0`; the presets are tested on both.
  - platform-admin-web and platform-tenant-web use `configs.ui`. In platform-admin-web the test id rule now reaches
    `src/` and four action buttons get a `data-testid`.
  - `configs.contract`, for the Layer 2A contracts: contract layer purity in `src/types/**`, which the default export
    selected by the path `**/runtime-sdk-api-web/src/types/**`, and React for types only. runtime-sdk-api-web and
    runtime-sdk-api-mobile lint with it; before, they had no lint at all, and nothing stopped a value import of React,
    which type-checks and makes the published contract need React at run time.
  - Every package with TypeScript sources lints with a preset: the platform consoles with `configs.ui`, the two
    contracts with `configs.contract` and the other 35 with `configs.platform`, among them the two UI adapters that
    the Lint job had skipped since 2026-05. Their lint scripts are `eslint src`; `--ext` is not an option of flat
    config. The Lint job fails a package that has TypeScript sources under `src/` but no lint script, or a lint script
    but no ESLint configuration; it used to list the second kind and go on. Eight eslint-disable comments named rules
    of plugins no configuration loads and one disabled nothing: they are gone. The deprecated role API's own
    implementation (`useAuth` in runtime-sdk-react; `PermissionGate`, `useAuth` and `useRole` in platform-auth-ui-web)
    says why it still calls `hasRole`, until 4.0.0 removes it.
  - create-brix's governed UI template ships `eslint.config.js` with `configs.ui`, a `lint` script and the rule
    package and ESLint as dev dependencies, and `test:phase7` lints the generated UI sources with the preset. The
    template's `routes.ts` held JSX, which neither ESLint nor `tsc` parses in a `.ts` file: it is `routes.tsx` now. The
    frozen legacy templates are unchanged.
  - Red Line 1 refuses the infrastructure adapters by the scope they are published under, `@brix-sdk/infra-adapter-*`.
    It named the old `@brix/` scope and matched no real import. `configs.ui` and the default export carry it;
    `configs.platform`, which a runtime profile that assembles the adapters uses, does not.
- **CI runs the architecture checks it skipped.** The Architecture Check workflow ran its checks as steps of one job,
  behind a Maven dependency analysis that could only fail on a fresh runner: it named one module without the modules it
  depends on, and it never compiled, so it had never analysed anything either. The governance and guard tests, the
  platform admin redline scan, the ESLint architecture rule tests, the lint and the type check behind it had not run on
  `main` since 2026-08-03. Each check is now a job of its own, so a failing check no longer hides the others, and the
  dependency analysis is gone. A failed Java test, ESLint rule test or redline finding is also written as a GitHub
  annotation, which can be read through the API without signing in.
  - Every push to `main` runs Architecture Check and Code Coverage, whatever it changes. Both used to run only for some
    file types, so a push of resources alone, such as a guard allow-list, a contract lock, a manifest or a migration,
    ran no architecture test. A later push no longer cancels an earlier push's run: each push has a result of its own.
  - The lint job lints the packages that have an ESLint configuration, today platform-admin-web and
    platform-tenant-web, and lists the 20 packages that have none: their `lint` stopped at "couldn't find a
    configuration file" before checking anything, and failed the job on every run. They get a configuration with the
    frontend architecture lint (queue item 19).
  - The type check builds the declarations platform-hosted-login-web imports before it runs: platform-design-tokens,
    then infra-adapter-ui-native and platform-i18n-web. It used to stop at that package with "cannot find module".
  - platform-admin-web's architecture test (no Page, Hook or Repository entry point; Pages reach Repositories only
    through Hooks; no network calls in Hooks and Pages) runs on every push as a job of its own. It ran only in the
    pull request coverage job, whose failures never failed the job.
  - The scaffold job runs create-brix's governed Phase 7 smoke (`test:phase7`): the plugin, operational module and UI
    templates generate no controller, scan, broker or JDBC client, fetch, UI vendor or placeholder, and ship their
    manifests and contract lock test. It used to run the legacy `create-brix app` entry, disabled since Runtime Shell
    v3.0.10 Phase 0, and failed on every run.
  - The "Runtime Shell v3.0.10 Phase 0 Guard" job is gone. It could not pass on a fresh runner: it built
    runtime-sdk-api without the modules it depends on, and one of its greps looked for a phrase the manifest schema
    never contained. runtime-sdk-api's tests run in Code Coverage with the whole build; the v3.0.10 root is superseded.
  - The 13 workflow files under package directories (`packages/@brix/*/.github/workflows/`) are gone. GitHub runs
    only the workflows at the repository root, so none of them ran after the packages moved into this repository.
    Their build, test, lint and dependency cycle checks run in the root workflows, and their release and API
    documentation jobs are not how Brix is published. The governance registry names the root Architecture Check as
    the CI evidence for the guard self-tests.
- **A body that is not JSON text is an invalid request, not a server error.** The host used to fail on it before the
  dispatcher, for any caller: the enterprise host answered 500 `PLATFORM-S-001` and logged an error with a stack. A
  call that passes the access checks now gets 400 `endpoint.invalid_request` with one violation, path `$` and rule
  `json`; a call without a verified caller gets 401, one the entry refuses 403.
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
  - The operational profile looked only at packages named `operational` and passed when there were none, and no
    module ran it: platform-admin's handlers and request types went unchecked. It now checks every class of the
    module and fails on a module with none. It also refuses the runtime's implementation outside the operational SPI,
    and any platform class outside the module other than a Data Owner's internal contracts and published api (root
    BRX-RS-LAYER-015, LAYER-016). platform-admin and platform-auth-operational run it.
  - The platform plugins run the plugin profile, by their `moduleKind`, and the commons profile: platform-audit's
    three artifacts first. The plugin rules do not count a platform plugin as a plugin, so a test keeps platform-audit
    off other platform plugins' implementations, as identity's and tenant's do.
  - platform-identity, platform-tenant and platform-tenant-api run the plugin profile and the commons profile too.
    Their Spring auto-configuration still reads five settings with `@Value`; those five members are listed in
    `META-INF/brix/guard/spring-value-readers.txt` of the two modules under exemption EX-2026-009, and any other
    `@Value` fails.
  - platform-auth runs the plugin profile next to the commons profile (its one `@Value` member is listed under the
    same exemption) and a boundary test like the others'; platform-auth-api runs both profiles, and
    platform-notification, which has no plugin manifest, the commons profile. Every platform module now runs the
    profile of its `moduleKind`.
  - The plugin profile's `@EventListener` rule passes a module without methods, such as an api of constants: it used
    to fail it as "no classes checked". The rules about classes still fail a module without classes.
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
