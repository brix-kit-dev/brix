---
id: testing
title: Testing Guide
sidebar_label: Testing
sidebar_position: 4
---

# Testing Guide

This guide covers testing strategies for Brix plugins, including unit tests, integration tests, and architecture tests.

## Testing Philosophy

Brix's capability-based architecture enables **fast, isolated unit tests** without spinning up real infrastructure:

| Traditional | Brix |
|-------------|------|
| Integration tests with real DB | Unit tests with mocked DataAccessCapability |
| TestContainers for Kafka | Mocked EventBusCapability |
| Embedded Redis | Mocked CacheCapability |
| Slow, flaky tests | Fast, reliable tests |

## Test Dependencies

### Java (Backend)

```xml
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
<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.assertj</groupId>
    <artifactId>assertj-core</artifactId>
    <scope>test</scope>
</dependency>
```

### TypeScript (Frontend)

```json
{
  "devDependencies": {
    "@brix/testing-utils-web": "workspace:*",
    "@testing-library/react": "^14.0.0",
    "@testing-library/jest-dom": "^6.0.0",
    "vitest": "^1.0.0"
  }
}
```

## Unit Testing

### Backend Services

Use `@BrixTest` annotation for automatic capability mocking:

```java
// OrderServiceTest.java
package com.example.order.service;

import io.brix.testing.BrixTest;
import io.brix.testing.MockCapability;
import io.brix.runtime.sdk.api.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@BrixTest
class OrderServiceTest {
    
    @MockCapability
    private DataAccessCapability dataAccess;
    
    @MockCapability
    private EventBusCapability eventBus;
    
    @MockCapability
    private CacheCapability cache;
    
    private OrderService orderService;
    
    @BeforeEach
    void setUp() {
        orderService = new OrderServiceImpl(dataAccess, eventBus, cache);
    }
    
    @Test
    void shouldCreateOrderAndPublishEvent() {
        // Arrange
        var command = new CreateOrderCommand("customer-1", List.of(
            new OrderItem("product-1", "Widget", 2, 9.99)
        ));
        
        when(dataAccess.save(any(Order.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        
        // Act
        Order result = orderService.createOrder(command);
        
        // Assert
        assertThat(result.getId()).isNotNull();
        assertThat(result.getCustomerId()).isEqualTo("customer-1");
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        
        // Verify event was published
        var eventCaptor = ArgumentCaptor.forClass(OrderCreatedEvent.class);
        verify(eventBus).publish(eventCaptor.capture());
        
        var event = eventCaptor.getValue();
        assertThat(event.orderId()).isEqualTo(result.getId());
    }
    
    @Test
    void shouldReturnCachedOrder() {
        // Arrange
        var cachedOrder = Order.builder()
            .id("order-123")
            .customerId("customer-1")
            .build();
        
        when(cache.get("order:order-123", Order.class))
            .thenReturn(Optional.of(cachedOrder));
        
        // Act
        var result = orderService.findById("order-123");
        
        // Assert
        assertThat(result).hasValue(cachedOrder);
        verify(dataAccess, never()).findById(any(), any());  // Cache hit
    }
    
    @Test
    void shouldFallbackToDatabaseOnCacheMiss() {
        // Arrange
        when(cache.get(anyString(), any())).thenReturn(Optional.empty());
        
        var order = Order.builder()
            .id("order-123")
            .customerId("customer-1")
            .build();
        
        when(dataAccess.findById(Order.class, "order-123"))
            .thenReturn(order);
        
        // Act
        var result = orderService.findById("order-123");
        
        // Assert
        assertThat(result).hasValue(order);
        verify(cache).set(eq("order:order-123"), eq(order), any());  // Cached
    }
}
```

### Event Handlers

Test event handlers with mocked dependencies:

```java
@BrixTest
class PaymentEventHandlerTest {
    
    @Mock
    private OrderService orderService;
    
    private PaymentEventHandler handler;
    
    @BeforeEach
    void setUp() {
        handler = new PaymentEventHandler(orderService);
    }
    
    @Test
    void shouldCompleteOrderOnPaymentSuccess() {
        // Arrange
        var event = new PaymentCompletedEvent(
            "event-1",
            "order-123",
            "payment-456",
            "99.99"
        );
        
        // Act
        handler.onPaymentCompleted(event);
        
        // Assert
        verify(orderService).completeOrder("order-123");
    }
    
    @Test
    void shouldBeIdempotent() {
        // Arrange
        var event = new PaymentCompletedEvent(
            "event-1",  // Same event ID
            "order-123",
            "payment-456",
            "99.99"
        );
        
        // Act - process twice
        handler.onPaymentCompleted(event);
        handler.onPaymentCompleted(event);
        
        // Assert - only processed once
        verify(orderService, times(1)).completeOrder("order-123");
    }
}
```

