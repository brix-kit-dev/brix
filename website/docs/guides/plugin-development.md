---
id: plugin-development
title: Plugin Development Guide
sidebar_label: Plugin Development
sidebar_position: 1
---

# Plugin Development Guide

This comprehensive guide covers building production-ready plugins following the Brix Runtime Shell Architecture.

## Prerequisites

- Completed [Quick Start](../getting-started/quick-start)
- Understanding of [Plugin Model](../concepts/plugin-model)
- Familiarity with [Capability Contracts](../concepts/capability-contract)

## Part 1: Plugin Architecture

### 1.1 Directory Structure

```
my-plugin/
├── frontend/
│   ├── web/                        # Web frontend
│   │   ├── pages/                  # View layer
│   │   ├── components/             # Reusable components
│   │   ├── hooks/                  # ViewModel layer
│   │   ├── repositories/           # Data access layer
│   │   └── plugin.ts               # Plugin entry
│   ├── mobile/                     # Mobile frontend (optional)
│   └── shared/                     # Shared types
│       ├── types/                  # DTOs, enums
│       └── events/                 # Event contracts
│
├── backend/
│   ├── api/                        # plugin-api (Maven module): public DTOs, JSON schemas
│   ├── core/                       # plugin-core (Maven module): domain layer
│   │   ├── service/                # Domain services (plain classes)
│   │   ├── model/                  # Entities, value objects, integration events
│   │   └── repository/             # Spring Data repository interfaces
│   └── server/                     # plugin-server (Maven module)
│       ├── handler/                # Endpoint and event handlers
│       ├── MyPlugin.java           # BrixPlugin: binds the handler factories
│       └── resources/
│           ├── META-INF/brix/plugin-manifest.yaml
│           ├── META-INF/services/io.runtime.sdk.plugin.BrixPlugin
│           └── db/migration/my-plugin/   # Flyway migrations (business tables only)
│
├── package.json
└── pom.xml
```

### 1.2 Layer Responsibilities

| Layer | Package | Responsibility | Dependencies |
|-------|---------|----------------|--------------|
| **View** | `pages/`, `components/` | UI rendering | ViewModel hooks |
| **ViewModel** | `hooks/` | State, business logic | Repositories |
| **Repository** | `repositories/` | API calls | HttpCapability |
| **Domain** | `core/service/` | Business rules | Repositories and capabilities, passed in by handler factories |
| **Handlers** | `server/handler/` | Endpoints and event subscriptions the manifest declares | Domain services |

