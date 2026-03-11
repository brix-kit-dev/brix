---
id: from-v2
title: Migrating from v2.x
sidebar_label: From v2.x
sidebar_position: 1
---

# Migrating from Brix v2.x to v3.x

This guide helps you migrate from Brix 2.x to the new Runtime Shell Architecture in 3.x.

## Overview of Changes

### Architecture Changes

| Aspect | v2.x | v3.x |
|--------|------|------|
| Architecture | Layered (traditional) | Runtime Shell |
| Infrastructure | Direct dependencies | Capability contracts |
| Plugin coupling | Shared services | Event-driven |
| React | Direct imports | shared-runtime |
| Java | 11+ | 17+ |
| Spring Boot | 2.7 | 3.2+ |

### Breaking Changes

1. **Capability contracts** - All infrastructure accessed through interfaces
2. **No cross-plugin imports** - Use events instead
3. **React from shared-runtime** - Single React instance
4. **Java 17 required** - Records, sealed classes, etc.
5. **New package structure** - `io.brix.runtime.sdk.api`

## Step-by-Step Migration

### Step 1: Update Dependencies

**Before (v2.x):**
```xml
<parent>
    <groupId>io.brix</groupId>
    <artifactId>brix-parent</artifactId>
    <version>2.7.0</version>
</parent>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>
</dependencies>
```

**After (v3.x):**
```xml
<parent>
    <groupId>io.brix</groupId>
    <artifactId>brix-parent</artifactId>
    <version>3.0.0</version>
</parent>

<dependencies>
    <!-- Only SDK - no infrastructure -->
    <dependency>
        <groupId>io.brix</groupId>
        <artifactId>runtime-sdk-api</artifactId>
    </dependency>
</dependencies>
```

### Step 2: Migrate Repository Layer

**Before (v2.x):**
```java
// Direct JPA usage
@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
    List<Order> findByCustomerId(String customerId);
    
    @Query("SELECT o FROM Order o WHERE o.status = :status")
    List<Order> findByStatus(@Param("status") OrderStatus status);
}

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    
    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }
    
    public Order findById(String id) {
        return orderRepository.findById(id)
            .orElseThrow(() -> new OrderNotFoundException(id));
    }
}
```

**After (v3.x):**
```java
// Use DataAccessCapability
@Service
public class OrderService {
    private final DataAccessCapability dataAccess;
    
    public OrderService(DataAccessCapability dataAccess) {
        this.dataAccess = dataAccess;
    }
    
    public Order findById(String id) {
        Order order = dataAccess.findById(Order.class, id);
        if (order == null) {
            throw new OrderNotFoundException(id);
        }
        return order;
    }
    
    public List<Order> findByCustomerId(String customerId) {
        return dataAccess.findBy(Order.class, Map.of("customerId", customerId));
    }
}
```

### Step 3: Migrate Cache Usage

**Before (v2.x):**
```java
// Direct Redis usage
@Service
public class ProductService {
    private final RedisTemplate<String, Product> redisTemplate;
    
    public Optional<Product> getCached(String id) {
        Product cached = redisTemplate.opsForValue().get("product:" + id);
        return Optional.ofNullable(cached);
    }
    
    public void cache(Product product) {
        redisTemplate.opsForValue().set(
            "product:" + product.getId(),
            product,
            Duration.ofMinutes(30)
        );
    }
}
```

**After (v3.x):**
```java
// Use CacheCapability
@Service
public class ProductService {
    private final CacheCapability cache;
    
    public Optional<Product> getCached(String id) {
        return cache.get("product:" + id, Product.class);
    }
    
    public void cache(Product product) {
        cache.set("product:" + product.getId(), product, Duration.ofMinutes(30));
    }
}
```

### Step 4: Migrate Event Publishing

**Before (v2.x):**
```java
// Direct Kafka usage
@Service
public class OrderService {
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;
    
    public Order createOrder(CreateOrderCommand command) {
        Order order = // create order
        
        // Direct Kafka publish
        kafkaTemplate.send("order-events", new OrderCreatedEvent(
            order.getId(),
            order.getCustomerId()
        ));
        
        return order;
    }
}
```

**After (v3.x):**
```java
// Use EventBusCapability
@Service
public class OrderService {
    private final EventBusCapability eventBus;
    
    public Order createOrder(CreateOrderCommand command) {
        Order order = // create order
        
        // Capability publish
        eventBus.publish(new OrderCreatedEvent(
            UUID.randomUUID().toString(),
            Instant.now().toString(),
            order.getId(),
            order.getCustomerId()
        ));
        
        return order;
    }
}
```

### Step 5: Migrate Event Handlers

**Before (v2.x):**
```java
// Kafka listener
@Component
public class PaymentListener {
    
    @KafkaListener(topics = "payment-events")
    public void onPaymentEvent(PaymentEvent event) {
        // Handle event
    }
}
```