### Frontend ViewModels

Use `renderHook` with mocked capabilities:

```typescript
// useOrders.test.ts
import { renderHook, act, waitFor } from '@testing-library/react';
import { mockCapability, CapabilityProvider } from '@brix/testing-utils-web';
import { HttpCapability } from '@brix/runtime-sdk-api-web';
import { useOrders } from '../useOrders';

describe('useOrders', () => {
  let mockHttp: jest.Mocked<HttpCapability>;
  
  beforeEach(() => {
    mockHttp = mockCapability(HttpCapability);
  });
  
  function wrapper({ children }: { children: React.ReactNode }) {
    return (
      <CapabilityProvider capabilities={{ [HttpCapability]: mockHttp }}>
        {children}
      </CapabilityProvider>
    );
  }
  
  it('should load orders on mount', async () => {
    const orders = [
      { id: '1', customerName: 'Alice', total: 100 },
      { id: '2', customerName: 'Bob', total: 200 },
    ];
    mockHttp.get.mockResolvedValue(orders);
    
    const { result } = renderHook(() => useOrders(), { wrapper });
    
    // Initially loading
    expect(result.current.isLoading).toBe(true);
    
    // Wait for load
    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });
    
    expect(result.current.orders).toEqual(orders);
    expect(mockHttp.get).toHaveBeenCalledWith('/api/orders');
  });
  
  it('should handle errors', async () => {
    mockHttp.get.mockRejectedValue(new Error('Network error'));
    
    const { result } = renderHook(() => useOrders(), { wrapper });
    
    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });
    
    expect(result.current.error?.message).toBe('Network error');
    expect(result.current.orders).toEqual([]);
  });
  
  it('should delete order optimistically', async () => {
    const orders = [
      { id: '1', customerName: 'Alice', total: 100 },
      { id: '2', customerName: 'Bob', total: 200 },
    ];
    mockHttp.get.mockResolvedValue(orders);
    mockHttp.delete.mockResolvedValue(undefined);
    
    const { result } = renderHook(() => useOrders(), { wrapper });
    
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    
    await act(async () => {
      await result.current.deleteOrder('1');
    });
    
    expect(result.current.orders).toHaveLength(1);
    expect(result.current.orders[0].id).toBe('2');
    expect(mockHttp.delete).toHaveBeenCalledWith('/api/orders/1');
  });
});
```

### Frontend Components

Test components with Testing Library:

```tsx
// OrderTable.test.tsx
import { React } from '@brix/shared-runtime-web';
import { render, screen, fireEvent } from '@testing-library/react';
import { OrderTable } from '../OrderTable';

describe('OrderTable', () => {
  const orders = [
    { id: '1', customerName: 'Alice', total: 100 },
    { id: '2', customerName: 'Bob', total: 200 },
  ];
  
  it('should render all orders', () => {
    render(<OrderTable orders={orders} onDelete={() => {}} />);
    
    expect(screen.getByText('Alice')).toBeInTheDocument();
    expect(screen.getByText('Bob')).toBeInTheDocument();
    expect(screen.getByText('$100.00')).toBeInTheDocument();
    expect(screen.getByText('$200.00')).toBeInTheDocument();
  });
  
  it('should call onDelete with correct id', () => {
    const onDelete = jest.fn();
    render(<OrderTable orders={orders} onDelete={onDelete} />);
    
    const deleteButtons = screen.getAllByRole('button', { name: /delete/i });
    fireEvent.click(deleteButtons[0]);
    
    expect(onDelete).toHaveBeenCalledWith('1');
  });
  
  it('should show empty state', () => {
    render(<OrderTable orders={[]} onDelete={() => {}} />);
    
    expect(screen.getByText(/no orders/i)).toBeInTheDocument();
  });
});
```

## Architecture Tests

Use ArchUnit to enforce architecture rules:

```java
// ArchitectureTest.java
package com.example.order;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import io.brix.architecture.guard.AdapterIsolationRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.*;

@AnalyzeClasses(packages = "com.example.order")
class ArchitectureTest extends AdapterIsolationRule {
    
    // Inherit all 13 standard rules from AdapterIsolationRule
    
    @ArchTest
    static final ArchRule servicesShouldNotAccessControllers = noClasses()
        .that().resideInAPackage("..service..")
        .should().dependOnClassesThat()
        .resideInAPackage("..controller..")
        .because("Services should not know about controllers");
    
    @ArchTest
    static final ArchRule handlersShouldNotAccessRepositories = noClasses()
        .that().resideInAPackage("..handler..")
        .should().dependOnClassesThat()
        .resideInAPackage("..repository..")
        .because("Handlers should use services, not repositories");
    
    @ArchTest
    static final ArchRule sharedShouldBeIndependent = classes()
        .that().resideInAPackage("..shared..")
        .should().onlyDependOnClassesThat()
        .resideInAnyPackage("..shared..", "java..", "jakarta..");
}
```

