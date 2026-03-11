---
id: backend-development
title: Backend Development Guide
sidebar_label: Backend Development
sidebar_position: 3
---

# Backend Development Guide

This guide covers building backend services for Brix plugins using Java and Spring Boot.

## Project Structure

```
my-plugin-core/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/myplugin/
│   │   │       ├── service/           # Domain services
│   │   │       │   ├── OrderService.java
│   │   │       │   └── impl/
│   │   │       │       └── OrderServiceImpl.java
│   │   │       ├── handler/           # Event handlers
│   │   │       │   └── PaymentEventHandler.java
│   │   │       ├── controller/        # REST controllers
│   │   │       │   └── OrderController.java
│   │   │       └── config/            # Module configuration
│   │   │           └── OrderModuleConfig.java
│   │   └── resources/
│   │       └── db/migration/          # Flyway migrations
│   │           └── V1__Create_orders.sql
│   └── test/
│       └── java/
│           └── com/example/myplugin/
│               ├── service/
│               │   └── OrderServiceTest.java
│               └── ArchitectureTest.java
└── pom.xml
```

## Maven Configuration

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>io.brix</groupId>
        <artifactId>brix-parent</artifactId>
        <version>3.0.0</version>
    </parent>
    
    <artifactId>my-plugin-core</artifactId>
    <name>My Plugin :: Core</name>
    
    <dependencies>
        <!-- Brix Runtime SDK -->
        <dependency>
            <groupId>io.brix</groupId>
            <artifactId>runtime-sdk-api</artifactId>
        </dependency>
        
        <!-- Platform Commons -->
        <dependency>
            <groupId>io.brix</groupId>
            <artifactId>platform-commons</artifactId>
        </dependency>
        
        <!-- Spring (only for annotations) -->
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-context</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework</groupId>
            <artifactId>spring-web</artifactId>
        </dependency>
        
        <!-- Validation -->
        <dependency>
            <groupId>jakarta.validation</groupId>
            <artifactId>jakarta.validation-api</artifactId>
        </dependency>
        
        <!-- Testing -->
        <dependency>
            <groupId>io.brix</groupId>
            <artifactId>testing-utils</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.brix</groupId>
            <artifactId>architecture-guard</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

## Domain Services

Domain services contain business logic and use capabilities for infrastructure.

### Service Interface

```java
// src/main/java/com/example/myplugin/service/OrderService.java
package com.example.myplugin.service;

import com.example.myplugin.shared.Order;
import com.example.myplugin.shared.CreateOrderCommand;
import java.util.List;
import java.util.Optional;

/**
 * Order management service.
 * Handles order lifecycle operations.
 */
public interface OrderService {
    
    /**
     * Creates a new order.
     *
     * @param command the order creation command
     * @return the created order
     * @throws ValidationException if command is invalid
     */
    Order createOrder(CreateOrderCommand command);
    
    /**
     * Finds an order by ID.
     *
     * @param orderId the order identifier
     * @return the order if found
     */
    Optional<Order> findById(String orderId);
    
    /**
     * Lists all orders for a customer.
     *
     * @param customerId the customer identifier
     * @return list of orders
     */
    List<Order> findByCustomer(String customerId);
    
    /**
     * Completes an order.
     *
     * @param orderId the order identifier
     * @return the completed order
     * @throws OrderNotFoundException if order not found
     * @throws InvalidOrderStateException if order cannot be completed
     */
    Order completeOrder(String orderId);
    
    /**
     * Cancels an order.
     *
     * @param orderId the order identifier
     * @param reason the cancellation reason
     */
    void cancelOrder(String orderId, String reason);
}
```

### Service Implementation

