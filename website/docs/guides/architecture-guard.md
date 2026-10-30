---
id: architecture-guard
title: Architecture Guard
sidebar_label: Architecture Guard
sidebar_position: 6
---

# Architecture Guard

**Architecture Guard** is Brix's built-in enforcement system that ensures all code adheres to the Runtime Shell Architecture. It uses ArchUnit to automatically verify 13 red-line rules.

## Overview

Add Architecture Guard tests to every Brix plugin, as [Using Architecture Guard](#using-architecture-guard) shows.
They run with the build and:
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
// ✅ CORRECT: Use EventBusCapability, passed in by the handler factory
import io.runtime.sdk.capability.EventBusCapability;

public final class OrderService {
    private final EventBusCapability events; // OK!

    public OrderService(EventBusCapability events) {
        this.events = events;
    }
}
```

A domain service is a plain class: no `@Service`, and its capabilities come from the handler factory. See
[Domain services](./backend-development.md#domain-services).

---

### Rule 2: Core Depends Only on Contracts

The `core` package depends only on capability contracts, the plugin's own types, and the JPA and Spring Data types
of its entities and repositories.

```java
@ArchTest
static final ArchRule coreDependsOnContractsOnly = classes()
    .that().resideInAPackage("..core..")
    .should().onlyDependOnClassesThat()
    .resideInAnyPackage(
        "io.runtime.sdk..",             // Capability contracts
        "..core..",                     // Own package
        "..api..",                      // The plugin's api types
        "java..",                       // Java stdlib
        "jakarta.persistence..",        // Entities
        "org.springframework.data.."    // Repository interfaces
    );
```

This is a rule of your own, not part of the guard's plugin profile. The sample plugins' core artifacts pass it.

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

Delivery is at least once, so a handler may see an event twice. Subscribe with a persistent inbox and let the runtime
deduplicate: it records `(handlerId, eventId)` in your inbox in the same transaction as your handler's writes, and a
redelivered event never reaches the handler again.

```yaml
# META-INF/brix/plugin-manifest.yaml
events:
  subscribes:
    - subscriptionId: payment-completed
      eventType: PaymentCompleted
      schemaRange: ">=1.0.0 <2.0.0"
      handlerId: orders.payment-completed.v1
      scopePolicyRef: orders-payment-tenant
      publisherPolicyRef: payments-owner
      timeoutPolicyRef: event-standard
      concurrencyPolicyRef: partition-serial
      retryPolicyRef: event-standard
      idempotencyPolicyRef: persistent-inbox   # the runtime's inbox makes each event apply once
```

Keep everything the handler does inside its own unit of work. A side effect outside your data, such as a call to
another system, is repeated when the handler is retried. See [Events and Commands](./events-and-commands.md).

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

Add the guard and the ArchUnit engine that runs its rules with JUnit, both at test scope:

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

### 2. Create Test Class

```java
package com.example.myplugin;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;

import io.brix.architecture.guard.BrixArchitectureRules;

@AnalyzeClasses(packages = "com.example.myplugin", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** The guard's rule set for plugin code. */
    @ArchTest
    static final ArchTests brixRules = BrixArchitectureRules.pluginProfile();

    /** Rules of your own sit next to it, such as Rule 2 above. */
    @ArchTest
    static final ArchRule coreDependsOnContractsOnly = classes()
        .that().resideInAPackage("..core..")
        .should().onlyDependOnClassesThat()
        .resideInAnyPackage(
            "io.runtime.sdk..",
            "..core..",
            "..api..",
            "java..",
            "jakarta.persistence..",
            "org.springframework.data..");
}
```

- `BrixArchitectureRules`, in `io.brix.architecture.guard`, is the entry point. The rule classes behind it, such as
  `AdapterIsolationRule`, are final and offer static rules only: use them, do not extend them.
- [Standard Architecture Rules](./testing.md#standard-architecture-rules) lists what the plugin profile refuses.
- Other modules use other profiles: `adapterProfile()` for infrastructure adapters, `sdkProfile()` for the SDK,
  `commonsProfile()` for platform commons and `operationalProfile()` for platform operational modules.
  `hostProfile()` and `coreProfileV2()` are deprecated since 3.2.0.

### 3. Run Tests

```bash
mvn test
```

Every rule runs as a test. A class that breaks one fails the build, with the rule's reason and each offending
dependency.

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
