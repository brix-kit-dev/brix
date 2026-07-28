# infra-adapter-outbox

Broker-neutral canonical Outbox persistence mapping for Runtime Shell M3.

## Layer

This module is an L2C persistence adapter. It implements the Runtime Shell
internal `OutboxMessageStore` port with JDBC and touches only the configured
canonical Outbox table.

## Responsibilities

- Claim due `CRITICAL` / `STANDARD` canonical Outbox records with conditional
  lease ownership.
- Reclaim expired `IN_FLIGHT` leases.
- Finalize `PUBLISHED`, release `PENDING` retries, and park exhausted or
  permanent failures.
- Report low-cardinality backlog, in-flight, parked, and oldest pending age
  snapshots.

## Boundaries

- Does not publish to Kafka or any broker.
- Does not own a Data Owner schema or migration.
- Does not read business tables.
- Does not expose Outbox or Relay APIs to plugins.
- Does not decide event reliability; reliability is already encoded by
  Manifest and L2B producer policy before records are committed.
