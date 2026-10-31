---
id: backend-development
title: Backend Development Guide
sidebar_label: Backend Development
sidebar_position: 3
---

# Backend Development Guide

This guide covers the Java side of a plugin:
- how the project is laid out;
- how code is bound to what the manifest declares;
- how handlers reach data, events and the other capabilities.

A plugin has no Spring context of its own: no controllers, no beans, no component scan. The runtime reads the
plugin's manifest, creates its handlers and calls them.

This guide quotes two sample plugins in the repository, and the test that runs them end to end. Each plugin is
three artifacts, laid out as [Project structure](#project-structure) describes:

| Sample | Path | What it shows |
|---|---|---|
| `sample-orders` | `packages/@brix/samples/sample-orders-api`, `-core`, `-server` | An HTTP endpoint and a domain service that stores an entity and publishes events |
| `sample-shipping` | `packages/@brix/samples/sample-shipping-api`, `-core`, `-server` | An event subscriber that stores its own entity, and an endpoint that reads it |
| `sample-e2e` | `packages/@brix/samples/sample-e2e` | Both plugins in a host made of configuration only, on PostgreSQL and Kafka |

## How the pieces fit

| Piece | What it does |
|---|---|
| `META-INF/brix/plugin-manifest.yaml` | Declares the plugin: endpoints, events, commands, capabilities, data and permissions |
| `META-INF/services/io.runtime.sdk.plugin.BrixPlugin` | Names the plugin class, so the runtime can find it |
| Plugin class (`BrixPlugin`) | Binds a handler factory to each handler ID the manifest declares |
| Handlers | `TypedEndpointHandler`, `EventHandler`, `CommandHandler`, `ManagedTask`: one per manifest entry, called by the runtime |
| Domain services | Plain classes with the business rules, created by the handler factories |
| Entities, repositories, migrations | The plugin's own tables, reached through `PersistenceCapability` |
| JSON schemas | The versioned contracts of requests, responses and events |

At startup the runtime:
1. finds the plugin class through the services file, and the manifest packaged with it;
2. validates the manifest and resolves the capabilities it requires; the plugin's migrations run before it starts;
3. calls `configure(...)` once, so that the plugin binds a handler factory to each declared handler ID; a binding
   to an ID the manifest does not declare is refused;
4. creates each handler through its factory, starts the plugin, and then opens its endpoints and subscriptions.

## Project structure

A backend plugin is three Maven artifacts. Two roles never share one artifact. The manifest records the role of each
under `modules`:

| Artifact | `moduleKind` | Holds | Must not |
|---|---|---|---|
| `sample-orders-api` | `plugin-api` | Public request, response and payload records; the JSON schemas | Contain logic or depend on a framework |
| `sample-orders-core` | `plugin-core` | Domain services, entities, repository interfaces, event classes | Know about HTTP or depend on the server artifact |
| `sample-orders-server` | `plugin-server` | The plugin class, handlers, manifest, services file and migrations | Hold business rules |

`sample-orders` is laid out this way:

```text
samples/
├── sample-orders-api/
│   └── src/main/
│       ├── java/io/brix/samples/orders/api/
│       │   ├── SampleOrderEvents.java             event type names
│       │   ├── SampleOrderPlacedPayload.java      event payloads, as their schemas describe them
│       │   ├── SampleOrderActivityPayload.java
│       │   ├── PlaceOrderRequest.java             endpoint request and response
│       │   └── PlacedOrder.java
│       └── resources/META-INF/brix/schemas/sample-orders/
│           ├── place-order-request/1.0.0.json
│           ├── placed-order/1.0.0.json
│           ├── sample-order-placed/1.0.0.json
│           └── sample-order-activity/1.0.0.json
├── sample-orders-core/
│   └── src/main/java/io/brix/samples/orders/core/
│       ├── OrderPlacement.java                    domain service, a plain class
│       ├── InvalidOrderException.java             a broken rule
│       ├── SampleOrder.java                       entity: assigned id, tenant_id
│       ├── SampleOrderRepository.java             Spring Data interface
│       ├── SampleOrderPlaced.java                 integration events
│       └── SampleOrderActivity.java
└── sample-orders-server/
    └── src/
        ├── main/
        │   ├── java/io/brix/samples/orders/server/
        │   │   ├── SampleOrdersPlugin.java        binds the handler factory
        │   │   └── PlaceOrder.java                endpoint handler
        │   └── resources/
        │       ├── META-INF/services/io.runtime.sdk.plugin.BrixPlugin
        │       ├── META-INF/brix/plugin-manifest.yaml
        │       └── db/migration/sample-orders/V1__sample_orders.sql
        └── test/java/io/brix/samples/orders/server/
            └── SampleOrdersEventSchemasTest.java
```

`sample-shipping` has the same three artifacts:
- `sample-shipping-api`: `ShipmentView` and its schemas;
- `sample-shipping-core`: `OrderPlaced`, `SampleShipment`, `SampleShipmentRepository` and the domain service
  `ShipmentDesk`;
- `sample-shipping-server`: `SampleShippingPlugin`, the handlers `PrepareShipment` and `FindShipment`, the manifest
  and the migration.

The manifest lives in the server artifact and lists all three:

```yaml title="sample-orders-server: META-INF/brix/plugin-manifest.yaml (excerpt)"
# Three artifacts (root BRX-RS-MANIFEST-005): the contract, the logic and data, the entry point and handlers.
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
```

The api and core artifacts carry no manifest. Their classes belong to the plugin because its manifest lists their
artifacts. This is how the runtime knows that `SampleOrderRepository`, in `sample-orders-core`, reaches this plugin's
tables.

The two plugins share no code. `sample-shipping` depends on no `sample-orders` artifact: it reads
`SampleOrderPlaced` as `OrderPlaced`, its own record of the published schema.

## Maven dependencies

The server depends on the core, the core on the api, and both server and core on the SDK. That is the whole
compile-time view a plugin has of the platform:

| Artifact | Dependencies |
|---|---|
| `sample-orders-api` | None |
| `sample-orders-core` | `sample-orders-api`, `runtime-sdk-api`, `jakarta.persistence-api`, `spring-data-jpa` |
| `sample-orders-server` | `sample-orders-core`, `runtime-sdk-api` |

The core holds the entity and the repository, so it is the artifact that needs JPA:

```xml title="sample-orders-core: pom.xml"
<dependencies>
    <dependency>
        <groupId>io.brix.samples</groupId>
        <artifactId>sample-orders-api</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>io.brix</groupId>
        <artifactId>runtime-sdk-api</artifactId>
        <version>${brix.version}</version>
    </dependency>
    <dependency>
        <groupId>jakarta.persistence</groupId>
        <artifactId>jakarta.persistence-api</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.data</groupId>
        <artifactId>spring-data-jpa</artifactId>
    </dependency>
</dependencies>
```

`brix.version` is `3.2.0`. The samples' parent imports the Spring Boot BOM (`spring-boot-dependencies`), which manages
the versions of the JPA and Spring Data artifacts.

Do not add platform or infrastructure libraries at compile time. In particular, leave out:
- `spring-boot-starter-*`, `spring-web` and `spring-context`;
- Kafka or other broker clients;
- JDBC drivers, Flyway and Hibernate.

The host brings the runtime and the adapters. For the tests described under [Testing](#testing), the server
artifact adds JUnit and, for the event schema contract, the `runtime-orchestrator` jar and its test jar:

```xml title="sample-orders-server: pom.xml (excerpt)"
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<!-- EventSchemaContract checks the published payloads against their schemas at build time (D24) -->
<dependency>
    <groupId>io.brix</groupId>
    <artifactId>runtime-orchestrator</artifactId>
    <version>${brix.version}</version>
    <type>test-jar</type>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>io.brix</groupId>
    <artifactId>runtime-orchestrator</artifactId>
    <version>${brix.version}</version>
    <scope>test</scope>
</dependency>
```

## The plugin class

The plugin class implements `io.runtime.sdk.plugin.BrixPlugin`. It is named in
`META-INF/services/io.runtime.sdk.plugin.BrixPlugin`, which holds one line:

```text title="sample-orders-server: META-INF/services/io.runtime.sdk.plugin.BrixPlugin"
io.brix.samples.orders.server.SampleOrdersPlugin
```

Each plugin has exactly one such class, in its server artifact:

```java title="sample-orders-server: SampleOrdersPlugin.java"
public final class SampleOrdersPlugin implements BrixPlugin {

    /** The handler of {@code POST /api/sample-orders/orders}, as the manifest names it. */
    public static final String PLACE_ORDER = "sample-orders.place.v1";

    private final AtomicReference<BrixHealth> health = new AtomicReference<>(BrixHealth.down("not started"));

    @Override
    public void configure(PluginBootstrapContext bootstrap) {
        bootstrap.bindEndpointHandlerFactory(PLACE_ORDER, context -> new PlaceOrder(new OrderPlacement(
            context.require(PersistenceCapability.class),
            context.require(EventBusCapability.class),
            context.require(IdGeneratorCapability.class),
            Clock.systemUTC())));
    }

    @Override
    public void onStart(PluginContext context) {
        health.set(BrixHealth.up());
    }

    @Override
    public void onStop() {
        health.set(BrixHealth.down("stopped"));
    }

    @Override
    public BrixHealth health() {
        return health.get();
    }
}
```

The factory builds the handler around the plugin's domain service, `OrderPlacement`, with the three capabilities the
manifest requires.

`configure` only binds factories. It cannot reach capabilities, and it must not create handlers, start threads or
call services. There is one bind method for each kind of manifest entry:

| Manifest entry | Bind method | Handler type |
|---|---|---|
| `endpoints.provides[].handlerId` | `bindEndpointHandlerFactory` | `EndpointHandler` |
| `events.subscribes[].handlerId` | `bindEventHandlerFactory` | `EventHandler` |
| `commands.provides[].handlerId` | `bindCommandHandlerFactory` | `CommandHandler` |
| `queries.provides[].handlerId` | `bindQueryHandlerFactory` | `QueryHandler` |
| `tasks.provides[].handlerId` | `bindTaskFactory` | `ManagedTask`; see [Tasks](./tasks.md) |

The older `bindEndpoint(...)`, `bindQueryHandler(...)`, `bindCommandHandler(...)` and `bindTask(...)` take a handler
that was built before the plugin had a context. They are deprecated for removal: use the factory methods.
[Queries](./queries.md) shows a query handler and the client another plugin calls it with.

The runtime calls each factory with the plugin's `PluginContext`. This is the only way to reach a capability:
- `context.require(X.class)` returns a capability the manifest lists under `capabilities.required`.
- `context.find(X.class)` returns an `Optional` for one listed under `capabilities.optional`. The `Optional` is empty
  when the host does not provide it, and the plugin must work without it.
- Asking for a capability the manifest does not list throws.
- A factory that throws or returns `null` keeps the plugin from starting.

`onStart` and `onStop` are for the plugin's own state, as in the sample's health flag. They must not register
handlers or start work. `health()` must never return `null`; `DOWN` and `UNKNOWN` keep the plugin from being ready.

## Domain services

The business rules live in a domain service in `plugin-core`, not in the handler. A domain service is a plain class:
- no `@Service`, `@Component` or `@Autowired`;
- no `@Transactional`;
- its constructor takes the capabilities it uses, and a `Clock` when it needs the time.

`OrderPlacement` is the domain service of `sample-orders`. It checks the order's rules, then stores the order and
publishes both events in one unit of work:

```java title="sample-orders-core: OrderPlacement.java (excerpt)"
public final class OrderPlacement {

    /** The most items one order may have. */
    public static final int MAX_QUANTITY = 1000;

    /** The longest sku. */
    public static final int MAX_SKU = 64;

    private final PersistenceCapability persistence;
    private final EventBusCapability events;
    private final IdGeneratorCapability ids;
    private final Clock clock;

    // ... a public constructor takes the four and checks that none is null

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
}
```

- A broken rule is an `InvalidOrderException`. The service throws it before it touches any data, so nothing is
  stored or published. The handler turns it into a 400.
- The service takes its request as `PlaceOrderRequest`, a record from `sample-orders-api`. It knows no HTTP.
- `required(...)` joins the unit of work the runtime opened for the handler (see [Units of work](#units-of-work)).
  The order and `SampleOrderPlaced` therefore commit together.
- The repository comes from `persistence.repository(...)`, inside that unit of work.

The handler factory creates the service, as shown in [The plugin class](#the-plugin-class). When several handlers
use one service, build it in one place. `SampleShippingPlugin` does this for `ShipmentDesk`, which both of its
handlers use:

```java title="sample-shipping-server: SampleShippingPlugin.java (excerpt)"
@Override
public void configure(PluginBootstrapContext bootstrap) {
    bootstrap.bindEventHandlerFactory(PREPARE_SHIPMENT, context -> new PrepareShipment(desk(context)));
    bootstrap.bindEndpointHandlerFactory(FIND_SHIPMENT, context -> new FindShipment(desk(context)));
}

private static ShipmentDesk desk(PluginContext context) {
    return new ShipmentDesk(context.require(PersistenceCapability.class),
        context.require(IdGeneratorCapability.class), Clock.systemUTC());
}
```

Each factory gets its own instance. A service holds only its capabilities, so that is cheap.

## Endpoint handlers

A plugin does not write `@RestController`, `@RequestMapping` or any other Spring MVC code. It declares each HTTP
endpoint in its manifest, and the runtime's dispatcher publishes the route, checks the caller and calls the handler.

### 1. Declare the endpoint

```yaml title="sample-orders-server: META-INF/brix/plugin-manifest.yaml"
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
        status: 201
      handlerId: sample-orders.place.v1
      # A tenant member signed in with an access token; the runtime binds the tenant from the token.
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
      # A retried request places another order; a client that needs exactly-once sends a business key of its own.
      idempotency:
        mode: not-applicable
```

| Field | Meaning |
|---|---|
| `endpointId` | Stable ID of the endpoint |
| `method`, `path` | HTTP method and path. A path segment such as `{orderId}` is a path variable. Two plugins cannot declare the same method and path. |
| `request.schemaRef`, `response.schemaRef` | The versioned JSON schemas of the request and the result. A GET names a schema too, for its query parameters and path variables. The runtime checks both for a typed handler. |
| `response.status` | What a success answers with: `200` (the default), `201`, `202` or `204` |
| `handlerId` | The ID your plugin class binds a factory to |
| `authentication` | `mode: required` with a `policyRef`: `tenant-actor` for tenant staff, `tenant-subject` for a tenant's end users, `platform-token` for platform administrators |
| `authorization` | The permission IDs the caller needs (declared under `permissions`) and how they combine (`all` or `any`) |
| `tenantContext` | `required`, `optional` or `forbidden`. With `required`, the runtime takes the tenant from the verified token. |
| `timeout`, `idempotency` | The deadline policy, and `idempotency.mode: not-applicable`: the runtime does not replay results yet |

The runtime refuses the request before your handler runs when:
- the caller is not signed in (401);
- the token is of the wrong kind (403);
- the tenant context does not match the declaration (403);
- the caller lacks a permission (403).

Your handler never parses tokens or tenant headers.

### 2. Write the handler

An endpoint handler implements `TypedEndpointHandler<B, R>`. It only adapts the protocol: the runtime has already
checked the request against its schema and read it as `B`, and the handler calls the domain service and answers
with `R`:

```java title="sample-orders-server: PlaceOrder.java"
final class PlaceOrder implements TypedEndpointHandler<PlaceOrderRequest, PlacedOrder> {

    private final OrderPlacement orders;

    PlaceOrder(OrderPlacement orders) {
        this.orders = Objects.requireNonNull(orders, "orders must not be null");
    }

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
}
```

- `PlaceOrderRequest` and `PlacedOrder` are records in `sample-orders-api`, as the request and response schemas
  describe them. A path variable is a property of the request: `ShowOrder` takes `FindOrderRequest(orderId)`.
- The tenant is the one the runtime verified, never one from the body.
- A failure is a `BrixException` whose code names its category, such as `sample-orders.order_invalid` (`INVALID`,
  400) or `sample-orders.order_not_found` (`NOT_FOUND`, 404). The handler never sets a status.
- The class and its constructor are package-private: only the plugin class creates the handler.

What the handler receives on `EndpointInvocation`:

| Accessor | Content |
|---|---|
| `body()` | The request as `B` |
| `tenantId()`, `actorId()` | The tenant and the caller the runtime verified |
| `access()` | The authorization result, such as the caller's tenant role and effective permissions |
| `traceId()`, `deadline()`, `cancellation()` | The request's trace ID, its absolute deadline, and the signal the runtime sets at it |

[Endpoints](./endpoints.md) covers the rest: query parameters, lists and paging, failures and the runtime's own
codes, and handlers written before 3.3.0.

### 3. Bind its factory

The handler is created by the factory that `configure` binds under the endpoint's `handlerId`, as shown in
[The plugin class](#the-plugin-class). Bind a factory for every handler ID the manifest declares, and for nothing
else:
- the runtime refuses a binding to an ID the manifest does not declare;
- it does not start a plugin that leaves an event subscription or a provided command without a factory;
- it never publishes an endpoint that has no factory.

## Persistence

### Entities

Entities live in the core artifact:

```java title="sample-orders-core: SampleOrder.java (excerpt)"
@Entity
@Table(name = "sample_orders_order")
public class SampleOrder implements Persistable<Long> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private Long tenantId;

    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    // ... quantity, version and placed_at are mapped the same way

    @Transient
    private boolean fresh = true;

    protected SampleOrder() {
    }

    public static SampleOrder place(long id, long tenantId, String sku, int quantity, OffsetDateTime placedAt) {
        SampleOrder order = new SampleOrder();
        order.id = id;
        order.tenantId = tenantId;
        // ...
        return order;
    }

    @PostPersist
    @PostLoad
    void stored() {
        fresh = false;
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return fresh;
    }

    // ... accessors: tenantId(), sku(), quantity(), version(), placedAt()
}
```

The persistence adapter checks every entity when the plugin starts, and refuses the plugin if a rule is broken:

| Rule | Why |
|---|---|
| No `@GeneratedValue`. Take ids from `IdGeneratorCapability.generate()`. | Ids do not depend on the database. The samples parse the generated string to a `long`. |
| Implement `Persistable` and report `isNew()` | The id is set before the first save, so Spring Data must be told to insert rather than merge |
| Every business table has a non-null `tenant_id`, set when the row is created | Tenant isolation. A table without `tenant_id` fails startup unless the platform registers it as global. |
| No schema or catalog in `@Table` | The adapter binds the plugin's schema |
| No Hibernate annotations except `@JdbcTypeCode(SqlTypes.JSON)`; no native queries or native mappings | The plugin stays portable across supported databases |
| Only your own entities | No association to, and no JPQL about, another plugin's entities |

Use `@Version` for optimistic locking.

### Repositories

A repository is a Spring Data interface, next to its entity in the core artifact:

```java title="sample-orders-core: SampleOrderRepository.java"
public interface SampleOrderRepository extends JpaRepository<SampleOrder, Long> {
}
```

`sample-shipping` adds a derived query:

```java title="sample-shipping-core: SampleShipmentRepository.java"
public interface SampleShipmentRepository extends JpaRepository<SampleShipment, Long> {

    /**
     * Finds the shipment of an order.
     *
     * @param tenantId the order's tenant
     * @param orderId the order
     * @return its shipment, if one was prepared
     */
    Optional<SampleShipment> findByTenantIdAndOrderId(Long tenantId, String orderId);
}
```

- Extend `JpaRepository` and, for dynamic filters, `JpaSpecificationExecutor`. Use derived query methods, or
  `@Query` with JPQL (never `nativeQuery`). A `@Modifying(clearAutomatically = true)` query must also set
  `flushAutomatically = true`.
- Get a repository only from `PersistenceCapability.repository(...)`, with the capability your handler factory
  received. Never `@Autowired`, `@EnableJpaRepositories`, `EntityManager`, `JdbcTemplate` or a `DataSource`. The
  adapter creates the implementation for your plugin, and refuses an interface that belongs to another plugin or
  uses native SQL.
- Do not add tenant conditions for isolation. The adapter keeps every read and write inside the unit of work's
  tenant. Never take a tenant ID from a request body: the tenant that reaches `findByTenantIdAndOrderId` is the one
  the runtime verified (`request.tenantId()` in `FindShipment`).

### Units of work

The runtime opens a unit of work for your plugin around every handler call. The changes commit when the handler
returns, and roll back when it throws.

| Entry | Unit of work | Tenant |
|---|---|---|
| `GET` or `HEAD` endpoint | Read-only | The caller's verified tenant |
| `POST`, `PUT`, `PATCH` or `DELETE` endpoint | Read-write | The caller's verified tenant |
| Event handler, command handler | Read-write, together with the inbox record | The message's tenant |

Within a handler, and in the domain services it calls, `persistence.unitOfWork()` gives you:
- `required(...)`, which joins the open unit of work, or opens one when there is none (`OrderPlacement.place` and
  `ShipmentDesk.prepare` use it this way);
- `readOnly(...)`, which narrows a block to reads (`ShipmentDesk.find`).

There is no nested independent transaction. A write from a `GET` handler fails with a read-only violation.

Persistence failures reach your code as `PersistenceCapabilityException`, never as ORM or JDBC exceptions. Its
`code()` is stable, for example:
- `persistence.optimistic_lock_conflict`;
- `persistence.unique_constraint_violation`;
- `persistence.read_only_violation`.

## Migrations

The manifest names the migration location:

```yaml title="sample-orders-server: META-INF/brix/plugin-manifest.yaml"
data:
  storageId: sample_orders
  schema: sample-orders
  migrationLocation: db/migration/sample-orders
  outbox: sample_orders_outbox
```

The scripts are Flyway versioned SQL files (`V1__description.sql`, `V2__...`) on the class path under that location;
the samples keep them in the server artifact. The platform runs them before the plugin starts, and keeps their
history apart from other plugins'.

```sql title="sample-orders-server: db/migration/sample-orders/V1__sample_orders.sql"
-- sample-orders' business table. The outbox (sample_orders_outbox) is not created here: the platform creates it
-- from its versioned messaging migrations because the manifest declares data.outbox.
CREATE TABLE sample_orders_order (
    id          BIGINT       NOT NULL,
    tenant_id   BIGINT       NOT NULL,
    sku         VARCHAR(64)  NOT NULL,
    quantity    INTEGER      NOT NULL,
    version     BIGINT       NOT NULL,
    placed_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_sample_orders_order PRIMARY KEY (id),
    CONSTRAINT chk_sample_orders_order_quantity CHECK (quantity BETWEEN 1 AND 1000)
);

CREATE INDEX idx_sample_orders_order_tenant ON sample_orders_order (tenant_id, placed_at);
```

- Create only your business tables. The platform creates `<storageId>_outbox`, `<storageId>_inbox` and
  `<storageId>_command_idempotency` itself; see
  [what the platform creates](./events-and-commands.md#tables-what-the-platform-creates-for-you).
- Never change a migration that has run in a shared environment. Add a new version. Make breaking changes in steps
  (expand, migrate, contract).
- No foreign keys to another plugin's tables or to platform tables. Store their IDs as plain values.
- PostgreSQL is the reference database.

## Events and commands

Events and commands are declared in the manifest and travel through the platform's outbox, broker and inbox.
Your code never sees Kafka, a topic name or a consumer group.

- **Publish** a fact by declaring it under `events.publishes` with a schema and a reliability. Then call
  `EventBusCapability.publishIntegration(...)` with an `IntegrationEvent` subclass, inside the unit of work that
  changes your data. `OrderPlacement` does this right after it saves the order:

  ```java title="sample-orders-core: OrderPlacement.java (excerpt)"
  events.publishIntegration(new SampleOrderPlaced(placed));
  events.publishIntegration(new SampleOrderActivity(placed, "placed"));
  ```

  `SampleOrderPlaced` is STANDARD, so it goes into the outbox in the same transaction. `SampleOrderActivity` is
  BEST_EFFORT, so it is sent after the commit.

- **Subscribe** by declaring the event under `events.subscribes`, and binding an `EventHandler` class with
  `bindEventHandlerFactory`:

  ```java title="sample-shipping-server: SampleShippingPlugin.java (excerpt)"
  bootstrap.bindEventHandlerFactory(PREPARE_SHIPMENT, context -> new PrepareShipment(desk(context)));
  ```

  Write the handler as a class that names its payload type, such as `EventHandler<OrderPlaced>`: the runtime decodes
  each event into that type, and refuses a handler whose type it cannot tell. `OrderPlaced` is `sample-shipping`'s
  own record in its core artifact, not a class of the publisher.

- **Ask another plugin to change its data** with a command: the owner publishes its contract in its api artifact
  and binds a `CommandHandler` with `bindCommandHandlerFactory`; the sender submits the generated type through
  `CommandCapability.client(...)` inside its unit of work.

Do not use the legacy `io.runtime.sdk.annotation.EventHandler` annotation: nothing reads it, and it will be removed.
[Events and Commands](./events-and-commands.md) covers declarations, schemas, guarantees, failures and operations in
full.

## The manifest

`META-INF/brix/plugin-manifest.yaml`, in the plugin-server artifact, is the one place where the plugin is declared.
There is no `module-manifest.yaml`, no `plugin-manifest.json`, and no Java configuration
(`@Configuration`, `@ComponentScan`, `@EntityScan` or auto-configuration).

Here is the rest of the `sample-orders` manifest: everything but the `endpoints`, `events` and `data` sections shown
above.

```yaml title="sample-orders-server: META-INF/brix/plugin-manifest.yaml (excerpt)"
apiVersion: brix.io/v1
kind: Plugin
metadata:
  pluginId: sample-orders
  name: sample-orders
  version: 3.2.0
  displayName: Sample Orders
  vendor: Brix Platform
  license: Apache-2.0
runtime:
  compiledAgainst: 3.2.0
  supportedRange: ">=3.2.0 <4.0.0"
# Three artifacts (root BRX-RS-MANIFEST-005): the contract, the logic and data, the entry point and handlers.
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
capabilities:
  required:
    - id: io.runtime.sdk.capability.EventBusCapability
      version: ">=3.0.10 <4.0.0"
    - id: io.runtime.sdk.capability.PersistenceCapability
      version: ">=3.2.0 <4.0.0"
    - id: io.runtime.sdk.capability.IdGeneratorCapability
      version: ">=3.2.0 <4.0.0"
  optional: []
queries:
  provides: []
  consumes: []
commands:
  provides: []
  consumes: []
tasks:
  provides: []
internalContracts:
  provides: []
permissions:
  - id: sample-orders:order:place
    name: Place sample orders
    description: Allows placing an order in the current tenant.
    grants:
      - {role: OWNER, scope: TENANT}
      - {role: ADMIN, scope: TENANT}
      - {role: MEMBER, scope: TENANT}
config: []
ui:
  web:
    enabled: false
```

| Section | Content |
|---|---|
| `metadata` | `pluginId` (lower-case letters, digits and dashes), version, vendor, license |
| `runtime` | The runtime version the plugin was built against, and the versions it runs on |
| `modules` | Each artifact of the plugin with its `moduleKind` |
| `endpoints.provides` | HTTP endpoints; see [Endpoint handlers](#endpoint-handlers) |
| `capabilities.required`, `capabilities.optional` | Capability interfaces by full name, with version ranges |
| `queries`, `commands`, `events`, `tasks` | What the plugin provides, consumes, publishes and subscribes to |
| `data` | `storageId`, migration location, and `outbox` / `inbox` when the plugin uses them |
| `permissions` | The plugin's permissions, and which tenant roles hold each one |
| `config`, `ui` | Configuration keys, and whether the plugin has a web or mobile UI |

The samples list every section, with empty lists where they have nothing to declare. A few rules:
- Version ranges use comparators separated by a space, such as `">=3.2.0 <4.0.0"`. Do not use `^`, `~` or
  wildcards.
- `storageId` is lower case with underscores, unique across plugins, and never changes. It names the messaging
  tables, so `outbox` and `inbox` must be exactly `<storageId>_outbox` and `<storageId>_inbox`.
- The loader is strict: an unknown field, for example in `metadata`, `data` or `events`, fails at startup instead
  of being ignored.

## Testing

### Unit tests

Domain services and handlers are classes with constructor arguments, so plain JUnit tests can create them. The
samples do not include such tests; for `sample-orders` they could look like the two below. They need JUnit and
Mockito at test scope (`org.junit.jupiter:junit-jupiter`, `org.mockito:mockito-core`); the Spring Boot BOM manages
both versions.

Test the rules, the unit of work and the events in the core, next to `OrderPlacement`:

```java title="sample-orders-core: src/test/java/io/brix/samples/orders/core/OrderPlacementTest.java"
package io.brix.samples.orders.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import io.brix.samples.orders.api.PlaceOrderRequest;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.IdGeneratorCapability;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.capability.UnitOfWork;

class OrderPlacementTest {

    private final PersistenceCapability persistence = mock(PersistenceCapability.class);
    private final EventBusCapability events = mock(EventBusCapability.class);
    private final IdGeneratorCapability ids = mock(IdGeneratorCapability.class);
    private final SampleOrderRepository orders = mock(SampleOrderRepository.class);
    private final OrderPlacement placement = new OrderPlacement(persistence, events, ids, Clock.systemUTC());

    @Test
    void refusesAnOrderWithoutAQuantity() {
        assertThrows(InvalidOrderException.class, () -> placement.place(42L, new PlaceOrderRequest("sku-1", 0)));

        verifyNoInteractions(persistence, events);
    }

    @Test
    void storesTheOrderAndPublishesBothEvents() {
        when(persistence.unitOfWork()).thenReturn(inline());
        when(persistence.repository(SampleOrderRepository.class)).thenReturn(orders);
        when(ids.generate()).thenReturn("1001");

        SampleOrder order = placement.place(42L, new PlaceOrderRequest("sku-1", 2));

        assertEquals(1001L, order.getId());
        assertEquals(42L, order.tenantId());
        verify(orders).save(order);
        verify(events).publishIntegration(any(SampleOrderPlaced.class));
        verify(events).publishIntegration(any(SampleOrderActivity.class));
    }

    /** Runs the work directly, as the runtime's unit of work would inside a transaction. */
    private static UnitOfWork inline() {
        return new UnitOfWork() {
            @Override
            public <T> T required(Supplier<T> work) {
                return work.get();
            }

            @Override
            public <T> T readOnly(Supplier<T> work) {
                return work.get();
            }
        };
    }
}
```

Test the protocol mapping in the server. `PlaceOrder` and its constructor are package-private, so the test sits in
the same package. It builds the handler around a real `OrderPlacement`: only the core can create an
`InvalidOrderException`, whose constructor is package-private.

```java title="sample-orders-server: src/test/java/io/brix/samples/orders/server/PlaceOrderTest.java"
package io.brix.samples.orders.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.brix.samples.orders.api.PlaceOrderRequest;
import io.brix.samples.orders.core.OrderPlacement;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.IdGeneratorCapability;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.error.ErrorCategory;
import io.runtime.sdk.plugin.EndpointInvocation;

class PlaceOrderTest {

    private final PersistenceCapability persistence = mock(PersistenceCapability.class);
    private final EventBusCapability events = mock(EventBusCapability.class);
    private final PlaceOrder handler = new PlaceOrder(new OrderPlacement(persistence, events,
        mock(IdGeneratorCapability.class), Clock.systemUTC()));

    @Test
    void refusesAnOrderThatBreaksARule() {
        SampleOrdersException refused = assertThrows(SampleOrdersException.class,
            () -> handler.handle(invocation(new PlaceOrderRequest("sku-1", 0))));

        assertEquals(ErrorCategory.INVALID, refused.errorCode().category());
        verifyNoInteractions(persistence, events);
    }

    private static EndpointInvocation<PlaceOrderRequest> invocation(PlaceOrderRequest body) {
        return new EndpointInvocation<>(body, Map.of(), Map.of(), Map.of(), Optional.of("42"), Optional.of("2001"),
            Optional.empty(), Instant.now().plusSeconds(5));
    }
}
```

Tests like these prove the service's rules and the handler's mapping. They do not prove the tenant binding, the
transaction or the outbox: the end-to-end test below does.

### Event schema contract

`EventSchemaContract` checks, in your build, that each published event matches its schema. Every `schemaRef` under
`events.publishes` must resolve to a file whose `$id` is that reference, and the sample payload must validate
against it. The test sits in the server artifact, next to the manifest; the payload records and the schemas come
from the api artifact:

```java title="sample-orders-server: SampleOrdersEventSchemasTest.java"
@Test
void everyPublishedEventMatchesItsSchema() {
    EventSchemaContract.verify(SampleOrdersPlugin.class, Map.of(
        SampleOrderEvents.PLACED, new SampleOrderPlacedPayload(42L, "1001", "sku-1", 2, 1L),
        SampleOrderEvents.ACTIVITY, new SampleOrderActivityPayload(42L, "1001", "placed")));
}
```

A second test in the same class checks the other way round: a payload whose fields the schema does not describe
fails the build.

A subscriber can check that its handler names the payload type the runtime will decode into:

```java title="sample-shipping-server: PrepareShipmentTest.java"
@Test
void theHandlerNamesThePayloadItReads() {
    assertSame(OrderPlaced.class, ResolvableType.forClass(PrepareShipment.class).as(EventHandler.class)
        .getGeneric(0).resolve());
}
```

### End to end

`sample-e2e` starts the plugins through the real runtime. Its host is made only of auto-configurations and
properties, and it depends on the two server artifacts, which bring their core and api artifacts. The test:
- uses a throwaway PostgreSQL schema and an embedded Kafka broker;
- places an order over HTTP;
- waits for the outbox row, the subscriber's change and the inbox receipt;
- reads the shipment back over HTTP, and checks that an unknown order answers 404.

The properties show what the host needs:

```java title="sample-e2e: SamplePluginsEndToEndTest.java"
@SpringBootTest(classes = SampleHost.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.jpa.open-in-view=false",
    "brix.capability.required[0]=io.runtime.sdk.capability.EventBusCapability",
    "brix.capability.required[1]=io.runtime.sdk.capability.IdGeneratorCapability",
    "brix.runtime-shell.required-plugins[0]=sample-orders",
    "brix.runtime-shell.required-plugins[1]=sample-shipping",
    "brix.infra.database.migration.fail-on-error=true",
    "brix.outbox.relay.enabled=true",
    "brix.outbox.relay.persistence.enabled=true",
    "brix.outbox.relay.owner=sample-e2e",
    "brix.outbox.relay.transport=kafka",
    // ... poll, retry and publish timings
})
@EnabledIfEnvironmentVariable(named = "BRIX_P0_POSTGRES_URL", matches = ".+",
    disabledReason = "needs a throwaway PostgreSQL in BRIX_P0_POSTGRES_URL, _USER and _PASSWORD")
class SamplePluginsEndToEndTest {
    // ...
}
```

`SampleHost` replaces the identity provider with one bean: every request comes from member 2001 of tenant 42. To
run the test, see [Running it end to end](../examples/event-driven-plugin.md#running-it-end-to-end). Use the same
approach for your own plugins.

## Best practices

1. **Declare, then bind.** Everything the plugin offers or uses is in the manifest, and `configure` binds exactly
   what the manifest declares.
2. **Capabilities come from the factory's context.** List them in the manifest. Never inject them, look them up in
   Spring, or keep them in static fields.
3. **Keep handlers thin.** A handler reads the input, calls a domain service and maps the result. The rules live in
   plain classes in `plugin-core`, as `PlaceOrder` and `OrderPlacement` show.
4. **Let the runtime own transactions.** No `@Transactional`. Narrow a block with `unitOfWork()` only when you need
   to.
5. **Ids from `IdGeneratorCapability`, a `tenant_id` on every row.** Never trust a tenant ID from a request body.
6. **Publish facts in the unit of work that changes your data.** Never use Kafka clients, `KafkaTemplate`,
   `@KafkaListener` or topic names.
7. **Migrations hold business tables only,** and an applied migration never changes.
8. **Let the schema refuse bad input, and fail with a `BrixException`** whose code names its category. A handler
   never sets an HTTP status.
9. **No threads, executors, timers or `@Scheduled`,** and no static mutable state. The runtime owns the resources
   a plugin runs on.

## Next Steps

- [Endpoints](./endpoints.md) - Typed handlers, schemas, failures and lists
- [Events and Commands](./events-and-commands.md) - Publish, subscribe and send commands
- [Event-Driven Plugin](../examples/event-driven-plugin.md) - The two samples, step by step
- [Frontend Development](./frontend-development.md) - Build the frontend
- [Testing Guide](./testing.md) - Testing strategies
- [Architecture Guard](./architecture-guard.md) - Enforce rules
