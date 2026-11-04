---
id: event-driven-plugin
title: Event-Driven Plugin Example
sidebar_label: Event-Driven Plugin
sidebar_position: 3
---

# Event-Driven Plugin Example

This example walks through two plugins in the repository that talk through events:

| Plugin | Role |
|---|---|
| `sample-orders` | Takes orders over HTTP and publishes what happened |
| `sample-shipping` | Prepares a shipment for each order |

The build runs them end to end on PostgreSQL and Kafka (`sample-e2e`). Neither plugin knows the other's code, the
broker or any table but its own.

The code lives in `packages/@brix/samples/`. For the rules behind each step, see
[Events and Commands](../guides/events-and-commands.md); for the guarantees, see the
[Event Model](../concepts/event-model.md).

## Scenario

1. A tenant member calls `POST /api/sample-orders/orders` with `{"sku": "sku-1", "quantity": 2}` and an
   `Idempotency-Key`. Repeating the call gets the same order back
   ([Repeating a call](../guides/endpoints.md#repeating-a-call)).
2. `sample-orders` stores the order and publishes two events:
   - `SampleOrderPlaced` (STANDARD). It is written to the plugin's outbox in the same transaction, so it exists if
     and only if the order does.
   - `SampleOrderActivity` (BEST_EFFORT). It is sent after the commit, for an activity feed, with no guarantee.
3. The relay publishes `SampleOrderPlaced` to Kafka.
4. `sample-shipping` receives it in the order's tenant and prepares one shipment. Its inbox records the event in the
   same transaction, so a redelivery changes nothing.
5. The member reads the shipment from `sample-shipping`'s own endpoint.

## Layout

Each plugin is three artifacts, as every plugin with a backend is:

| Artifact | Holds | Depends on |
|---|---|---|
| `*-api` (`plugin-api`) | What the plugin offers: payload and endpoint records, JSON schemas | Nothing |
| `*-core` (`plugin-core`) | Domain logic, entities, repositories, event classes | Its api, `runtime-sdk-api`, JPA |
| `*-server` (`plugin-server`) | Plugin class, handlers, manifest, migrations | Its core, `runtime-sdk-api` |

```text
samples/
├── sample-orders-api/                               publisher's contract
│   └── src/main/
│       ├── java/io/brix/samples/orders/api/
│       │   ├── SampleOrderEvents.java               event type names
│       │   ├── SampleOrderPlacedPayload.java        payloads, as their schemas describe them
│       │   ├── SampleOrderActivityPayload.java
│       │   ├── PlaceOrderRequest.java               endpoint request and response
│       │   └── PlacedOrder.java
│       └── resources/META-INF/brix/schemas/sample-orders/…/1.0.0.json
├── sample-orders-core/
│   └── src/main/java/io/brix/samples/orders/core/
│       ├── OrderPlacement.java                      rules, unit of work, publishing
│       ├── InvalidOrderException.java
│       ├── SampleOrder.java                         entity (tenant_id, assigned id)
│       ├── SampleOrderRepository.java
│       ├── SampleOrderPlaced.java                   STANDARD event
│       └── SampleOrderActivity.java                 BEST_EFFORT event
├── sample-orders-server/
│   └── src/main/
│       ├── java/io/brix/samples/orders/server/
│       │   ├── SampleOrdersPlugin.java              binds the endpoint handler
│       │   └── PlaceOrder.java                      the endpoint handler
│       └── resources/
│           ├── META-INF/services/io.runtime.sdk.plugin.BrixPlugin
│           ├── META-INF/brix/plugin-manifest.yaml
│           └── db/migration/sample-orders/V1__sample_orders.sql   business table only
├── sample-shipping-api/                             ShipmentView and its schemas
├── sample-shipping-core/                            OrderPlaced, SampleShipment, SampleShipmentRepository,
│                                                    ShipmentDesk
├── sample-shipping-server/                          SampleShippingPlugin, PrepareShipment, FindShipment,
│                                                    manifest, migration
└── sample-e2e/                                      a host made of configuration, and the end-to-end test
```

`sample-shipping` depends on no `sample-orders` artifact. It reads the event as `OrderPlaced`, its own record of
the published schema, so the two plugins share only the schema.

Neither plugin creates its outbox or inbox: the manifests declare `data.outbox` and `data.inbox`, and the platform
creates `sample_orders_outbox` and `sample_shipping_inbox` from its own versioned migrations.

## The publisher

The manifest lists the three artifacts, and declares the endpoint, who may call it, the two events and the data the
plugin owns:

```yaml title="sample-orders-server: META-INF/brix/plugin-manifest.yaml (excerpt)"
modules:
  - groupId: io.brix.samples
    artifactId: sample-orders-api
    version: 3.2.0
    moduleKind: plugin-api
  - groupId: io.brix.samples
    artifactId: sample-orders-core
    version: 3.2.0
    moduleKind: plugin-core
  - groupId: io.brix.samples
    artifactId: sample-orders-server
    version: 3.2.0
    moduleKind: plugin-server
endpoints:
  provides:
    - endpointId: sample-orders.place.v1
      transport: http
      method: POST
      path: /api/sample-orders/orders
      request:
        schemaRef: brix://schemas/sample-orders/place-order-request/1.0.0
      response:
        schemaRef: brix://schemas/sample-orders/placed-order/1.0.0
      handlerId: sample-orders.place.v1
      authentication:
        mode: required
        policyRef: tenant-actor
      authorization:
        permissions:
          - sample-orders:order:place
        decision: all
        resourcePolicyRef: sample-orders-place
      tenantContext:
        mode: required
        source: runtime
      timeout:
        policyRef: endpoint-default
      idempotency:
        mode: required
events:
  publishes:
    - eventType: SampleOrderPlaced
      schemaRef: brix://schemas/sample-orders/sample-order-placed/1.0.0
      version: 1.0.0
      reliability: STANDARD
    - eventType: SampleOrderActivity
      schemaRef: brix://schemas/sample-orders/sample-order-activity/1.0.0
      version: 1.0.0
      reliability: BEST_EFFORT
data:
  storageId: sample_orders
  schema: sample-orders
  migrationLocation: db/migration/sample-orders
  outbox: sample_orders_outbox
```

The plugin class binds the handler. The runtime creates it after the plugin started, with the capabilities the
manifest requires:

```java title="sample-orders-server: SampleOrdersPlugin.java"
@Override
public void configure(PluginBootstrapContext bootstrap) {
    bootstrap.bindEndpointHandlerFactory(PLACE_ORDER, context -> new PlaceOrder(new OrderPlacement(
        context.require(PersistenceCapability.class),
        context.require(EventBusCapability.class),
        context.require(IdGeneratorCapability.class),
        Clock.systemUTC())));
}
```

The runtime has already done four things before the handler runs:
- checked the body against `sample-orders/place-order-request` and read it as `PlaceOrderRequest`;
- authenticated the caller;
- checked the `sample-orders:order:place` permission;
- bound the tenant.

The handler only adapts HTTP to the domain service, and the runtime answers `201`, as the manifest declares:

```java title="sample-orders-server: PlaceOrder.java"
@Override
public CompletionStage<PlacedOrder> handle(EndpointInvocation<PlaceOrderRequest> request) {
    long tenantId = Long.parseLong(request.tenantId().orElseThrow());
    try {
        SampleOrder order = orders.place(tenantId, request.body());
        return CompletableFuture.completedFuture(new PlacedOrder(String.valueOf(order.getId()), order.version()));
    } catch (InvalidOrderException invalid) {
        throw new SampleOrdersException(SampleOrdersException.Code.ORDER_INVALID);
    }
}
```

`OrderPlacement` in the core checks the order's rules, then writes the order and publishes both events in one unit of
work:

```java title="sample-orders-core: OrderPlacement.java"
public SampleOrder place(long tenantId, PlaceOrderRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    if (request.sku() == null || request.sku().isBlank() || request.sku().length() > MAX_SKU) {
        throw new InvalidOrderException("An order needs a sku of at most " + MAX_SKU + " characters");
    }
    if (request.quantity() < 1 || request.quantity() > MAX_QUANTITY) {
        throw new InvalidOrderException("An order needs a quantity from 1 to " + MAX_QUANTITY);
    }
    return persistence.unitOfWork().required(() -> {
        SampleOrder placed = SampleOrder.place(Long.parseLong(ids.generate()), tenantId, request.sku(),
            request.quantity(), OffsetDateTime.now(clock));
        persistence.repository(SampleOrderRepository.class).save(placed);
        events.publishIntegration(new SampleOrderPlaced(placed));
        events.publishIntegration(new SampleOrderActivity(placed, "placed"));
        return placed;
    });
}
```

The events name their manifest type, their partition key and their payload, a record from the api. The tenant passed
to the constructor makes them TENANT events:

```java title="sample-orders-core: SampleOrderPlaced.java (excerpt)"
SampleOrderPlaced(SampleOrder order) {
    // The tenant makes it a TENANT event; the runtime binds the publisher itself.
    super(SampleOrderEvents.PUBLISHER, String.valueOf(order.tenantId()));
    this.payload = new SampleOrderPlacedPayload(order.tenantId(), String.valueOf(order.getId()), order.sku(),
        order.quantity(), order.version());
}

@Override
public String eventTypeId() {
    return SampleOrderEvents.PLACED;      // "SampleOrderPlaced"
}

@Override
public String getRoutingKey() {
    return "order." + payload.orderId();  // events of one order stay in order
}
```

`SampleOrdersEventSchemasTest` checks both payload records against their JSON schemas, so a drifting payload fails
the publisher's build.

## The subscriber

```yaml title="sample-shipping-server: META-INF/brix/plugin-manifest.yaml (excerpt)"
events:
  subscribes:
    - subscriptionId: prepare-shipment
      eventType: SampleOrderPlaced
      schemaRange: ">=1.0.0 <2.0.0"
      handlerId: sample-shipping.prepare-shipment.v1
      scopePolicyRef: sample-shipping-order-tenant
      publisherPolicyRef: sample-orders-owner
      timeoutPolicyRef: event-standard
      concurrencyPolicyRef: partition-serial
      retryPolicyRef: event-standard
      idempotencyPolicyRef: persistent-inbox
data:
  storageId: sample_shipping
  schema: sample-shipping
  migrationLocation: db/migration/sample-shipping
  inbox: sample_shipping_inbox
```

The handler reads the event as its own record and hands it to the core's `ShipmentDesk`:

```java title="sample-shipping-server: PrepareShipment.java"
final class PrepareShipment implements EventHandler<OrderPlaced> {

    @Override
    public CompletionStage<Void> handle(EventInvocation<OrderPlaced> invocation) {
        shipments.prepare(invocation.event().payload());
        return CompletableFuture.completedFuture(null);
    }
}
```

The runtime has opened a unit of work in the event's tenant, and `required(...)` joins it. The shipment and the
inbox receipt therefore commit together:

```java title="sample-shipping-core: ShipmentDesk.java"
public SampleShipment prepare(OrderPlaced order) {
    Objects.requireNonNull(order, "order must not be null");
    return persistence.unitOfWork().required(() -> persistence.repository(SampleShipmentRepository.class)
        .save(SampleShipment.prepare(Long.parseLong(ids.generate()), order, OffsetDateTime.now(clock))));
}
```

If the handler throws, the shipment and the inbox receipt roll back and the event is retried. After the last
attempt it is parked for an operator.

`sample-shipping` also shows each order's shipment at `GET /api/sample-shipping/shipments/{orderId}`. The endpoint
requires the `sample-shipping:shipment:read` permission, and its answer is `ShipmentView` from `sample-shipping-api`:

```java title="sample-shipping-server: FindShipment.java"
@Override
public CompletionStage<Shipment> handle(EndpointInvocation<FindShipmentRequest> request) {
    long tenantId = Long.parseLong(request.tenantId().orElseThrow());
    return CompletableFuture.completedFuture(shipments.find(tenantId, request.body().orderId())
        .orElseThrow(() -> new SampleShippingException(SampleShippingException.Code.SHIPMENT_NOT_FOUND)));
}
```

An order without a shipment answers 404 `sample-shipping.shipment_not_found`: the code's category is `NOT_FOUND`.
See [Endpoints](../guides/endpoints.md).

## Running it end to end

`sample-e2e` starts both plugins in a host made only of auto-configurations and properties:
- PostgreSQL in a fresh schema;
- an embedded Kafka broker with topic auto-creation off;
- the Kafka transport.

It places an order over HTTP and then checks:
- the order's event reached the outbox and was published;
- `sample-shipping` prepared exactly one shipment in tenant 42 and recorded the event in its inbox;
- the member reads the shipment over HTTP, and an unknown order answers 404;
- the BEST_EFFORT event reached its topic without the outbox;
- a refused request changed nothing;
- the messaging tables came from the platform.

It needs a throwaway PostgreSQL, and is skipped without one:

```bash
BRIX_P0_POSTGRES_URL=jdbc:postgresql://127.0.0.1:5432/scratch BRIX_P0_POSTGRES_USER=scratch BRIX_P0_POSTGRES_PASSWORD=scratch mvn -pl :sample-e2e -am verify -Dtest=SamplePluginsEndToEndTest -Dsurefire.failIfNoSpecifiedTests=false
```

The host properties the test sets show what a host needs for this chain:
- `brix.outbox.relay.enabled`;
- `brix.outbox.relay.persistence.enabled`;
- `brix.outbox.relay.transport=kafka`;
- `brix.infra.kafka.bootstrap-servers`;
- the required plugins;
- `EventBusCapability` and `IdGeneratorCapability` under `brix.capability.required`.

## Extending it

- **Another subscriber.** Declare the same event under `events.subscribes` with a new `handlerId`. It gets its own
  consumer group and inbox rows, and starts with the events published from then on.
- **A new field.** Add it as optional in `sample-order-placed/1.1.0`, publish 1.1.0, and keep subscribers on
  `>=1.0.0 <2.0.0`. They ignore the field until they need it.
- **A cancellation.** Publish `SampleOrderCancelled` with the order's next `version`. A subscriber that receives
  both event types keeps the shipment's `order_version` and ignores anything older.
- **Asking another plugin to act.** Send the owner's typed command rather than an event, as `sample-shipping` does
  when a shipment is refused: `CommandCapability.client(SampleOrdersCommands.CANCEL_ORDER)`. It answers with the
  command id, and `GET /api/sample-shipping/cancellations/{commandId}` reads where that command stands. A refusal may
  name a reason, `REFUSED` or `DAMAGED`; refusing the same order again with the other one reads as `CONFLICT`.
  `sample-orders` rejects the cancellation of an order it does not have: that one reads as `REJECTED` with
  `sample-orders.order_not_found`. See [Commands](../guides/events-and-commands.md#commands).
