# infra-adapter-outbox

> **Status**: 🚧 WIP (Work In Progress)  
> **Target**: v4.0  
> **Purpose**: Transactional Outbox pattern adapter for reliable event publishing

This module will implement the **Transactional Outbox** pattern as an
infrastructure adapter, ensuring cross-service event consistency for
critical business events (Red Line 13 — Blueprint v3.0.9).

## Architecture

```text
Plugin Domain Layer
    ↓ publishes DomainEvent
EventBusCapability (Layer 2A contract)
    ↓ delegates to
infra-adapter-outbox (this module)
    ↓ persists to outbox table in same DB transaction
Outbox Poller / CDC
    ↓ relays to
infra-adapter-kafka (Kafka producer)
```

## Planned Implementation

- `OutboxEventPublisher` — writes events to `*_outbox` table within the
  current transaction boundary
- `OutboxPoller` — background task that reads unpublished events and
  forwards them to the Kafka adapter
- Schema: see `create-brix` template `V001__init.sql.ejs` for the
  outbox table DDL

## Dependencies

- `infra-adapter-kafka` — downstream event relay
- `infra-adapter-database` — transaction management
- `runtime-sdk-api` — `EventBusCapability` contract

## Related

- Architecture Guard: `OutboxConsistencyRule.java`
- Blueprint v3.0.9 Red Line 13: Cross-Service Event Consistency
