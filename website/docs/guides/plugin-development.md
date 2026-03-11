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
│   ├── core/                       # Domain layer (Maven module)
│   │   ├── service/                # Domain services
│   │   ├── model/                  # Entities, value objects
│   │   ├── repository/             # Repository interfaces
│   │   └── event/                  # Event handlers
│   ├── server/                     # REST layer (Maven module)
│   │   └── controller/             # REST controllers
│   └── infrastructure/             # JPA repositories (optional)
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
| **Domain** | `core/service/` | Business rules | Capabilities |
| **REST** | `server/controller/` | HTTP endpoints | Domain services |

## Part 2: Backend Development

### 2.1 Domain Service Pattern

```java title="core/service/ReservationService.java"
package com.example.reservation.core.service;

import io.brix.runtime.sdk.api.EventBusCapability;
import io.brix.runtime.sdk.api.StateStoreCapability;
import com.example.reservation.core.model.Reservation;
import com.example.reservation.core.model.ReservationStatus;
import com.example.reservation.shared.events.ReservationEvents;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Domain service for reservation management.
 * 
 * <p>This service demonstrates capability-first development:
 * <ul>
 *   <li>Uses EventBusCapability for event publishing</li>
 *   <li>Uses StateStoreCapability for caching</li>
 *   <li>No direct infrastructure imports (Kafka, Redis, etc.)</li>
 * </ul>
 * 
 * @see io.brix.runtime.sdk.api.EventBusCapability
 */
@Service
@Transactional
public class ReservationService {
    
    private final ReservationRepository repository;
    private final EventBusCapability eventBus;
    private final StateStoreCapability stateStore;
    
    /**
     * Constructor injection of capabilities.
     * Capabilities are provided by the Runtime Shell at startup.
     */
    public ReservationService(
            ReservationRepository repository,
            EventBusCapability eventBus,
            StateStoreCapability stateStore) {
        this.repository = repository;
        this.eventBus = eventBus;
        this.stateStore = stateStore;
    }
    
    /**
     * Creates a new reservation.
     *
     * @param request the creation request
     * @return the created reservation
     */
    public Reservation createReservation(CreateReservationRequest request) {
        // Validate business rules
        validateReservationRequest(request);
        
        // Create domain entity
        Reservation reservation = Reservation.builder()
            .id(generateId())
            .customerId(request.getCustomerId())
            .roomId(request.getRoomId())
            .checkIn(request.getCheckIn())
            .checkOut(request.getCheckOut())
            .status(ReservationStatus.PENDING)
            .build();
        
        // Persist
        repository.save(reservation);
        
        // Publish domain event
        eventBus.publish(ReservationEvents.CREATED, 
            new ReservationCreatedEvent(reservation));
        
        // Cache for quick access
        stateStore.put(
            "reservation:" + reservation.getId(), 
            reservation,
            Duration.ofHours(24)
        );
        
        return reservation;
    }
    
    /**
     * Confirms a pending reservation.
     * Publishes integration event for other plugins.
     */
    public void confirmReservation(String reservationId) {
        Reservation reservation = getReservation(reservationId);
        
        reservation.confirm();
        repository.save(reservation);
        
        // Update cache
        stateStore.put("reservation:" + reservationId, reservation);
        
        // Publish integration event for other plugins
        eventBus.publishIntegration(
            ReservationEvents.CONFIRMED,
            new ReservationConfirmedIntegrationEvent(
                reservationId,
                reservation.getRoomId(),
                reservation.getCheckIn().toString(),
                reservation.getCheckOut().toString()
            )
        );
    }
}
```

### 2.2 Event Handler Pattern

```java title="core/event/PaymentEventHandler.java"
package com.example.reservation.core.event;

import io.brix.runtime.sdk.api.annotation.EventHandler;
import com.example.reservation.core.service.ReservationService;
import org.springframework.stereotype.Component;

/**
 * Handles payment events from Payment plugin.
 * 
 * <p>Event handlers receive Integration Events from other plugins.
 * They must be idempotent as events may be delivered multiple times.
 */
@Component
public class PaymentEventHandler {
    
    private final ReservationService reservationService;
    
    public PaymentEventHandler(ReservationService reservationService) {
        this.reservationService = reservationService;
    }
    
    /**
     * Handles payment completion for reservations.
     * 
     * <p>This handler is idempotent - calling it multiple times
     * with the same payment ID has no additional effect.
     *
     * @param event the payment completed event
     */
    @EventHandler("payment.completed")
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        String reservationId = event.getReferenceId();
        
        // Idempotency check
        if (reservationService.isAlreadyPaid(reservationId)) {
            log.info("Reservation {} already marked as paid", reservationId);
            return;
        }
        
        reservationService.markAsPaid(reservationId, event.getPaymentId());
    }
}
```

### 2.3 REST Controller Pattern