```java
// src/main/java/com/example/myplugin/service/impl/OrderServiceImpl.java
package com.example.myplugin.service.impl;

import com.example.myplugin.service.OrderService;
import com.example.myplugin.shared.*;
import com.example.myplugin.event.OrderCreatedEvent;
import com.example.myplugin.event.OrderCompletedEvent;
import io.brix.runtime.sdk.api.DataAccessCapability;
import io.brix.runtime.sdk.api.EventBusCapability;
import io.brix.runtime.sdk.api.CacheCapability;
import io.brix.runtime.sdk.api.LockCapability;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Default implementation of {@link OrderService}.
 * Uses capabilities for all infrastructure operations.
 */
@Service
public class OrderServiceImpl implements OrderService {
    
    private static final String CACHE_PREFIX = "order:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(15);
    
    private final DataAccessCapability dataAccess;
    private final EventBusCapability eventBus;
    private final CacheCapability cache;
    private final LockCapability lock;
    
    /**
     * Constructs OrderServiceImpl with required capabilities.
     */
    public OrderServiceImpl(
            DataAccessCapability dataAccess,
            EventBusCapability eventBus,
            CacheCapability cache,
            LockCapability lock) {
        this.dataAccess = dataAccess;
        this.eventBus = eventBus;
        this.cache = cache;
        this.lock = lock;
    }
    
    @Override
    @Transactional
    public Order createOrder(CreateOrderCommand command) {
        // Validate command
        validateCommand(command);
        
        // Create order entity
        Order order = Order.builder()
            .id(UUID.randomUUID().toString())
            .customerId(command.customerId())
            .items(command.items())
            .status(OrderStatus.PENDING)
            .createdAt(Instant.now())
            .build();
        
        // Persist using DataAccessCapability
        Order saved = dataAccess.save(order);
        
        // Publish domain event
        eventBus.publish(new OrderCreatedEvent(
            saved.getId(),
            saved.getCustomerId(),
            saved.getTotal()
        ));
        
        // Cache the order
        cache.set(CACHE_PREFIX + saved.getId(), saved, CACHE_TTL);
        
        return saved;
    }
    
    @Override
    public Optional<Order> findById(String orderId) {
        // Try cache first
        Optional<Order> cached = cache.get(CACHE_PREFIX + orderId, Order.class);
        if (cached.isPresent()) {
            return cached;
        }
        
        // Fall back to database
        Optional<Order> order = Optional.ofNullable(
            dataAccess.findById(Order.class, orderId)
        );
        
        // Cache if found
        order.ifPresent(o -> cache.set(CACHE_PREFIX + orderId, o, CACHE_TTL));
        
        return order;
    }
    
    @Override
    public List<Order> findByCustomer(String customerId) {
        Map<String, Object> criteria = Map.of("customerId", customerId);
        return dataAccess.findBy(Order.class, criteria);
    }
    
    @Override
    @Transactional
    public Order completeOrder(String orderId) {
        // Acquire distributed lock for order modification
        var orderLock = lock.acquire("order:" + orderId, Duration.ofSeconds(10));
        
        try {
            Order order = findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
            
            if (order.getStatus() != OrderStatus.PENDING) {
                throw new InvalidOrderStateException(
                    "Cannot complete order in status: " + order.getStatus()
                );
            }
            
            // Update status
            order.setStatus(OrderStatus.COMPLETED);
            order.setCompletedAt(Instant.now());
            
            Order saved = dataAccess.save(order);
            
            // Publish completion event
            eventBus.publish(new OrderCompletedEvent(
                saved.getId(),
                saved.getCustomerId(),
                saved.getTotal(),
                saved.getCompletedAt()
            ));
            
            // Update cache
            cache.set(CACHE_PREFIX + orderId, saved, CACHE_TTL);
            
            return saved;
        } finally {
            lock.release(orderLock);
        }
    }
    
    @Override
    @Transactional
    public void cancelOrder(String orderId, String reason) {
        Order order = findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));
        
        if (order.getStatus() == OrderStatus.COMPLETED) {
            throw new InvalidOrderStateException("Cannot cancel completed order");
        }
        
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason(reason);
        order.setCancelledAt(Instant.now());
        
        dataAccess.save(order);
        
        // Invalidate cache
        cache.delete(CACHE_PREFIX + orderId);
        
        // Publish cancellation event
        eventBus.publish(new OrderCancelledEvent(orderId, reason));
    }
    
    private void validateCommand(CreateOrderCommand command) {
        if (command.customerId() == null || command.customerId().isBlank()) {
            throw new ValidationException("customerId is required");
        }
        if (command.items() == null || command.items().isEmpty()) {
            throw new ValidationException("At least one item is required");
        }
    }
}
```

## Event Handlers

Handle events from other plugins or external systems.

