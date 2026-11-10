---
id: capability-contract
title: Capability Contract
sidebar_label: Capability Contract
sidebar_position: 2
---

# Capability Contract

**Capability Contracts** are the interfaces that plugins depend on. They define WHAT your plugin can do, while adapters define HOW it's done.

## Core Principle

:::warning Design Constraint #2
> Plugins depend ONLY on Runtime Capability Contracts.
> 
> No Kafka, Redis, HTTP client, or database driver imports in plugin code.
:::

## Available Capabilities

### EventBusCapability

Publishes the plugin's integration events. Subscriptions are not made through this interface: a subscriber declares
them in its manifest and binds an `EventHandler`. See [Events and Commands](../guides/events-and-commands.md).

```java
public interface EventBusCapability {

    /** Publishes an event inside the publisher (not carried between plugins by the owner outbox bus). */
    void publish(DomainEvent event);

    /**
     * Publishes an integration event the plugin declares under events.publishes. CRITICAL and STANDARD events go
     * to the plugin's outbox in its current unit of work; BEST_EFFORT events are sent after it commits.
     */
    void publishIntegration(IntegrationEvent event);
}
```

**Usage** (from the `sample-orders` plugin):

```java
// In configure(...): the runtime creates the handler once the plugin has started.
bootstrap.bindEndpointHandlerFactory(PLACE_ORDER, context -> new PlaceOrder(new OrderPlacement(
    context.require(PersistenceCapability.class),
    context.require(EventBusCapability.class),
    context.require(IdGeneratorCapability.class),
    Clock.systemUTC())));

// In OrderPlacement, the plugin's domain service: the order and its events commit together.
return persistence.unitOfWork().required(() -> {
    SampleOrder placed = SampleOrder.place(Long.parseLong(ids.generate()), tenantId, request.sku(),
        request.quantity(), OffsetDateTime.now(clock));
    persistence.repository(SampleOrderRepository.class).save(placed);
    events.publishIntegration(new SampleOrderPlaced(placed));
    events.publishIntegration(new SampleOrderActivity(placed, "placed"));
    return placed;
});
```

**Implementations:** each plugin gets an owner outbox bus from the persistence adapter. The host's relay delivers
outbox messages over the transport it selects: Kafka in production, or in-process.

---

### CommandCapability

Asks the one plugin that provides a command to change its data. The command is written to the sender's outbox in its
unit of work, and the provider applies it once per business key of the sender and tenant.

```java
public interface CommandCapability {

    /** The client of a command the plugin declares under commands.consumes, from the owner's generated type. */
    <C> CommandClient<C> client(CommandType<C> type);

    /** Deprecated, for removal in 4.0: a string command type and a tenant the caller names. */
    @Deprecated(since = "3.3.0", forRemoval = true)
    <C> CommandSubmission submit(CommandRequest<C> request);
}
```

**Usage:**

```java
CommandClient<CancelOrder> cancellations = context.require(CommandCapability.class)
    .client(SampleOrdersCommands.CANCEL_ORDER);
// inside a unit of work of the sending plugin; the tenant is the current call's
CommandSubmission submission = cancellations.submit(
    CancelOrder.builder().orderId(orderId).reason(CancelOrder.Reason.SHIPPING_REFUSED).build(), orderId);
// submission.durableReceipt() completes once the command is durably queued (QUEUED), not when it is handled.

// Later, by its id: whether the provider ran it, took it as a duplicate, or parked it. Only this plugin, in this
// tenant, reads it; anything else is UNKNOWN.
CommandStatus status = cancellations.status(submission.commandId());
```

