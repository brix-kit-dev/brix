# M0 Phase 4 Anchor And Contract Freeze

> Date: 2026-07-28
> Scope: Runtime Shell 3.0.10 Outbox reliability M0
> Status: M0 evidence artifact; not Implementation Accepted

## Baseline

Architecture baseline validation passed with:

- `runtime-shell=3.0.10` ACTIVE
- `frontend=1.1` ACTIVE
- `super-admin=3.0` ACTIVE
- `multi-tenant=4.0` ACTIVE
- `runtime-shell=3.0.11` CANDIDATE and non-guiding

Guiding documents:

- `../brix-enterprise/docs/v3.0.10-运行壳架构设计蓝图.md`
- `../brix-enterprise/docs/v3.0.10-Outbox数据可靠性最小实现改造计划.md`
- `../brix-enterprise/docs/v3.0-平台超管功能最小实现-设计蓝图（冻结）.md`
- `../brix-enterprise/docs/v4.0-多租户功能最小实现-设计蓝图（冻结）.md`
- `../brix-enterprise/docs/v1.1-前端架构-设计蓝图（冻结）.md`

The Outbox plan is treated as guiding for this M0 request because the request
explicitly selected it as the construction plan. It remains lower authority than
the ACTIVE root and specialized blueprints.

## Phase 4 Anchor

Current immutable repository heads observed for this M0 slice:

- `brix`: `6132993` (`架构模型升级前未测试版本`)
- `brix-enterprise`: `02f308b` (`feat(phase0): freeze pre-release evidence baseline`)

Current `brix` worktree is not clean. The dirty scope includes
`platform-admin`, `platform-tenant`, `runtime-orchestrator`,
`runtime-manifest`, `runtime-sdk-api`, `platform-devtools/schemas`,
`architecture-guard`, `platform-notification` and email adapter changes.

Current `brix-enterprise` worktree has an untracked copy of the Outbox plan.

M0 conclusion:

- No existing dirty worktree state may be called `Accepted`.
- Phase 4 and Outbox changes are not proven as an independent immutable commit
  in the current `brix` worktree.
- This M0 slice adds only support and contract evidence files. It does not
  modify the existing dirty implementation files and does not reclassify them as
  accepted.

## Migration Decision

Observed current candidate file:

- `packages/@brix/platform-commons/packages/server/platform-tenant/src/main/resources/db/migration/platform-tenant/V021__phase3_first_owner_internal_contract.sql`

It is untracked in the current worktree and contains early
`platform_tenant_outbox` and `platform_tenant_inbox` definitions. Those
definitions do not yet match the M0 frozen canonical contract because they do
not contain the full envelope, lease, retry and error fields required by the
Outbox plan.

M0 decision:

- This slice does not edit `V021`.
- Unless a deployment ledger proves `V021` has never been shared, retained,
  released, migrated or used as a candidate artifact, later schema correction
  must use the next monotonic forward Flyway migration.
- The required target fields are frozen in
  `packages/@brix/platform-devtools/schemas/outbox-message-contract.v1.json`.

## Frozen Contract

The M0 frozen contract file is:

- `packages/@brix/platform-devtools/schemas/outbox-message-contract.v1.json`

Frozen reliability levels:

- `CRITICAL`
- `STANDARD`
- `BEST_EFFORT`

Only `CRITICAL` and `STANDARD` are durable at-least-once levels. `BEST_EFFORT`
must not claim at-least-once and must not drive correctness-critical state.

Frozen canonical envelope fields:

- `eventId`
- `eventType`
- `schemaVersion`
- `occurredAt`
- `producerPluginId`
- `scope`
- `tenantId`
- `correlationId`
- `causationId`
- `traceparent`
- `tracestate`
- `partitionKey`
- `payload`

Frozen canonical Outbox fields:

- `message_id`
- `message_kind`
- `message_type`
- `schema_version`
- `reliability`
- `producer_plugin_id`
- `scope`
- `tenant_id`
- `partition_key`
- `occurred_at`
- `correlation_id`
- `causation_id`
- `traceparent`
- `tracestate`
- `payload`
- `status`
- `available_at`
- `attempt_count`
- `claim_owner`
- `claim_until`
- `published_at`
- `last_error_code`
- `created_at`

Frozen Outbox states:

- `PENDING`
- `IN_FLIGHT`
- `PUBLISHED`
- `PARKED`

Frozen canonical Inbox fields:

- `handler_id`
- `message_id`
- `message_kind`
- `message_type`
- `schema_version`
- `processed_at`

Frozen Inbox primary key:

- `(handler_id, message_id)`

## TenantFirstOwnerAccepted

Frozen event contract:

- event type: `TenantFirstOwnerAccepted`
- schema version: `1.0.0`
- reliability: `CRITICAL`
- producer plugin: `platform-tenant`
- scope: `TENANT`
- storage ID: `platform_tenant`
- partition key: `tenantId`

Frozen payload fields:

- `tenantId`
- `memberId`
- `profileId`
- `acceptedByIdentityId`
- `acceptedAt`
- `tenantStatus`

The event is a past-tense fact. It must be emitted only after the FIRST_OWNER
acceptance business state is part of the same Owner local transaction as the
canonical Outbox record. It must not be implemented as a direct Kafka topic
send, direct cross-plugin call, platform-admin repository write or Host branch.

## Transport Decision

Kafka is the first L2C transport mapping for the later delivery slice. CDC is
deferred. Kafka topic names are not business contract fields; transport routing
is resolved later by L2B from Manifest and Schema Registry.

## Dependency Graph

Target producer path:

```text
L1 platform-tenant
  -> L2A EventBusCapability
     -> L2B reliable event policy and canonical append
        -> Data Owner canonical platform_tenant_outbox
```

Target relay path:

```text
L2B OutboxRelay
  -> L2C canonical store claim/finalize mapping
  -> L2C Kafka transport publish
```

Forbidden dependencies:

- Plugin to L2B runtime implementation
- Plugin to L2C Kafka, Redis, MinIO, HTTP, JWT, Feign or database infrastructure
- Plugin to another plugin entity, repository or mapper
- Host source code importing business types or implementing Outbox behavior
- `platform-admin` or an internal contract consumer directly writing Owner
  Outbox repositories

## Current Gap Register

- The observed `platform-tenant` Manifest declares `TenantFirstOwnerAccepted`
  with id and version only; M1 must add reliability and policy fields through
  schema and validator changes.
- The observed `FirstOwnerInvitationService` writes
  `PlatformTenantOutboxRepository` directly; M2 must replace that with
  Owner-scoped `EventBusCapability`.
- The observed `V021` outbox table lacks the full canonical field set and lease
  model; later migration work must be forward-only unless a deployment ledger
  proves in-place edit safety.
- The observed `V021` inbox table is a minimal unique store; M4 must align it
  with Consumer Owner transaction semantics and event dispatcher behavior.
- Existing Kafka Outbox/AOP assets remain non-authoritative for the target
  reliable chain; later milestones must retire or isolate them.

## Validation

Executable M0 guard:

```text
mvn -pl packages/@brix/platform-devtools/architecture-guard test
```

The guard checks that the frozen M0 contract file still contains the required
runtime-shell clauses, reliability levels, canonical fields, state names,
`TenantFirstOwnerAccepted` anchors and Kafka/CDC transport decision.

Full Runtime Outbox acceptance is intentionally not claimed by M0. Producer
transaction tests, Relay fault tests, persistent Inbox duplicate tests, Host
runtime tests and authenticated HTTP/E2E evidence belong to M1-M4.
