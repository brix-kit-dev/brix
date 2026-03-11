---
id: faq
title: Frequently Asked Questions
sidebar_label: FAQ
---

# Frequently Asked Questions

## General

### What is Brix Framework?

Brix is a **Runtime Shell Architecture** framework that provides infrastructure capabilities to business plugins through well-defined contracts. It enables building maintainable enterprise applications where plugins remain isolated from infrastructure details.

### Why is it called "Runtime Shell"?

The "shell" metaphor describes how the runtime wraps plugins and provides all infrastructure capabilities (database, cache, messaging, etc.) through standardized interfaces. Plugins live inside this protective shell and never directly interact with infrastructure.

### How does Brix compare to traditional architectures?

| Aspect | Traditional | Brix Runtime Shell |
|--------|-------------|-------------------|
| Infrastructure | Direct dependency | Capability contracts |
| Plugin coupling | Tightly coupled | Isolated |
| Testing | Integration tests | Unit tests |
| Migration | Full replacement | Adapter swap |
| Learning curve | Per-library | Contract-based |

### What programming languages does Brix support?

- **Backend**: Java 17+ (Spring Boot 3.x)
- **Frontend**: TypeScript (React 18+)

---

## Architecture

### What are the 5 layers in Brix?

1. **Layer 0 (Host)**: Ultra-thin application entry point
2. **Layer 1 (Runtime)**: Capability loading and binding
3. **Layer 2 (Adapters)**: Infrastructure implementations (2A/2B/2C)
4. **Layer 3 (Contracts)**: Capability interfaces (SDK)
5. **Layer 4 (Plugins)**: Business logic

### Why can't plugins depend on each other?

Cross-plugin dependencies create tight coupling that makes independent deployment impossible. Plugins communicate via **Integration Events** through the EventBusCapability, maintaining loose coupling.

### What is the "ultra-thin host" principle?

The host module should contain **zero business logic**. It only:
- Configures which adapters to use
- Starts the application
- Provides build metadata

If you're adding code to the host, you're likely violating architecture.

### How do adapters work?

Adapters implement capability contracts. The runtime binds adapters based on configuration:

```java
// Capability contract (Layer 3)
public interface CacheCapability {
    <T> Optional<T> get(String key, Class<T> type);
    void set(String key, Object value, Duration ttl);
}

// Redis adapter (Layer 2A)
@Adapter
public class RedisCacheAdapter implements CacheCapability {
    private final RedisTemplate<String, Object> redis;
    
    @Override
    public <T> Optional<T> get(String key, Class<T> type) {
        return Optional.ofNullable(type.cast(redis.opsForValue().get(key)));
    }
}
```

---

## Development

### How do I create a new plugin?

```bash
# Using Maven archetype
mvn archetype:generate \
  -DarchetypeGroupId=io.brix \
  -DarchetypeArtifactId=plugin-archetype \
  -DgroupId=com.example \
  -DartifactId=my-plugin
```

Or manually create the package structure:
```
my-plugin/
├── core/          # Business logic
├── shared/        # Types and DTOs
├── web/           # Frontend (optional)
└── pom.xml
```

### How do I add a new capability?

1. Define the interface in `runtime-sdk-api`:
   ```java
   public interface MyCapability extends Capability {
       void doSomething();
   }
   ```

2. Create adapter in `infra-adapters`:
   ```java
   @Adapter
   public class MyCapabilityAdapter implements MyCapability {
       @Override
       public void doSomething() { /* implementation */ }
   }
   ```

3. Register in configuration:
   ```java
   @Configuration
   public class MyCapabilityConfig {
       @Bean
       public MyCapability myCapability() {
           return new MyCapabilityAdapter();
       }
   }
   ```

### How do I test my plugin?

Use **CapabilityTestKit** for unit testing:

```java
@BrixTest
class OrderServiceTest {
    @MockCapability
    private DataAccessCapability dataAccess;
    
    @MockCapability
    private EventBusCapability eventBus;
    
    @InjectMocks
    private OrderService orderService;
    
    @Test
    void shouldCreateOrder() {
        // Arrange
        when(dataAccess.save(any(Order.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        
        // Act
        Order order = orderService.createOrder(new CreateOrderCommand("item1"));
        
        // Assert
        assertThat(order.getItems()).hasSize(1);
        verify(eventBus).publish(any(OrderCreatedEvent.class));
    }
}
```