**After (v3.x):**
```java
// EventHandler annotation
@Component
public class PaymentEventHandler {
    
    @EventHandler(topic = "payment.completed")
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        // Idempotency check first!
        if (alreadyProcessed(event.eventId())) {
            return;
        }
        
        // Handle event
    }
}
```

### Step 6: Migrate Cross-Plugin Dependencies

**Before (v2.x):**
```java
// Direct dependency on another plugin
@Service
public class OrderService {
    private final InventoryService inventoryService;  // ❌ Cross-plugin import
    
    public Order createOrder(CreateOrderCommand command) {
        // Direct call
        inventoryService.reserveStock(command.getProductId(), command.getQuantity());
        // ...
    }
}
```

**After (v3.x):**
```java
// Event-driven communication
@Service
public class OrderService {
    private final EventBusCapability eventBus;  // ✅ Event bus only
    
    public Order createOrder(CreateOrderCommand command) {
        Order order = // save order
        
        // Publish event - Inventory plugin subscribes
        eventBus.publish(new OrderCreatedEvent(
            // ... event data
        ));
        
        return order;
    }
}

// In Inventory plugin - subscribes to event
@Component
public class OrderEventHandler {
    private final InventoryService inventoryService;
    
    @EventHandler(topic = "order.created")
    public void onOrderCreated(OrderCreatedEvent event) {
        inventoryService.reserveStock(event.orderId(), /* ... */);
    }
}
```

### Step 7: Migrate Frontend React Imports

**Before (v2.x):**
```typescript
// Direct React imports
import React, { useState, useEffect } from 'react';
import axios from 'axios';

function OrderList() {
    const [orders, setOrders] = useState([]);
    
    useEffect(() => {
        axios.get('/api/orders').then(res => setOrders(res.data));
    }, []);
}
```

**After (v3.x):**
```typescript
// From shared-runtime
import { React, useState, useEffect } from '@brix/shared-runtime-web';
import { useCapability, HttpCapability } from '@brix/runtime-sdk-api-web';

function OrderList() {
    const [orders, setOrders] = useState([]);
    const http = useCapability(HttpCapability);
    
    useEffect(() => {
        http.get<Order[]>('/api/orders').then(setOrders);
    }, [http]);
}
```

### Step 8: Add Architecture Tests

Add Architecture Guard tests to enforce rules:

```java
// src/test/java/com/example/ArchitectureTest.java
@AnalyzeClasses(packages = "com.example")
class ArchitectureTest extends AdapterIsolationRule {
    // Inherits all 13 standard rules
}
```

Run tests:
```bash
mvn test -Dtest=*ArchitectureTest
```

## Migration Checklist

### Backend

- [ ] Update parent POM to v3.0.0
- [ ] Remove direct infrastructure dependencies (JPA, Kafka, Redis)
- [ ] Add `runtime-sdk-api` dependency
- [ ] Replace `@Repository` with `DataAccessCapability`
- [ ] Replace `RedisTemplate` with `CacheCapability`
- [ ] Replace `KafkaTemplate` with `EventBusCapability`
- [ ] Replace `@KafkaListener` with `@EventHandler`
- [ ] Add idempotency checks to event handlers
- [ ] Remove cross-plugin service imports
- [ ] Use events for cross-plugin communication
- [ ] Update to Java 17 syntax (records, etc.)
- [ ] Add `ArchitectureTest` extending `AdapterIsolationRule`
- [ ] Run `mvn test` to verify architecture rules

### Frontend

- [ ] Update package.json dependencies
- [ ] Replace `react` imports with `@brix/shared-runtime-web`
- [ ] Replace `axios` with `HttpCapability`
- [ ] Update hooks to use capability pattern
- [ ] Test multi-plugin scenarios for React singleton

### Testing

- [ ] Update tests to use `@BrixTest` annotation
- [ ] Use `@MockCapability` for capability mocking
- [ ] Replace Testcontainers with mock capabilities
- [ ] Verify tests run without external infrastructure

## Common Issues

### "Multiple React instances" Error

**Cause:** Direct React import instead of shared-runtime.

**Fix:**
```typescript
// Change this:
import { useState } from 'react';

// To this:
import { useState } from '@brix/shared-runtime-web';
```

### Architecture Guard Failures

**Cause:** Direct infrastructure imports remaining.

**Fix:** Search for and replace:
- `org.apache.kafka` → Use `EventBusCapability`
- `redis.clients` → Use `CacheCapability`
- `jakarta.persistence` → Use `DataAccessCapability`
- `org.springframework.data` → Use `DataAccessCapability`

### Event Handler Not Invoked

**Cause:** Topic name mismatch.

**Fix:** Verify topic names match exactly:
```java
// Publisher
eventBus.publish(new MyEvent(...)); // publishes to topic derived from class

// Handler - topic must match
@EventHandler(topic = "my.event")  // Check topic name!
public void handle(MyEvent event) {}
```

## Resources

- [Architecture Concepts](../concepts/architecture-layers)
- [Capability Contract](../concepts/capability-contract)
- [Event Model](../concepts/event-model)
- [Architecture Guard](../guides/architecture-guard)
