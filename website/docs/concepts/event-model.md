---
id: event-model
title: Event Model
sidebar_label: Event Model
sidebar_position: 4
---

# Event Model

Plugins never call each other and never share data. They exchange two kinds of messages over one platform chain:

| Message | Means | Receivers |
|---|---|---|
| **Integration event** | A fact that happened in the publisher's data, such as "an order was placed" | Any number of subscribers, each for its own reasons |
| **Command** | A request that the one plugin owning some data changes it, such as "reserve this stock" | Exactly one provider |

Everything about a message is declared in the plugin manifest: its type, payload schema, reliability, who may
publish it and who subscribes. Plugin code only publishes, submits and handles. How a message moves (outbox, relay,
broker, inbox) is the runtime's job, and the host chooses the transport without any plugin changing.

For how to write publishers, subscribers and commands, see [Events and Commands](../guides/events-and-commands.md).
For a working pair of plugins, see the [Event-Driven Plugin Example](../examples/event-driven-plugin.md).

## The envelope

Every message travels in the same canonical envelope, built by the runtime rather than by the plugin:

| Field | Meaning |
|---|---|
| `eventId` / `messageId` | Unique id; it never changes, including on retry, replay or republish |
| `messageKind`, `messageType`, `schemaVersion` | EVENT or COMMAND, the declared type, and the version of its schema |
| `producerPluginId` | The plugin that published it, bound by the runtime from the publishing code |
| `scope`, `tenantId` | TENANT with its tenant, or PLATFORM with none; the tenant must be one the publishing unit of work reaches |
| `partitionKey` | The key whose order subscribers see within the type (the event's routing key) |
| `correlationId`, `causationId`, `traceparent` | Tracing across plugins |
| `payload` | The JSON the schema describes |
| `reliability` | CRITICAL, STANDARD or BEST_EFFORT, from the manifest |

## Reliability

| Level | Path | Promise |
|---|---|---|
| CRITICAL, STANDARD | Written to the publisher's outbox in the same local transaction as its business change; a relay sends it after the commit | Delivered at least once, and only if the business change committed |
| BEST_EFFORT | Sent directly once the unit of work has committed | No retry and no promise; for signals nothing depends on |

## Delivery and idempotency

At-least-once delivery means a subscriber may receive a message more than once. With a persistent inbox, the runtime
records `(handlerId, eventId)` in the subscriber's own transaction, together with what the handler changed. A
duplicate finds the receipt and is skipped, so each handler applies each event once.

A command is deduplicated twice:
- by its command id, in the provider's inbox;
- by the sender's business idempotency key, in the provider's idempotency table.

## Ordering

A subscriber sees the events of **one event type with the same partition key** in publish order, across retries and
restarts. Nothing else is ordered:
- not events of different types;
- not different keys;
- not the platform as a whole.

When one aggregate publishes several event types and a subscriber needs their order, the publisher puts the
aggregate's version in each payload and the subscriber ignores anything older than what it has seen.

## Contracts and versions

- **One publisher.** Each event type has exactly one publishing plugin. Subscribers name it with
  `publisherPolicyRef: <pluginId>-owner`, and the runtime checks it at startup.
- **Versioned schemas.**
  - Each payload has a versioned JSON schema in the publisher's artifact (`brix://schemas/<plugin>/<name>/<version>`).
  - Subscribers accept a `schemaRange`. A message outside it is parked rather than misread.
  - Compatible changes add optional fields in a minor version. Breaking changes need a new major version that
    subscribers opt into.
- **Startup checks.** The runtime refuses, at startup, a plugin whose declarations do not resolve:
  - an unknown policy;
  - a missing schema file;
  - a subscription without its handler.

## Tenancy

- A TENANT message is handled in its tenant, and a PLATFORM message in no tenant.
- The tenant comes from the publisher's unit of work, never from what the message says about itself. A plugin cannot
  publish into a tenant it is not working in.

## Failures

| Situation | What happens |
|---|---|
| The handler throws or runs past its deadline | Its writes and the inbox receipt roll back. The message is retried with exponential backoff and jitter. |
| Retries run out, or a permanent error (undecodable payload, unsupported schema) | The message is **parked**. Later messages of the subscription go on. |
| The subscriber's database is down, the subscriber is not started, or the broker is unreachable | Delivery pauses without using up attempts, and resumes when the cause is gone. |

Operators handle parked messages on the host's management port:
- list them;
- replay or discard them, one at a time or a whole subscription's;
- republish a published message while the publisher's outbox still keeps it.

Each action records the operator and the reason in the platform audit before it takes effect.

## New subscriptions and retention

A subscription starts at the moment it is first registered; it does not receive older messages. History comes from
republishing messages that are still in the outbox. By default:

| Data | Kept for |
|---|---|
| Published and discarded outbox messages | 14 days |
| Inbox and command idempotency records | 30 days |
| The broker's topics | 7 days |

The host refuses to start if the inbox retention is shorter than the outbox retention plus the broker's. Parked
messages stay until an operator acts.

## Transports

- **Kafka**:
  - one topic per event type, `brix.event.<type>`, and per command type, `brix.command.<type>`;
  - one consumer group per subscription;
  - a compacted parking topic per subscription.
- **In-process**: the relay hands messages directly to subscribers in the same process. It suits a single-process
  host and tests.

Both transports pass the same conformance suite and give the same guarantees, so plugins behave the same on either.
In production the Kafka adapter requires:
- TLS with a client certificate;
- at least three replicas;
- two in-sync replicas.
