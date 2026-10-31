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

Declare the event in `META-INF/brix/plugin-manifest.yaml` (`events.publishes` with its schema and reliability, plus
`data.outbox`), then publish it in the unit of work that changes your data:

```java
// The handler factory hands in the capabilities the manifest requires
persistence.unitOfWork().required(() -> {
    Order order = // create and save the order through persistence.repository(...)

    // Written to the plugin's outbox in the same transaction; the relay sends it after the commit
    events.publishIntegration(new OrderCreated(order));
    return order;
});
```

`OrderCreated` extends `IntegrationEvent`: it names its manifest event type, its routing key and its payload. See
[Events and Commands](../guides/events-and-commands.md#publishing-an-event).

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

Declare the subscription in the manifest (`events.subscribes` with `handlerId`, `schemaRange`,
`publisherPolicyRef: <publisher>-owner` and `idempotencyPolicyRef: persistent-inbox`, plus `data.inbox`), and bind a
handler class that names its payload type:

```java
bootstrap.bindEventHandlerFactory("orders.payment-completed.v1",
    context -> new PaymentCompletedHandler(context.require(PersistenceCapability.class)));

final class PaymentCompletedHandler implements EventHandler<PaymentCompleted> {
    @Override
    public CompletionStage<Void> handle(EventInvocation<PaymentCompleted> invocation) {
        // Runs in your unit of work, in the event's tenant; the runtime's inbox skips redeliveries,
        // so no idempotency bookkeeping is needed here.
        PaymentCompleted payment = invocation.event().payload();
        // ... change your data
        return CompletableFuture.completedFuture(null);
    }
}
```

See [Events and Commands](../guides/events-and-commands.md#handling-an-event).

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

Reserving stock changes Inventory's data, so the Order plugin sends Inventory a **command**; Inventory alone provides
it. (An event would only say that an order was created, and nobody would be obliged to reserve anything.)

```java
// Order plugin: depends on inventory-api, declares inventory.reserve-stock under commands.consumes and
// CommandCapability under capabilities.required, and takes the client in its handler factory
CommandClient<ReserveStock> reservations = context.require(CommandCapability.class)
    .client(InventoryCommands.RESERVE_STOCK);

persistence.unitOfWork().required(() -> {
    Order order = // save the order
    reservations.submit(ReserveStock.builder().productId(command.getProductId())
        .quantity(command.getQuantity()).build(), String.valueOf(order.getId()));
    return order;
});

// Inventory plugin: publishes the command's contract in inventory-api, declares it under commands.provides and binds
// its handler
bootstrap.bindCommandHandlerFactory("inventory.reserve-stock.v1", context -> new ReserveStockHandler(...));
```

The command commits with the order, in the order's tenant. Inventory applies it once per business key (the order
id) from the Order plugin in that tenant, and reports the outcome with its own event. See
[Commands](../guides/events-and-commands.md#commands).

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

Add the Architecture Guard to each backend module, with the ArchUnit engine that runs its rules, both at test scope:

```xml
<dependency>
    <groupId>io.brix</groupId>
    <artifactId>architecture-guard</artifactId>
    <version>3.2.0</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit6</artifactId>
    <version>1.5.1</version>
    <scope>test</scope>
</dependency>
```

Then add one test class. It applies the guard's rule set for plugin code:

```java
// src/test/java/com/example/ArchitectureTest.java
package com.example;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;

import io.brix.architecture.guard.BrixArchitectureRules;

@AnalyzeClasses(packages = "com.example", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchTests brixRules = BrixArchitectureRules.pluginProfile();
}
```

Do not extend a rule class such as `AdapterIsolationRule`: the rule classes are final and offer static rules only.
[Standard Architecture Rules](../guides/testing.md#standard-architecture-rules) lists what the plugin profile refuses.

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
- [ ] Replace `@KafkaListener` with a manifest subscription and a bound `EventHandler`
- [ ] Subscribe with `persistent-inbox` (the runtime deduplicates redeliveries)
- [ ] Remove cross-plugin service imports
- [ ] Use events for facts and commands for changes to another plugin's data
- [ ] Update to Java 17 syntax (records, etc.)
- [ ] Add an `ArchitectureTest` that applies `BrixArchitectureRules.pluginProfile()`
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

**Cause:** the subscription does not match the publication, or its handler is not bound.

**Fix:** check that the subscription's `eventType` is the publisher's `events.publishes[].eventType`, that its
`schemaRange` contains the published version, and that `configure(...)` binds a factory for its `handlerId`. A new
subscription receives only events published after it was first registered. Parked events are listed at
`GET /actuator/events` on the management port. See the [FAQ](../faq.md#plugin-events-not-received).

## Resources

- [Architecture Concepts](../concepts/architecture-layers)
- [Capability Contract](../concepts/capability-contract)
- [Event Model](../concepts/event-model)
- [Architecture Guard](../guides/architecture-guard)