### How do I handle database migrations?

Use `FlywayCapability` or `LiquibaseCapability`:

```java
@Autowired
private FlywayCapability flyway;

// Migrations are auto-discovered in:
// src/main/resources/db/migration/V1__Initial.sql
```

---

## Frontend

### Why do I import React from shared-runtime?

Shared-runtime ensures all plugins use the same React instance, preventing multiple-React errors:

```typescript
// ✅ Correct
import { React, useState } from '@brix/shared-runtime-web';

// ❌ Wrong - causes multiple React instances
import React from 'react';
```

### How does HttpCapability work in the browser?

HttpCapability provides type-safe API calls:

```typescript
const http = useCapability(HttpCapability);

// GET request
const orders = await http.get<Order[]>('/api/orders');

// POST request
const newOrder = await http.post<Order>('/api/orders', {
    customerId: '123',
    items: [{ productId: 'abc', quantity: 2 }]
});
```

### What is the View → ViewModel → Repository pattern?

A unidirectional data flow pattern:

1. **View**: Pure React components (rendering only)
2. **ViewModel**: Custom hooks for state and logic
3. **Repository**: Data access through capabilities

```typescript
// Repository
class OrderRepository {
    constructor(private http: HttpCapability) {}
    async getOrders() { return this.http.get<Order[]>('/api/orders'); }
}

// ViewModel
function useOrders() {
    const [orders, setOrders] = useState<Order[]>([]);
    const repo = useOrderRepository();
    
    useEffect(() => { repo.getOrders().then(setOrders); }, []);
    return { orders };
}

// View
function OrderList() {
    const { orders } = useOrders();
    return <ul>{orders.map(o => <li key={o.id}>{o.name}</li>)}</ul>;
}
```

---

## Deployment

### How do I deploy to production?

Build the host with all plugins:

```bash
cd enterprise-host
mvn clean package -Pprod

# Docker
docker build -t brix-app .
docker run -p 8080:8080 brix-app
```

### How do I switch infrastructure?

Change adapter dependencies in the host's pom.xml:

```xml
<!-- From Redis to Memcached -->
<dependency>
    <groupId>io.brix</groupId>
    <artifactId>infra-adapter-memcached</artifactId>
</dependency>

<!-- From PostgreSQL to MySQL -->
<dependency>
    <groupId>io.brix</groupId>
    <artifactId>infra-adapter-mysql</artifactId>
</dependency>
```

No plugin code changes required!

### How do I configure adapters?

Use `application.yml` or environment variables:

```yaml
brix:
  adapters:
    cache:
      type: redis
      redis:
        host: ${REDIS_HOST:localhost}
        port: ${REDIS_PORT:6379}
    database:
      type: postgresql
      postgresql:
        url: ${DATABASE_URL}
```

---

## Troubleshooting

### Architecture Guard test failed

Check which rule failed and fix the violation:

```
Architecture Violation: Rule 'no classes in plugin should depend on Kafka' was violated:
    Class com.example.OrderService imports org.apache.kafka.clients.producer.KafkaProducer
```

**Fix**: Use EventBusCapability instead of direct Kafka imports.

### "Multiple React instances" error

Ensure you import from shared-runtime:

```typescript
// Change this:
import { useState } from 'react';

// To this:
import { useState } from '@brix/shared-runtime-web';
```

### "Capability not found" error

1. Check if the adapter is registered
2. Verify the capability interface is correct
3. Ensure autowiring is enabled

```java
// Check registration
@Autowired(required = false)
private MyCapability myCapability;

if (myCapability == null) {
    throw new IllegalStateException("MyCapability adapter not registered");
}
```

### Plugin events not received

1. Verify event topic name matches
2. Check event schema compatibility
3. Ensure handler is annotated correctly

```java
@EventHandler("order.created")  // Topic must match publisher
public void onOrderCreated(OrderCreatedEvent event) {
    // Handler code
}
```

---

## Contributing

### How do I contribute to Brix?

See [CONTRIBUTING.md](https://github.com/nickmao/brix/blob/main/CONTRIBUTING.md) for:
- Code style guidelines
- PR requirements
- Architecture decisions process

### Where do I report bugs?

Create an issue on GitHub: https://github.com/nickmao/brix/issues

### How do I propose a new feature?

1. Open a Discussion on GitHub
2. Get feedback from maintainers
3. Submit an RFC if approved
4. Implement and submit PR
