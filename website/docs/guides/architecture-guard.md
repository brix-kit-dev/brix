---
id: architecture-guard
title: Architecture Guard
sidebar_label: Architecture Guard
sidebar_position: 6
---

# Architecture Guard

**Architecture Guard** is Brix's built-in enforcement system that ensures all code adheres to the Runtime Shell Architecture. It uses ArchUnit to automatically verify 13 red-line rules.

## Overview

Every Brix plugin includes Architecture Guard tests that run during build. These tests:
- Prevent infrastructure leakage into plugins
- Enforce layer boundaries
- Validate capability-only dependencies
- Block cross-plugin direct dependencies

## The 13 Red-Line Rules

### Rule 1: No Infrastructure Imports in Plugins

Plugins must not import infrastructure clients directly.

```java
@ArchTest
static final ArchRule noInfrastructureImports = noClasses()
    .that().resideInAPackage("..plugin..")
    .should().dependOnClassesThat()
    .resideInAnyPackage(
        "org.apache.kafka..",          // Kafka client
        "redis.clients..",              // Jedis
        "org.springframework.data.redis..", // Spring Redis
        "org.springframework.kafka..",  // Spring Kafka
        "io.minio..",                   // MinIO client
        "com.amazonaws..",              // AWS SDK
        "org.postgresql.."              // PostgreSQL driver
    );
```

**Example Violation:**
```java
// ❌ VIOLATION: Direct Kafka import in plugin
import org.apache.kafka.clients.producer.KafkaProducer;

@Service
public class OrderService {
    private final KafkaProducer<String, String> producer; // BLOCKED!
}
```

**Correct Approach:**
```java
// ✅ CORRECT: Use EventBusCapability
import io.brix.runtime.sdk.api.EventBusCapability;

@Service
public class OrderService {
    private final EventBusCapability eventBus; // OK!
}
```

---

### Rule 2: Core Depends Only on Contracts

The `core` package can only depend on capability contracts and its own classes.

```java
@ArchTest
static final ArchRule coreDependsOnContractsOnly = classes()
    .that().resideInAPackage("..core..")
    .should().onlyDependOnClassesThat()
    .resideInAnyPackage(
        "io.brix.runtime.sdk.api..",   // Capability contracts
        "..core..",                     // Own package
        "..shared..",                   // Shared types
        "java..",                       // Java stdlib
        "org.springframework.stereotype..",
        "org.springframework.transaction..",
        "jakarta.validation.."
    );
```

---

### Rule 3: No Cross-Plugin Dependencies

Plugins must not directly depend on other plugins.

```java
@ArchTest
static final ArchRule noCrossPluginDependencies = noClasses()
    .that().resideInAPackage("..reservation..")
    .should().dependOnClassesThat()
    .resideInAPackage("..order..")
    .orShould().dependOnClassesThat()
    .resideInAPackage("..payment..")
    .because("Plugins communicate via events, not direct dependencies");
```

---

### Rule 4: Controllers Depend on Services Only

REST controllers should only depend on domain services.

```java
@ArchTest
static final ArchRule controllersDependOnServicesOnly = classes()
    .that().resideInAPackage("..controller..")
    .should().onlyDependOnClassesThat()
    .resideInAnyPackage(
        "..service..",
        "..shared..",
        "org.springframework.web..",
        "org.springframework.http..",
        "java.."
    );
```

---

### Rule 5: No Direct Database Access in Domain

Domain services use DataAccessCapability, not JPA directly.

```java
@ArchTest
static final ArchRule noDirectJpaInDomain = noClasses()
    .that().resideInAPackage("..core.service..")
    .should().dependOnClassesThat()
    .resideInAnyPackage(
        "jakarta.persistence..",
        "org.springframework.data.jpa..",
        "org.hibernate.."
    );
```

---

### Rule 6: Event Handlers Are Idempotent

Event handlers should store processed event IDs for idempotency.

```java
// This is a code review guideline enforced by documentation
// Event handlers MUST check for duplicate delivery:

@EventHandler("payment.completed")
public void onPaymentCompleted(PaymentCompletedEvent event) {
    // ✅ REQUIRED: Idempotency check
    if (processedEvents.contains(event.getId())) {
        return;
    }
    
    // Process event
    processedEvents.add(event.getId());
}
```

---

### Rule 7: Shared Types Are Primitives