The three backend modules are separate Maven artifacts, and the manifest lists each one under `modules` with its
`moduleKind`. The [Backend Development Guide](./backend-development.md#project-structure) describes what each
artifact may contain.

## Part 2: Backend Development

The backend has no Spring beans, controllers or component scan. The manifest declares what the plugin offers, the
plugin class binds a factory to each declared handler, and the runtime creates the handlers and calls them. The
[Backend Development Guide](./backend-development.md) covers each piece in detail.

### 2.1 Domain Service Pattern

```java title="core/service/ReservationService.java"
package com.example.reservation.core.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;

import com.example.reservation.core.model.Reservation;
import com.example.reservation.core.model.ReservationConfirmed;
import com.example.reservation.core.repository.ReservationRepository;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.IdGeneratorCapability;

/**
 * Domain service for reservation management.
 *
 * <p>A plain class: no Spring annotations and no transactions of its own. A handler factory creates it
 * with the repository and the capabilities it uses. Each call runs in the unit of work the runtime opened
 * for the handler, in the caller's tenant.</p>
 */
public final class ReservationService {

    private final ReservationRepository reservations;
    private final EventBusCapability events;
    private final IdGeneratorCapability ids;
    private final Clock clock;

    public ReservationService(
            ReservationRepository reservations,
            EventBusCapability events,
            IdGeneratorCapability ids,
            Clock clock) {
        this.reservations = Objects.requireNonNull(reservations, "reservations must not be null");
        this.events = Objects.requireNonNull(events, "events must not be null");
        this.ids = Objects.requireNonNull(ids, "ids must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Creates a pending reservation in the caller's tenant.
     */
    public Reservation create(long tenantId, String customerId, String roomId, LocalDate checkIn, LocalDate checkOut) {
        Reservation reservation = Reservation.pending(Long.parseLong(ids.generate()), tenantId, customerId, roomId,
            checkIn, checkOut, OffsetDateTime.now(clock));
        return reservations.save(reservation);
    }

    /**
     * Finds a reservation; the persistence adapter only sees the caller's tenant.
     */
    public Optional<Reservation> find(long reservationId) {
        return reservations.findById(reservationId);
    }

    /**
     * Confirms a pending reservation and tells other plugins, in the same transaction.
     */
    public Optional<Reservation> confirm(long reservationId) {
        Optional<Reservation> found = reservations.findById(reservationId);
        found.ifPresent(reservation -> {
            reservation.confirm();
            events.publishIntegration(new ReservationConfirmed(reservation));
        });
        return found;
    }

    /**
     * Records the payment of a reservation.
     */
    public void markPaid(long reservationId, String paymentId) {
        reservations.findById(reservationId).ifPresent(reservation -> reservation.markPaid(paymentId));
    }
}
```

- `ReservationRepository` is a Spring Data interface in `core/repository/`. The service gets it from its factory;
  it never comes from Spring injection.
- `Reservation` is an entity with an id from `IdGeneratorCapability` and a `tenant_id` column; see
  [Persistence](./backend-development.md#persistence).
- `ReservationConfirmed` extends `IntegrationEvent`, and the manifest declares it under `events.publishes`. It goes
  through the plugin's outbox in the same transaction as the confirmation; see
  [Publishing an event](./events-and-commands.md#publishing-an-event).

### 2.2 Event Handler Pattern

A subscription is declared in the manifest:

```yaml title="server/resources/META-INF/brix/plugin-manifest.yaml (excerpt)"
events:
  subscribes:
    - subscriptionId: payment-completed
      eventType: PaymentCompleted
      schemaRange: ">=1.0.0 <2.0.0"
      handlerId: reservation.payment-completed.v1
      scopePolicyRef: reservation-payment-tenant
      publisherPolicyRef: payment-owner        # the plugin that declares PaymentCompleted
      timeoutPolicyRef: event-standard
      concurrencyPolicyRef: partition-serial
      retryPolicyRef: event-standard
      idempotencyPolicyRef: persistent-inbox
data:
  storageId: reservation
  migrationLocation: db/migration/reservation
  inbox: reservation_inbox                   # the platform creates it
```

The handler is a class that names its payload type:

```java title="server/handler/PaymentCompletedHandler.java"
package com.example.reservation.server.handler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import com.example.reservation.core.service.ReservationService;
import io.runtime.sdk.plugin.EventHandler;
import io.runtime.sdk.plugin.EventInvocation;

/**
 * Handles PaymentCompleted events from the payment plugin.
 *
 * <p>The runtime calls it in this plugin's unit of work, in the event's tenant, and records the event in
 * the plugin's inbox in the same transaction. A redelivered event never reaches it again, so it needs no
 * idempotency check of its own. When it throws, its writes and the inbox record roll back and the runtime
 * retries the event.</p>
 */
public final class PaymentCompletedHandler implements EventHandler<PaymentCompletedHandler.PaymentCompleted> {

    private final ReservationService reservations;

    public PaymentCompletedHandler(ReservationService reservations) {
        this.reservations = reservations;
    }

    @Override
    public CompletionStage<Void> handle(EventInvocation<PaymentCompleted> invocation) {
        PaymentCompleted payment = invocation.event().payload();
        reservations.markPaid(Long.parseLong(payment.referenceId()), payment.paymentId());
        return CompletableFuture.completedFuture(null);
    }

    /**
     * PaymentCompleted as this plugin reads it: the fields of the payment plugin's schema. The plugin keeps
     * its own record instead of depending on the payment plugin's classes.
     */
    public record PaymentCompleted(long tenantId, String paymentId, String referenceId) {
    }
}
```

The runtime decodes each event into the handler's type argument. Do the work synchronously and return a completed
stage. [Handling an event](./events-and-commands.md#handling-an-event) explains the policies, retries and parking.

### 2.3 Endpoint Handlers and Their Factories

HTTP endpoints are declared in the manifest. The runtime's dispatcher authenticates the caller, checks the
permission, binds the tenant and opens the unit of work before it calls the handler:

```yaml title="server/resources/META-INF/brix/plugin-manifest.yaml (excerpt)"
endpoints:
  provides:
    - endpointId: reservation.create.v1
      transport: http
      method: POST
      path: /api/v1/reservations
      request:
        schemaRef: brix://schemas/reservation/create-reservation-request/1.0.0
      response:
        schemaRef: brix://schemas/reservation/reservation/1.0.0
      handlerId: reservation.create.v1
      authentication:
        mode: required
        policyRef: tenant-actor
      authorization:
        permissions:
          - reservation:create
        decision: all
        resourcePolicyRef: reservation-create
      tenantContext:
        mode: required
        source: runtime
      timeout:
        policyRef: endpoint-default
      idempotency:
        mode: not-applicable
```

The other two endpoints are declared with the same fields:

| `endpointId` and `handlerId` | Method | Path | Permission |
|---|---|---|---|
| `reservation.get.v1` | `GET` | `/api/v1/reservations/{id}` | `reservation:read` |
| `reservation.confirm.v1` | `POST` | `/api/v1/reservations/{id}/confirm` | `reservation:confirm` |

The list and cancel calls that the frontend in Part 3 makes are declared and bound the same way.

Each endpoint handler implements `EndpointHandler<EndpointInvocation<Object>, R>`. The handler reads the JSON body
(a `Map`), the path variables and the verified tenant from the invocation, and calls the domain service:

```java title="server/handler/CreateReservationHandler.java"
package com.example.reservation.server.handler;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;

import com.example.reservation.core.model.Reservation;
import com.example.reservation.core.service.ReservationService;
import io.runtime.sdk.plugin.EndpointHandler;
import io.runtime.sdk.plugin.EndpointHandlingException;
import io.runtime.sdk.plugin.EndpointInvocation;
import io.runtime.sdk.plugin.EndpointResponse;

/**
 * POST /api/v1/reservations: creates a pending reservation in the caller's tenant.
 */
public final class CreateReservationHandler
        implements EndpointHandler<EndpointInvocation<Object>, EndpointResponse> {

    static final String INVALID = "RESERVATION_INVALID";

    private final ReservationService reservations;

    public CreateReservationHandler(ReservationService reservations) {
        this.reservations = reservations;
    }

    @Override
    public EndpointResponse handle(EndpointInvocation<Object> request) {
        Map<?, ?> body = request.body() instanceof Map<?, ?> map ? map : Map.of();
        String customerId = text(body.get("customerId"));
        String roomId = text(body.get("roomId"));
        LocalDate checkIn = date(body.get("checkIn"));
        LocalDate checkOut = date(body.get("checkOut"));
        if (customerId == null || roomId == null || checkIn == null || checkOut == null
                || !checkOut.isAfter(checkIn)) {
            throw new EndpointHandlingException(400, INVALID,
                "A reservation needs a customer, a room and a check-out after the check-in");
        }
        long tenantId = Long.parseLong(request.tenantId().orElseThrow());
        Reservation reservation = reservations.create(tenantId, customerId, roomId, checkIn, checkOut);
        return EndpointResponse.created(ReservationMapper.toResponse(reservation));
    }

    private static String text(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private static LocalDate date(Object value) {
        try {
            return value instanceof String text ? LocalDate.parse(text) : null;
        } catch (DateTimeParseException notADate) {
            return null;
        }
    }
}
```

```java title="server/handler/ConfirmReservationHandler.java"
/**
 * POST /api/v1/reservations/{id}/confirm: confirms a pending reservation.
 */
public final class ConfirmReservationHandler
        implements EndpointHandler<EndpointInvocation<Object>, EndpointResponse> {

    private final ReservationService reservations;

    public ConfirmReservationHandler(ReservationService reservations) {
        this.reservations = reservations;
    }

    @Override
    public EndpointResponse handle(EndpointInvocation<Object> request) {
        reservations.confirm(ReservationIds.of(request)).orElseThrow(ReservationIds::notFound);
        return new EndpointResponse(204, null);
    }
}
```

```java title="server/handler/GetReservationHandler.java"
/**
 * GET /api/v1/reservations/{id}: runs in a read-only unit of work; the returned record becomes a 200 response.
 */
public final class GetReservationHandler
        implements EndpointHandler<EndpointInvocation<Object>, ReservationResponse> {

    private final ReservationService reservations;

    public GetReservationHandler(ReservationService reservations) {
        this.reservations = reservations;
    }

    @Override
    public ReservationResponse handle(EndpointInvocation<Object> request) {
        return reservations.find(ReservationIds.of(request))
            .map(ReservationMapper::toResponse)
            .orElseThrow(ReservationIds::notFound);
    }
}
```

```java title="server/handler/ReservationIds.java"
final class ReservationIds {

    /** The {id} path variable; a value that is not a number names no reservation. */
    static long of(EndpointInvocation<Object> request) {
        try {
            return Long.parseLong(request.pathVariables().get("id"));
        } catch (NumberFormatException notAnId) {
            throw notFound();
        }
    }

    static EndpointHandlingException notFound() {
        return new EndpointHandlingException(404, "RESERVATION_NOT_FOUND", "No such reservation");
    }
}
```

`ReservationMapper` turns the entity into `ReservationResponse`, the record the response schema describes. A
handler fails with `EndpointHandlingException`: the client gets that status and a body with the `errorCode` and the
`message`.

The plugin class binds a factory to every handler ID the manifest declares. The runtime calls each factory with the
plugin's `PluginContext`, the only way to reach a capability:

```java title="server/ReservationPlugin.java"
package com.example.reservation.server;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;

import com.example.reservation.core.repository.ReservationRepository;
import com.example.reservation.core.service.ReservationService;
import com.example.reservation.server.handler.ConfirmReservationHandler;
import com.example.reservation.server.handler.CreateReservationHandler;
import com.example.reservation.server.handler.GetReservationHandler;
import com.example.reservation.server.handler.PaymentCompletedHandler;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.IdGeneratorCapability;
import io.runtime.sdk.capability.PersistenceCapability;
import io.runtime.sdk.plugin.BrixHealth;
import io.runtime.sdk.plugin.BrixPlugin;
import io.runtime.sdk.plugin.PluginBootstrapContext;
import io.runtime.sdk.plugin.PluginContext;

public final class ReservationPlugin implements BrixPlugin {

    static final String CREATE = "reservation.create.v1";
    static final String GET = "reservation.get.v1";
    static final String CONFIRM = "reservation.confirm.v1";
    static final String PAYMENT_COMPLETED = "reservation.payment-completed.v1";

    private final AtomicReference<BrixHealth> health = new AtomicReference<>(BrixHealth.down("not started"));

    @Override
    public void configure(PluginBootstrapContext bootstrap) {
        bootstrap.bindEndpointHandlerFactory(CREATE, context -> new CreateReservationHandler(service(context)));
        bootstrap.bindEndpointHandlerFactory(GET, context -> new GetReservationHandler(service(context)));
        bootstrap.bindEndpointHandlerFactory(CONFIRM, context -> new ConfirmReservationHandler(service(context)));
        bootstrap.bindEventHandlerFactory(PAYMENT_COMPLETED,
            context -> new PaymentCompletedHandler(service(context)));
    }

    /** The capabilities must be listed under capabilities.required in the manifest. */
    private static ReservationService service(PluginContext context) {
        return new ReservationService(
            context.require(PersistenceCapability.class).repository(ReservationRepository.class),
            context.require(EventBusCapability.class),
            context.require(IdGeneratorCapability.class),
            Clock.systemUTC());
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

`server/resources/META-INF/services/io.runtime.sdk.plugin.BrixPlugin` contains the single line
`com.example.reservation.server.ReservationPlugin`. Bind a factory for every handler ID the manifest declares: the
runtime refuses a binding to an undeclared ID, does not start a plugin whose event subscription has no factory, and
never publishes an endpoint without one.

## Part 3: Frontend Development

### 3.1 Repository Pattern

```typescript title="repositories/ReservationRepository.ts"
import { HttpCapability } from '@brix/runtime-sdk-api-web';
import { 
  Reservation, 
  CreateReservationRequest,
  ReservationListResponse 
} from '../../shared/types';

/**
 * Repository for Reservation API operations.
 * 
 * Uses HttpCapability from runtime instead of fetch/axios.
 * This ensures infrastructure agnosticism per v3.0.7 constraint #7.
 */
export class ReservationRepository {
  
  constructor(private readonly http: HttpCapability) {}
  
  /**
   * Fetches all reservations with pagination.
   */
  async getAll(page = 0, size = 20): Promise<ReservationListResponse> {
    return this.http.get<ReservationListResponse>(
      `/api/v1/reservations?page=${page}&size=${size}`
    );
  }
  
  /**
   * Fetches a single reservation by ID.
   */
  async getById(id: string): Promise<Reservation> {
    return this.http.get<Reservation>(`/api/v1/reservations/${id}`);
  }
  
  /**
   * Creates a new reservation.
   */
  async create(request: CreateReservationRequest): Promise<Reservation> {
    return this.http.post<Reservation>('/api/v1/reservations', request);
  }
  
  /**
   * Confirms a pending reservation.
   */
  async confirm(id: string): Promise<void> {
    return this.http.post(`/api/v1/reservations/${id}/confirm`);
  }
  
  /**
   * Cancels a reservation.
   */
  async cancel(id: string, reason: string): Promise<void> {
    return this.http.post(`/api/v1/reservations/${id}/cancel`, { reason });
  }
}
```

### 3.2 ViewModel Hook Pattern

```typescript title="hooks/useReservations.ts"
import { useState, useEffect, useCallback, useMemo } from 'react';
import { useCapability } from '@brix/runtime-sdk-api-web';
import { HttpCapability } from '@brix/runtime-sdk-api-web';
import { ReservationRepository } from '../repositories/ReservationRepository';
import { Reservation, CreateReservationRequest } from '../../shared/types';

/**
 * ViewModel hook for reservation management.
 * 
 * Follows View -> ViewModel -> Repository pattern per v3.0.7 constraint #7.
 * The View layer uses this hook for all data and business operations.
 */
export function useReservations() {
  // Get capability from runtime
  const http = useCapability(HttpCapability);
  
  // Memoize repository instance
  const repository = useMemo(
    () => new ReservationRepository(http),
    [http]
  );
  
  // State
  const [reservations, setReservations] = useState<Reservation[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<Error | null>(null);
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(true);
  
  /**
   * Fetches reservations from API.
   */
  const fetchReservations = useCallback(async (pageNum = 0) => {
    setLoading(true);
    setError(null);
    
    try {
      const response = await repository.getAll(pageNum);
      
      if (pageNum === 0) {
        setReservations(response.content);
      } else {
        setReservations(prev => [...prev, ...response.content]);
      }
      
      setHasMore(!response.last);
      setPage(pageNum);
    } catch (e) {
      setError(e as Error);
    } finally {
      setLoading(false);
    }
  }, [repository]);
  
  /**
   * Creates a new reservation.
   */
  const createReservation = useCallback(async (
    request: CreateReservationRequest
  ): Promise<Reservation> => {
    const newReservation = await repository.create(request);
    setReservations(prev => [newReservation, ...prev]);
    return newReservation;
  }, [repository]);
  
  /**
   * Confirms a reservation.
   */
  const confirmReservation = useCallback(async (id: string) => {
    await repository.confirm(id);
    setReservations(prev =>
      prev.map(r => r.id === id ? { ...r, status: 'CONFIRMED' } : r)
    );
  }, [repository]);
  
  /**
   * Loads next page.
   */
  const loadMore = useCallback(() => {
    if (!loading && hasMore) {
      fetchReservations(page + 1);
    }
  }, [loading, hasMore, page, fetchReservations]);
  
  // Initial fetch
  useEffect(() => {
    fetchReservations(0);
  }, [fetchReservations]);
  
  return {
    reservations,
    loading,
    error,
    hasMore,
    createReservation,
    confirmReservation,
    loadMore,
    refresh: () => fetchReservations(0),
  };
}
```

### 3.3 View Component Pattern

```tsx title="pages/ReservationList.tsx"
import React, { useState } from 'react';
import { useReservations } from '../hooks/useReservations';
import { ReservationCard } from '../components/ReservationCard';
import { CreateReservationModal } from '../components/CreateReservationModal';
import { LoadingSpinner } from '@brix/shared-runtime-web/ui';

/**
 * Reservation list page.
 * 
 * This is a pure View component - it delegates all data and logic
 * to the useReservations hook (ViewModel pattern).
 * 
 * NO direct API calls, NO business logic here.
 */
export function ReservationListPage() {
  const {
    reservations,
    loading,
    error,
    hasMore,
    createReservation,
    confirmReservation,
    loadMore,
    refresh,
  } = useReservations();
  
  const [showCreateModal, setShowCreateModal] = useState(false);
  
  if (error) {
    return (
      <div className="error-container">
        <h2>Error loading reservations</h2>
        <p>{error.message}</p>
        <button onClick={refresh}>Retry</button>
      </div>
    );
  }
  
  return (
    <div className="reservation-list-page">
      <header className="page-header">
        <h1>Reservations</h1>
        <button 
          className="btn-primary"
          onClick={() => setShowCreateModal(true)}
        >
          New Reservation
        </button>
      </header>
      
      {loading && reservations.length === 0 ? (
        <LoadingSpinner />
      ) : (
        <>
          <div className="reservation-grid">
            {reservations.map(reservation => (
              <ReservationCard
                key={reservation.id}
                reservation={reservation}
                onConfirm={() => confirmReservation(reservation.id)}
              />
            ))}
          </div>
          
          {hasMore && (
            <button 
              className="load-more"
              onClick={loadMore}
              disabled={loading}
            >
              {loading ? 'Loading...' : 'Load More'}
            </button>
          )}
        </>
      )}
      
      <CreateReservationModal
        open={showCreateModal}
        onClose={() => setShowCreateModal(false)}
        onCreate={createReservation}
      />
    </div>
  );
}
```

## Part 4: Testing

### 4.1 Unit Testing Services

The domain service is a plain class, so a unit test creates it with mocks. No Spring context and no runtime are
involved:

```java title="test/service/ReservationServiceTest.java"
class ReservationServiceTest {

    private final ReservationRepository repository = mock(ReservationRepository.class);
    private final EventBusCapability events = mock(EventBusCapability.class);
    private final IdGeneratorCapability ids = mock(IdGeneratorCapability.class);
    private final ReservationService service = new ReservationService(repository, events, ids,
        Clock.fixed(Instant.parse("2026-01-05T09:00:00Z"), ZoneOffset.UTC));

    @Test
    void createsAPendingReservationWithAGeneratedId() {
        when(ids.generate()).thenReturn("1001");
        when(repository.save(any(Reservation.class))).thenAnswer(call -> call.getArgument(0));

        Reservation result = service.create(42L, "customer-1", "room-101",
            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 3));

        assertThat(result.getId()).isEqualTo(1001L);
        assertThat(result.getStatus()).isEqualTo(ReservationStatus.PENDING);
        verifyNoInteractions(events);
    }

    @Test
    void confirmingPublishesReservationConfirmed() {
        Reservation pending = Reservation.pending(1001L, 42L, "customer-1", "room-101",
            LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 3), OffsetDateTime.parse("2026-01-05T09:00:00Z"));
        when(repository.findById(1001L)).thenReturn(Optional.of(pending));

        service.confirm(1001L);

        assertThat(pending.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(events).publishIntegration(any(ReservationConfirmed.class));
    }
}
```

A test like this does not cover the tenant binding, the transaction or the outbox. For handler tests, the event
schema contract and end-to-end tests on PostgreSQL and Kafka, see
[Testing](./backend-development.md#testing) in the Backend Development Guide.

### 4.2 Architecture Tests

The Architecture Guard (`io.brix:architecture-guard`) ships its rules as ArchUnit rule sets. Add it and the
ArchUnit JUnit engine to the core module, at test scope:

```xml title="core/pom.xml (excerpt)"
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

Then add one test class. `BrixArchitectureRules.pluginProfile()` is the guard's rule set for plugin code; rules of
your own sit next to it. The test checks the core's classes and those of the api module; add the same class to the
server module to check the handlers and the plugin class too.

```java title="core/test/ArchitectureTest.java"
package com.example.reservation.core;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchTests;
import com.tngtech.archunit.lang.ArchRule;

import io.brix.architecture.guard.BrixArchitectureRules;

@AnalyzeClasses(packages = "com.example.reservation", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** The guard's rules for plugin code: no infrastructure clients, no Spring container, no broker SDKs. */
    @ArchTest
    static final ArchTests brixRules = BrixArchitectureRules.pluginProfile();

    /** A rule of this plugin's own: domain services see capabilities, the plugin's own types and the JDK. */
    @ArchTest
    static final ArchRule servicesDependOnCapabilitiesOnly = classes()
        .that().resideInAPackage("..core.service..")
        .should().onlyDependOnClassesThat()
        .resideInAnyPackage(
            "io.runtime.sdk..",
            "com.example.reservation.api..",
            "com.example.reservation.core..",
            "java..")
        .because("domain services reach the platform only through capabilities");
}
```

- `BrixArchitectureRules`, in `io.brix.architecture.guard`, is the entry point. The rule classes behind it, such as
  `AdapterIsolationRule`, are final and offer static rules only: use them, do not extend them.
- The plugin profile refuses, among others: Kafka, Redis and AMQP clients; direct HTTP clients; the Spring container
  API; Spring application events. In `..core..` packages it also refuses broker templates and SDKs, JWT and crypto
  libraries, and the runtime's implementation packages, its outbox among them.
  [Testing](./testing.md#standard-architecture-rules) lists the rules.
- Capabilities live in `io.runtime.sdk.*`, the packages of `runtime-sdk-api`.

`mvn test` runs the rules with the module's other tests. A class that breaks a rule fails the build, with the rule's
reason.

## Part 5: Plugin Manifest

The manifest is `META-INF/brix/plugin-manifest.yaml` in the server module. It is the only declaration of the plugin:
there is no `plugin-manifest.json`, no `module-manifest.yaml` and no Java configuration. It follows the structure of
the sample plugins in `packages/@brix/samples`:

```yaml title="server/resources/META-INF/brix/plugin-manifest.yaml"
apiVersion: brix.io/v1
kind: Plugin
metadata:
  pluginId: reservation
  name: reservation
  version: 1.0.0
  displayName: Reservation Management
  vendor: Example Inc.
  license: Apache-2.0
runtime:
  compiledAgainst: 3.2.0
  supportedRange: ">=3.2.0 <4.0.0"
modules:
  - groupId: com.example
    artifactId: reservation-api
    version: 1.0.0
    moduleKind: plugin-api
  - groupId: com.example
    artifactId: reservation-core
    version: 1.0.0
    moduleKind: plugin-core
  - groupId: com.example
    artifactId: reservation-server
    version: 1.0.0
    moduleKind: plugin-server
endpoints:
  provides:
    - endpointId: reservation.create.v1
      transport: http
      method: POST
      path: /api/v1/reservations
      request:
        schemaRef: brix://schemas/reservation/create-reservation-request/1.0.0
      response:
        schemaRef: brix://schemas/reservation/reservation/1.0.0
      handlerId: reservation.create.v1
      authentication:
        mode: required
        policyRef: tenant-actor
      authorization:
        permissions:
          - reservation:create
        decision: all
        resourcePolicyRef: reservation-create
      tenantContext:
        mode: required
        source: runtime
      timeout:
        policyRef: endpoint-default
      idempotency:
        mode: not-applicable
    # reservation.get.v1 and reservation.confirm.v1 are declared the same way (see 2.3)
capabilities:
  required:
    - id: io.runtime.sdk.capability.EventBusCapability
      version: ">=3.0.10 <4.0.0"
    - id: io.runtime.sdk.capability.PersistenceCapability
      version: ">=3.2.0 <4.0.0"
    - id: io.runtime.sdk.capability.IdGeneratorCapability
      version: ">=3.2.0 <4.0.0"
  # context.find(...) returns an empty Optional for an optional capability the host does not provide
  optional:
    - id: io.runtime.sdk.capability.NotificationCapability
      version: ">=3.2.0 <4.0.0"
queries:
  provides: []
  consumes: []
commands:
  provides: []
  consumes: []
events:
  publishes:
    - eventType: ReservationConfirmed
      schemaRef: brix://schemas/reservation/reservation-confirmed/1.0.0
      version: 1.0.0
      reliability: STANDARD
  subscribes:
    - subscriptionId: payment-completed
      eventType: PaymentCompleted
      schemaRange: ">=1.0.0 <2.0.0"
      handlerId: reservation.payment-completed.v1
      scopePolicyRef: reservation-payment-tenant
      publisherPolicyRef: payment-owner
      timeoutPolicyRef: event-standard
      concurrencyPolicyRef: partition-serial
      retryPolicyRef: event-standard
      idempotencyPolicyRef: persistent-inbox
tasks:
  provides: []
internalContracts:
  provides: []
data:
  storageId: reservation
  schema: reservation
  migrationLocation: db/migration/reservation
  outbox: reservation_outbox
  inbox: reservation_inbox
permissions:
  - id: reservation:create
    name: Create reservations
    description: Allows creating a reservation in the current tenant.
    grants:
      - {role: OWNER, scope: TENANT}
      - {role: ADMIN, scope: TENANT}
      - {role: MEMBER, scope: TENANT}
  - id: reservation:read
    name: Read reservations
    description: Allows reading the reservations of the current tenant.
    grants:
      - {role: OWNER, scope: TENANT}
      - {role: ADMIN, scope: TENANT}
      - {role: MEMBER, scope: TENANT}
  - id: reservation:confirm
    name: Confirm reservations
    description: Allows confirming a pending reservation in the current tenant.
    grants:
      - {role: OWNER, scope: TENANT}
      - {role: ADMIN, scope: TENANT}
config: []
ui:
  web:
    enabled: true
    manifestUrl: /plugins/reservation/ui-manifest.json
    runtimeRange: ">=3.0.10 <4.0.0"
```

- Every `handlerId` needs a factory bound in `ReservationPlugin.configure`, and every bound factory needs a
  `handlerId` here.
- `context.require(...)` only returns capabilities listed under `capabilities.required`.
- Each `schemaRef` names a JSON schema file in the plugin, at `META-INF/brix/schemas/reservation/<name>/<version>.json`.
- `data.outbox` and `data.inbox` must be `<storageId>_outbox` and `<storageId>_inbox`. The platform creates both
  tables; the plugin's migrations under `data.migrationLocation` create only its business tables.
- `permissions` lists each permission and the tenant roles that hold it. An endpoint can only require permissions
  declared here.
- `ui.web` tells the host that the plugin has a web UI and where its UI manifest is served.

The [Backend Development Guide](./backend-development.md#the-manifest) describes each section.

## Summary

| Step | Backend | Frontend |
|------|---------|----------|
| 1 | Define domain models | Define shared types |
| 2 | Create services using capabilities | Create repositories |
| 3 | Add event handlers | Create ViewModel hooks |
| 4 | Declare endpoints and write their handlers | Create View components |
| 5 | Write architecture tests | Write component tests |
| 6 | Create plugin manifest | Configure routes |

## Next Steps

- [Testing Guide](./testing) - Comprehensive testing strategies
- [Architecture Guard](./architecture-guard) - Understanding red-line rules
- [Deployment Guide](./deployment) - Deploy to production