```java title="server/controller/ReservationController.java"
package com.example.reservation.server.controller;

import com.example.reservation.core.service.ReservationService;
import com.example.reservation.shared.types.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * REST API for reservation management.
 * 
 * <p>This controller lives in the {@code server} module, which depends on
 * {@code core}. It only delegates to domain services.
 */
@RestController
@RequestMapping("/api/v1/reservations")
@Tag(name = "Reservations", description = "Reservation management API")
public class ReservationController {
    
    private final ReservationService reservationService;
    
    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }
    
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new reservation")
    public ReservationResponse createReservation(
            @RequestBody @Valid CreateReservationRequest request) {
        Reservation reservation = reservationService.createReservation(request);
        return ReservationMapper.toResponse(reservation);
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get reservation by ID")
    public ReservationResponse getReservation(@PathVariable String id) {
        Reservation reservation = reservationService.getReservation(id);
        return ReservationMapper.toResponse(reservation);
    }
    
    @PostMapping("/{id}/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Confirm a pending reservation")
    public void confirmReservation(@PathVariable String id) {
        reservationService.confirmReservation(id);
    }
}
```

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

```java title="test/service/ReservationServiceTest.java"
@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {
    
    @Mock
    private ReservationRepository repository;
    
    @Mock
    private EventBusCapability eventBus;
    
    @Mock
    private StateStoreCapability stateStore;
    
    @InjectMocks
    private ReservationService service;
    
    @Test
    void shouldPublishEventOnCreate() {
        // Given
        CreateReservationRequest request = CreateReservationRequest.builder()
            .customerId("customer-1")
            .roomId("room-101")
            .checkIn(LocalDate.now().plusDays(1))
            .checkOut(LocalDate.now().plusDays(3))
            .build();
        
        // When
        Reservation result = service.createReservation(request);
        
        // Then
        assertThat(result.getStatus()).isEqualTo(ReservationStatus.PENDING);
        
        verify(eventBus).publish(
            eq(ReservationEvents.CREATED),
            any(ReservationCreatedEvent.class)
        );
        
        verify(stateStore).put(
            eq("reservation:" + result.getId()),
            eq(result),
            any(Duration.class)
        );
    }
}
```

### 4.2 Architecture Tests

```java title="test/ArchitectureTest.java"
class ArchitectureTest extends AdapterIsolationRule {
    
    private static final String BASE_PACKAGE = "com.example.reservation";
    
    @ArchTest
    static final ArchRule noInfrastructureInCore = noClasses()
        .that().resideInAPackage("..core..")
        .should().dependOnClassesThat()
        .resideInAnyPackage(
            "org.apache.kafka..",
            "redis.clients..",
            "org.springframework.data.redis..",
            "org.springframework.kafka.."
        )
        .because("Core must use capabilities, not infrastructure directly");
    
    @ArchTest
    static final ArchRule servicesDependOnCapabilitiesOnly = classes()
        .that().resideInAPackage("..core.service..")
        .should().onlyDependOnClassesThat()
        .resideInAnyPackage(
            "io.brix.runtime.sdk.api..",
            "com.example.reservation.core..",
            "com.example.reservation.shared..",
            "java..",
            "org.springframework.stereotype..",
            "org.springframework.transaction.."
        );
}
```

## Part 5: Plugin Manifest

```json title="META-INF/plugin-manifest.json"
{
  "plugin": {
    "id": "reservation-plugin",
    "groupId": "com.example",
    "artifactId": "reservation-plugin",
    "version": "1.0.0",
    "name": "Reservation Management",
    "description": "Room reservation and booking management"
  },
  "capabilities": {
    "required": [
      "EventBusCapability",
      "StateStoreCapability",
      "DataAccessCapability",
      "AuthContextCapability"
    ],
    "optional": [
      "NotificationCapability"
    ]
  },
  "events": {
    "publishes": [
      "reservation.created",
      "reservation.confirmed",
      "reservation.cancelled"
    ],
    "subscribes": [
      "payment.completed",
      "user.registered"
    ]
  },
  "permissions": [
    "reservation.create",
    "reservation.view",
    "reservation.confirm",
    "reservation.cancel",
    "reservation.admin"
  ],
  "frontend": {
    "web": {
      "entry": "frontend/web/plugin.ts",
      "routes": [
        "/reservations",
        "/reservations/:id"
      ]
    }
  }
}
```

## Summary

| Step | Backend | Frontend |
|------|---------|----------|
| 1 | Define domain models | Define shared types |
| 2 | Create services using capabilities | Create repositories |
| 3 | Add event handlers | Create ViewModel hooks |
| 4 | Create REST controllers | Create View components |
| 5 | Write architecture tests | Write component tests |
| 6 | Create plugin manifest | Configure routes |

## Next Steps

- [Testing Guide](./testing) - Comprehensive testing strategies
- [Architecture Guard](./architecture-guard) - Understanding red-line rules
- [Deployment Guide](./deployment) - Deploy to production