Integration event payloads use only primitive types.

```java
// ✅ CORRECT: Primitives and strings
public record OrderCompletedIntegrationEvent(
    String orderId,
    String customerId,
    String totalAmount,     // String, not BigDecimal
    String completedAt      // String, not Instant
) {}

// ❌ WRONG: Complex domain types
public record OrderCompletedIntegrationEvent(
    String orderId,
    Customer customer,      // Domain object - BLOCKED!
    Money totalAmount       // Value object - BLOCKED!
) {}
```

---

### Rule 8: No Business Logic in Adapters

Adapters implement contracts, they don't add business logic.

```java
@ArchTest
static final ArchRule adaptersOnlyImplementContracts = classes()
    .that().resideInAPackage("io.brix.infra.adapter..")
    .should().implement(Capability.class)
    .orShould().beAnnotatedWith(Configuration.class);
```

---

### Rule 9: Host Has No Implementation

Host modules contain only configuration.

```java
@ArchTest
static final ArchRule hostHasNoServices = noClasses()
    .that().resideInAPackage("..host..")
    .should().beAnnotatedWith(Service.class)
    .orShould().beAnnotatedWith(Component.class)
    .as("Host is ultra-thin - no services allowed");
```

---

### Rule 10: Frontend Uses HttpCapability

Frontend code uses HttpCapability, not fetch/axios.

```typescript
// Architecture lint rule (ESLint)
// "no-restricted-imports": ["error", {
//   "patterns": ["axios", "fetch"]
// }]

// ❌ BLOCKED by ESLint
import axios from 'axios';

// ✅ ALLOWED
import { useCapability, HttpCapability } from '@brix/runtime-sdk-api-web';
```

---

### Rule 11: View Layer Is Pure

View components don't call APIs directly.

```tsx
// ❌ VIOLATION: Direct API call in component
function ReservationList() {
  useEffect(() => {
    fetch('/api/reservations')  // BLOCKED!
      .then(res => res.json());
  }, []);
}

// ✅ CORRECT: Uses ViewModel hook
function ReservationList() {
  const { reservations } = useReservations(); // Hook handles API
}
```

---

### Rule 12: Shared Runtime Single Source

All React imports come from shared-runtime.

```typescript
// ❌ VIOLATION: Direct React import
import React from 'react';
import { useState } from 'react';

// ✅ CORRECT: From shared-runtime
import { React, useState } from '@brix/shared-runtime-web';
```

---

### Rule 13: No Circular Dependencies

No circular dependencies between packages.

```java
@ArchTest
static final ArchRule noCircularDependencies = slices()
    .matching("com.example.(*)..")
    .should().beFreeOfCycles();
```

## Using Architecture Guard

### 1. Add Dependency

```xml
<dependency>
    <groupId>io.brix</groupId>
    <artifactId>architecture-guard</artifactId>
    <scope>test</scope>
</dependency>
```

### 2. Create Test Class

```java
package com.example.myplugin;

import io.brix.architecture.guard.AdapterIsolationRule;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.example.myplugin")
class ArchitectureTest extends AdapterIsolationRule {
    
    // All 13 rules are inherited from AdapterIsolationRule
    
    // Add custom rules if needed:
    @ArchTest
    static final ArchRule customRule = // your rule
}
```

### 3. Run Tests

```bash
mvn test

# Output:
# [INFO] Running com.example.myplugin.ArchitectureTest
# [INFO] Tests run: 13, Failures: 0, Errors: 0
```

## CI/CD Integration

Architecture Guard runs in CI pipelines:

```yaml
# .github/workflows/architecture-guard.yml
name: Architecture Guard

on: [push, pull_request]

jobs:
  architecture-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
      
      - name: Run Architecture Tests
        run: mvn test -Dtest=*ArchitectureTest
```

## Suppressing Rules (Not Recommended)

In rare cases, you may need to suppress a rule:

```java
@ArchTest
@ArchIgnore(reason = "Legacy code - migration planned for v3.2")
static final ArchRule noKafkaInDomain = // ...
```

:::warning
Suppressing rules should be rare and well-documented. Architecture Guard exists to prevent architectural decay.
:::

## Next Steps

- [Plugin Development](./plugin-development) - Build compliant plugins
- [Testing Guide](./testing) - Full testing strategy
- [Architecture Layers](../concepts/architecture-layers) - Layer details
