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

Publish and subscribe to domain and integration events.

```java
public interface EventBusCapability {
    
    /**
     * Publishes a domain event within the current bounded context.
     *
     * @param eventType the event type identifier
     * @param payload the event payload
     */
    void publish(String eventType, Object payload);
    
    /**
     * Publishes an integration event for cross-plugin communication.
     *
     * @param eventType the event type identifier  
     * @param payload the event payload
     */
    void publishIntegration(String eventType, Object payload);
    
    /**
     * Subscribes to events of the specified type.
     *
     * @param eventType the event type to subscribe to
     * @param handler the event handler
     */
    void subscribe(String eventType, EventHandler handler);
}
```

**Usage:**

```java
// Publishing
eventBus.publish("order.created", new OrderCreatedEvent(orderId));

// Subscribing
eventBus.subscribe("user.registered", event -> {
    sendWelcomeEmail(event.getUserId());
});
```

**Implementations:**
- `KafkaEventBusAdapter` - Production (Kafka)
- `SimpleEventBusAdapter` - Development (In-memory)

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

### AuthContextCapability

Current authentication and authorization context.

```java
public interface AuthContextCapability {
    
    /**
     * Gets the current authenticated user.
     */
    Optional<AuthenticatedUser> getCurrentUser();
    
    /**
     * Gets the current tenant ID (multi-tenant mode).
     */
    Optional<String> getCurrentTenantId();
    
    /**
     * Checks if current user has the specified permission.
     */
    boolean hasPermission(String permission);
    
    /**
     * Checks if current user has the specified role.
     */
    boolean hasRole(String role);
}
```

**Usage:**

```java
AuthContextCapability auth = context.getCapability(AuthContextCapability.class);

if (auth.hasPermission("order.approve")) {
    approveOrder(orderId);
}

String tenantId = auth.getCurrentTenantId()
    .orElseThrow(() -> new UnauthorizedException("No tenant context"));
```

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
- [Event Model](./event-model) - Domain vs Integration events
- [Architecture Guard](../guides/architecture-guard) - Enforcing capability-only dependencies