See [Commands](../guides/events-and-commands.md#commands).

---

### StateStoreCapability

Key-value state storage for caching and session data.

```java
public interface StateStoreCapability {
    
    /**
     * Stores a value with the given key.
     */
    <T> void put(String key, T value);
    
    /**
     * Stores a value with TTL.
     */
    <T> void put(String key, T value, Duration ttl);
    
    /**
     * Retrieves a value by key.
     */
    <T> Optional<T> get(String key, Class<T> type);
    
    /**
     * Deletes a value by key.
     */
    void delete(String key);
    
    /**
     * Checks if a key exists.
     */
    boolean exists(String key);
}
```

**Usage:**

```java
// Store user session
stateStore.put("session:" + sessionId, userSession, Duration.ofHours(24));

// Retrieve
Optional<UserSession> session = stateStore.get("session:" + sessionId, UserSession.class);
```

**Implementations:**
- `RedisStateStoreAdapter` - Production (Redis)
- `SimpleStateStoreAdapter` - Development (In-memory ConcurrentHashMap)

---

### HttpCapability

HTTP client for external API calls.

```typescript
interface HttpCapability {
  get<T>(url: string, config?: RequestConfig): Promise<T>;
  post<T>(url: string, body?: unknown, config?: RequestConfig): Promise<T>;
  put<T>(url: string, body?: unknown, config?: RequestConfig): Promise<T>;
  patch<T>(url: string, body?: unknown, config?: RequestConfig): Promise<T>;
  delete<T>(url: string, config?: RequestConfig): Promise<T>;
}
```

**Usage:**

```typescript
// Frontend - uses HttpCapability, not fetch/axios
const http = useCapability(HttpCapability);
const users = await http.get<User[]>('/api/users');
```

**Implementations:**
- `FetchHttpAdapter` - Browser fetch API
- `AxiosHttpAdapter` - Axios client
- `MockHttpAdapter` - Testing

---

### AuthCapability

The caller of the current request, and the permissions it holds. Authorization checks a permission code, never a
role name (super-admin BRX-SA-AUTH-006, multi-tenant T-29). The runtime already checks the permissions an endpoint
names under `authorization.permissions` before your handler runs (see [Endpoints](../guides/endpoints.md)); use the
capability for a decision inside the handler.

```java
public interface AuthCapability {

    /** The caller, or null when the request is not authenticated. */
    Principal getCurrentPrincipal();

    boolean hasPermission(String permission);

    default boolean hasAnyPermission(String... permissions) { ... }

    default boolean hasAllPermissions(String... permissions) { ... }

    /** The data scopes granted to the caller, never null. */
    Set<DataScope> getAuthorizedScopes();

    default boolean isAuthenticated() { ... }

    /** The caller's tenant, or null outside a tenant. */
    default String getTenantId() { ... }

    /** Deprecated since 3.3.0, removed in 4.0.0: check a permission code instead. */
    @Deprecated(since = "3.3.0", forRemoval = true)
    boolean hasRole(String role);
}
```

**Usage:**

```java
AuthCapability auth = context.require(AuthCapability.class);

// Shows the order's history only to a caller who may read orders, a permission the plugin declares.
boolean withHistory = auth.hasPermission("sample-orders:order:read");
```

`hasRole` stays until 4.0.0 for code compiled against it, but a plugin cannot use it: the plugin profile of the
Architecture Guard refuses a call to `hasRole` or `hasAnyRole`, and a method reference to them.
`AuthContextCapability` is the deprecated old name of the same interface.

---

### DataAccessCapability

Database operations with automatic tenant isolation.

```java
public interface DataAccessCapability {
    
    /**
     * Finds an entity by ID.
     */
    <T> Optional<T> findById(Class<T> entityClass, Object id);
    
    /**
     * Saves an entity.
     */
    <T> T save(T entity);
    
    /**
     * Executes a query with parameters.
     */
    <T> List<T> query(String jpql, Map<String, Object> params, Class<T> type);
    
    /**
     * Begins a transaction context.
     */
    TransactionContext beginTransaction();
}
```

**Usage:**

```java
DataAccessCapability dataAccess = context.getCapability(DataAccessCapability.class);

// Automatic tenant isolation applied
List<Order> orders = dataAccess.query(
    "SELECT o FROM Order o WHERE o.status = :status",
    Map.of("status", "PENDING"),
    Order.class
);
```

---

### ObservabilityCapability

Metrics, tracing, and logging integration.

```java
public interface ObservabilityCapability {
    
    /**
     * Records a counter metric.
     */
    void incrementCounter(String name, Map<String, String> tags);
    
    /**
     * Records a gauge value.
     */
    void recordGauge(String name, double value, Map<String, String> tags);
    
    /**
     * Records operation duration.
     */
    void recordTimer(String name, Duration duration, Map<String, String> tags);
    
    /**
     * Creates a trace span.
     */
    Span startSpan(String operationName);
}
```

**Usage:**

```java
ObservabilityCapability obs = context.getCapability(ObservabilityCapability.class);

obs.incrementCounter("orders.created", Map.of("region", "asia"));

Span span = obs.startSpan("processPayment");
try {
    processPayment();
} finally {
    span.end();
}
```

---

## Capability Registry Pattern

Capabilities are registered using the **Registry Pattern**:

```java
public interface RuntimeContext {
    
    /**
     * Retrieves a registered capability.
     * 
     * @param capabilityType the capability interface class
     * @return the capability implementation
     * @throws CapabilityNotAvailableException if not registered
     */
    <T> T getCapability(Class<T> capabilityType);
    
    /**
     * Checks if a capability is available.
     */
    boolean hasCapability(Class<?> capabilityType);
}
```

Hosts register implementations at startup:

```java
// Inside Host assembly
CapabilityRegistry registry = new DefaultCapabilityRegistry();
registry.register(EventBusCapability.class, new KafkaEventBusAdapter());
registry.register(StateStoreCapability.class, new RedisStateStoreAdapter());
```

## Creating Custom Capabilities

You can define custom capabilities for your domain:

```java
// 1. Define the contract
public interface PaymentGatewayCapability {
    PaymentResult processPayment(PaymentRequest request);
    PaymentStatus getStatus(String transactionId);
}

// 2. Create adapter implementation
public class StripePaymentAdapter implements PaymentGatewayCapability {
    // Implementation using Stripe SDK
}

// 3. Register in Host
registry.register(PaymentGatewayCapability.class, new StripePaymentAdapter());

// 4. Use in plugin
PaymentGatewayCapability payments = context.getCapability(PaymentGatewayCapability.class);
```

## Next Steps

- [Plugin Model](./plugin-model) - How plugins are structured
- [Event Model](./event-model) - Integration events and commands between plugins
- [Architecture Guard](../guides/architecture-guard) - Enforcing capability-only dependencies