```java
// src/main/java/com/example/myplugin/handler/PaymentEventHandler.java
package com.example.myplugin.handler;

import com.example.myplugin.service.OrderService;
import io.brix.runtime.sdk.api.EventHandler;
import io.brix.runtime.sdk.api.DataAccessCapability;
import io.brix.platform.commons.event.IntegrationEvent;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles payment-related events.
 * Updates order status based on payment outcomes.
 */
@Component
public class PaymentEventHandler {
    
    // Idempotency tracking (in production, use CacheCapability)
    private final Set<String> processedEvents = ConcurrentHashMap.newKeySet();
    
    private final OrderService orderService;
    private final DataAccessCapability dataAccess;
    
    /**
     * Constructs handler with dependencies.
     */
    public PaymentEventHandler(OrderService orderService, DataAccessCapability dataAccess) {
        this.orderService = orderService;
        this.dataAccess = dataAccess;
    }
    
    /**
     * Handles payment completion.
     * Marks the order as ready for fulfillment.
     *
     * @param event the payment completed event
     */
    @EventHandler(topic = "payment.completed")
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        // Idempotency check - skip if already processed
        if (!processedEvents.add(event.eventId())) {
            return;
        }
        
        try {
            // Complete the order
            orderService.completeOrder(event.orderId());
        } catch (Exception e) {
            // Remove from processed set on failure for retry
            processedEvents.remove(event.eventId());
            throw e;
        }
    }
    
    /**
     * Handles payment failure.
     * Cancels the order with payment failure reason.
     *
     * @param event the payment failed event
     */
    @EventHandler(topic = "payment.failed")
    public void onPaymentFailed(PaymentFailedEvent event) {
        if (!processedEvents.add(event.eventId())) {
            return;
        }
        
        try {
            orderService.cancelOrder(
                event.orderId(), 
                "Payment failed: " + event.reason()
            );
        } catch (Exception e) {
            processedEvents.remove(event.eventId());
            throw e;
        }
    }
    
    /**
     * Payment completed event record.
     */
    public record PaymentCompletedEvent(
        String eventId,
        String orderId,
        String paymentId,
        String amount
    ) implements IntegrationEvent {}
    
    /**
     * Payment failed event record.
     */
    public record PaymentFailedEvent(
        String eventId,
        String orderId,
        String reason
    ) implements IntegrationEvent {}
}
```

## REST Controllers

Controllers expose services via HTTP.

```java
// src/main/java/com/example/myplugin/controller/OrderController.java
package com.example.myplugin.controller;

import com.example.myplugin.service.OrderService;
import com.example.myplugin.shared.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API for order management.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {
    
    private final OrderService orderService;
    
    /**
     * Constructs controller with OrderService.
     */
    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }
    
    /**
     * Creates a new order.
     *
     * @param command the order creation command
     * @return the created order
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDto createOrder(@Valid @RequestBody CreateOrderCommand command) {
        Order order = orderService.createOrder(command);
        return toDto(order);
    }
    
    /**
     * Gets an order by ID.
     *
     * @param id the order ID
     * @return the order
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderDto> getOrder(@PathVariable String id) {
        return orderService.findById(id)
            .map(this::toDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Lists orders for a customer.
     *
     * @param customerId the customer ID
     * @return list of orders
     */
    @GetMapping
    public List<OrderDto> listOrders(@RequestParam String customerId) {
        return orderService.findByCustomer(customerId)
            .stream()
            .map(this::toDto)
            .toList();
    }
    
    /**
     * Completes an order.
     *
     * @param id the order ID
     * @return the completed order
     */
    @PostMapping("/{id}/complete")
    public OrderDto completeOrder(@PathVariable String id) {
        Order order = orderService.completeOrder(id);
        return toDto(order);
    }
    
    /**
     * Cancels an order.
     *
     * @param id the order ID
     * @param request the cancellation request
     */
    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelOrder(
            @PathVariable String id, 
            @RequestBody CancelOrderRequest request) {
        orderService.cancelOrder(id, request.reason());
    }
    
    private OrderDto toDto(Order order) {
        return new OrderDto(
            order.getId(),
            order.getCustomerId(),
            order.getItems(),
            order.getStatus().name(),
            order.getTotal().toString(),
            order.getCreatedAt().toString()
        );
    }
}
```

## Database Migrations

Use Flyway for versioned migrations:

```sql
-- src/main/resources/db/migration/V1__Create_orders.sql

CREATE TABLE orders (
    id VARCHAR(36) PRIMARY KEY,
    customer_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total DECIMAL(10, 2) NOT NULL,
    cancellation_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    
    INDEX idx_orders_customer (customer_id),
    INDEX idx_orders_status (status)
);

CREATE TABLE order_items (
    id VARCHAR(36) PRIMARY KEY,
    order_id VARCHAR(36) NOT NULL,
    product_id VARCHAR(36) NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    INDEX idx_order_items_order (order_id)
);
```