### Standard Architecture Rules

The `AdapterIsolationRule` base class includes:

1. **No infrastructure imports** - Kafka, Redis, etc.
2. **Core depends on contracts only**
3. **No cross-plugin dependencies**
4. **Controllers → Services only**
5. **No direct JPA in domain**
6. **Event handlers idempotent**
7. **Shared types are primitives**
8. **No business logic in adapters**
9. **Host has no implementation**
10. **Frontend uses HttpCapability**
11. **View layer is pure**
12. **Shared-runtime single source**
13. **No circular dependencies**

## Integration Tests

For complex scenarios, use `@BrixIntegrationTest`:

```java
@BrixIntegrationTest
@TestProfile("integration")
class OrderIntegrationTest {
    
    @Autowired
    private OrderService orderService;
    
    @Autowired
    private TestEventBus testEventBus;  // In-memory event bus
    
    @Test
    void shouldProcessOrderLifecycle() {
        // Create order
        var command = new CreateOrderCommand("customer-1", List.of(
            new OrderItem("product-1", "Widget", 1, 49.99)
        ));
        
        Order order = orderService.createOrder(command);
        
        // Verify event published
        var event = testEventBus.getPublished(OrderCreatedEvent.class).getFirst();
        assertThat(event.orderId()).isEqualTo(order.getId());
        
        // Simulate payment
        testEventBus.publish(new PaymentCompletedEvent(
            "event-1",
            order.getId(),
            "payment-123",
            "49.99"
        ));
        
        // Wait for processing
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var completed = orderService.findById(order.getId()).orElseThrow();
            assertThat(completed.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        });
    }
}
```

## Test Configuration

### Vitest Config (Frontend)

```typescript
// vitest.config.ts
import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    globals: true,
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      exclude: ['**/*.test.{ts,tsx}', '**/test/**'],
    },
  },
});
```

### Test Setup (Frontend)

```typescript
// src/test/setup.ts
import '@testing-library/jest-dom';
import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

// Cleanup after each test
afterEach(() => {
  cleanup();
});

// Mock shared-runtime
vi.mock('@brix/shared-runtime-web', async () => {
  const React = await import('react');
  return {
    React,
    ...React,
  };
});
```

## Test Utilities

### Capability Mocking

```java
// Java
@MockCapability
private CacheCapability cache;

// TypeScript
const mockCache = mockCapability(CacheCapability);
mockCache.get.mockResolvedValue({ key: 'value' });
```

### Event Testing

```java
// Capture published events
ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
verify(eventBus, times(2)).publish(captor.capture());

List<DomainEvent> events = captor.getAllValues();
assertThat(events).hasSize(2);
```

### Async Testing

```java
// Java - Awaitility
await()
    .atMost(Duration.ofSeconds(10))
    .pollInterval(Duration.ofMillis(100))
    .untilAsserted(() -> {
        var order = orderService.findById(orderId).orElseThrow();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    });
```

```typescript
// TypeScript - waitFor
await waitFor(() => {
  expect(result.current.isLoading).toBe(false);
}, { timeout: 5000 });
```

## Coverage Requirements

Recommended coverage thresholds:

| Type | Min Coverage |
|------|--------------|
| Services | 80% |
| ViewModels | 80% |
| Event Handlers | 90% |
| Controllers | 70% |
| Views | 60% |
| Overall | 75% |

Configure in Maven:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>check</id>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.75</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

## CI Integration

```yaml
# .github/workflows/test.yml
name: Tests

on: [push, pull_request]

jobs:
  backend-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Run tests
        run: mvn test
      
      - name: Upload coverage
        uses: codecov/codecov-action@v4
        with:
          files: '**/target/site/jacoco/jacoco.xml'
  
  frontend-tests:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: pnpm/action-setup@v2
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'pnpm'
      
      - run: pnpm install
      - run: pnpm test -- --coverage
      
      - name: Upload coverage
        uses: codecov/codecov-action@v4
        with:
          files: 'coverage/lcov.info'
```

## Next Steps

- [Plugin Development](./plugin-development) - End-to-end guide
- [Architecture Guard](./architecture-guard) - Enforcement rules
- [Deployment Guide](./deployment) - Production deployment