```sql
-- src/main/resources/db/migration/V2__Add_order_notes.sql

ALTER TABLE orders 
ADD COLUMN notes TEXT;
```

## Module Configuration

```java
// src/main/java/com/example/myplugin/config/OrderModuleConfig.java
package com.example.myplugin.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for the Order module.
 * Enables component scanning for this plugin.
 */
@Configuration
@ComponentScan(basePackages = "com.example.myplugin")
public class OrderModuleConfig {
    // Additional beans can be defined here if needed
}
```

## Testing Services

```java
// src/test/java/com/example/myplugin/service/OrderServiceTest.java
package com.example.myplugin.service;

import com.example.myplugin.service.impl.OrderServiceImpl;
import com.example.myplugin.shared.*;
import com.example.myplugin.event.OrderCreatedEvent;
import io.brix.runtime.sdk.api.*;
import io.brix.testing.BrixTest;
import io.brix.testing.MockCapability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderServiceImpl.
 */
@BrixTest
class OrderServiceTest {
    
    @MockCapability
    private DataAccessCapability dataAccess;
    
    @MockCapability
    private EventBusCapability eventBus;
    
    @MockCapability
    private CacheCapability cache;
    
    @MockCapability
    private LockCapability lock;
    
    private OrderServiceImpl orderService;
    
    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(dataAccess, eventBus, cache, lock);
        
        // Default lock behavior
        when(lock.acquire(anyString(), any())).thenReturn(mock(Lock.class));
    }
    
    @Test
    void createOrder_shouldPersistAndPublishEvent() {
        // Arrange
        CreateOrderCommand command = new CreateOrderCommand(
            "customer-1",
            List.of(new OrderItem("product-1", "Widget", 2, 9.99))
        );
        
        when(dataAccess.save(any(Order.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        
        // Act
        Order result = orderService.createOrder(command);
        
        // Assert
        assertThat(result.getId()).isNotNull();
        assertThat(result.getCustomerId()).isEqualTo("customer-1");
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        
        // Verify event published
        ArgumentCaptor<OrderCreatedEvent> eventCaptor = 
            ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(eventBus).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().orderId()).isEqualTo(result.getId());
        
        // Verify cached
        verify(cache).set(eq("order:" + result.getId()), eq(result), any());
    }
    
    @Test
    void findById_shouldReturnFromCache() {
        // Arrange
        Order cached = Order.builder()
            .id("order-1")
            .customerId("customer-1")
            .build();
        
        when(cache.get("order:order-1", Order.class))
            .thenReturn(Optional.of(cached));
        
        // Act
        Optional<Order> result = orderService.findById("order-1");
        
        // Assert
        assertThat(result).contains(cached);
        verify(dataAccess, never()).findById(any(), any());
    }
    
    @Test
    void completeOrder_shouldAcquireLock() {
        // Arrange
        Order order = Order.builder()
            .id("order-1")
            .customerId("customer-1")
            .status(OrderStatus.PENDING)
            .build();
        
        when(cache.get(anyString(), eq(Order.class)))
            .thenReturn(Optional.of(order));
        when(dataAccess.save(any(Order.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        
        // Act
        orderService.completeOrder("order-1");
        
        // Assert
        verify(lock).acquire(eq("order:order-1"), any());
        verify(lock).release(any());
    }
    
    @Test
    void createOrder_shouldRejectEmptyItems() {
        // Arrange
        CreateOrderCommand command = new CreateOrderCommand(
            "customer-1",
            List.of()  // Empty items
        );
        
        // Act & Assert
        assertThatThrownBy(() -> orderService.createOrder(command))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("At least one item is required");
    }
}
```

## Best Practices

1. **Use capabilities, not infrastructure** - Never import Kafka, Redis, etc.
2. **Keep services stateless** - Use capabilities for state
3. **Handle idempotency** - Track processed event IDs
4. **Use distributed locks** - For concurrent modifications
5. **Cache strategically** - Read-heavy data benefits from caching
6. **Document public APIs** - Javadoc on all public methods
7. **Test thoroughly** - Mock capabilities for fast unit tests

## Next Steps

- [Frontend Development](./frontend-development) - Build the frontend
- [Testing Guide](./testing) - Complete testing strategies
- [Architecture Guard](./architecture-guard) - Enforce rules
