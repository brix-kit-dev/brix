---
id: runtime-sdk-api
title: Runtime SDK API
sidebar_label: runtime-sdk-api
---

# @brix/runtime-sdk-api-web

TypeScript API reference for the frontend Runtime SDK.

## Installation

```bash
pnpm add @brix/runtime-sdk-api-web
```

## Capabilities

### HttpCapability

HTTP client for API requests.

```typescript
interface HttpCapability extends Capability {
  get<T>(url: string, options?: HttpOptions): Promise<T>;
  post<T>(url: string, body?: unknown, options?: HttpOptions): Promise<T>;
  put<T>(url: string, body?: unknown, options?: HttpOptions): Promise<T>;
  delete(url: string, options?: HttpOptions): Promise<void>;
}
```

**Usage:**
```typescript
import { useCapability, HttpCapability } from '@brix/runtime-sdk-api-web';

function MyComponent() {
  const http = useCapability(HttpCapability);
  
  async function loadData() {
    const data = await http.get<MyData[]>('/api/data');
    return data;
  }
}
```

### EventBusCapability

Event subscription and publishing.

```typescript
interface EventBusCapability extends Capability {
  publish(event: DomainEvent | IntegrationEvent): Promise<void>;
  subscribe<T>(topic: string, handler: (event: T) => void): Subscription;
  unsubscribe(subscription: Subscription): void;
}
```

**Usage:**
```typescript
import { useCapability, EventBusCapability } from '@brix/runtime-sdk-api-web';
import { useEffect } from '@brix/shared-runtime-web';

function OrderDashboard() {
  const eventBus = useCapability(EventBusCapability);
  
  useEffect(() => {
    const sub = eventBus.subscribe('order.created', (event) => {
      console.log('New order:', event);
    });
    
    return () => eventBus.unsubscribe(sub);
  }, [eventBus]);
}
```

### StorageCapability

Browser local/session storage.

```typescript
interface StorageCapability extends Capability {
  get<T>(key: string): T | null;
  set<T>(key: string, value: T): void;
  remove(key: string): void;
  clear(): void;
}
```

## Hooks

### useCapability

Retrieves a capability instance.

```typescript
function useCapability<T extends Capability>(type: CapabilityType<T>): T;
```

### useHttp

Shorthand for HttpCapability.

```typescript
function useHttp(): HttpCapability;
```

### useEventBus

Shorthand for EventBusCapability.

```typescript
function useEventBus(): EventBusCapability;
```

## Types

### Capability

Base interface for all capabilities.

```typescript
interface Capability {
  readonly name: string;
}
```

### DomainEvent

Events within a plugin.

```typescript
interface DomainEvent {
  readonly type: string;
  readonly timestamp: Date;
  readonly payload: unknown;
}
```

### IntegrationEvent

Events across plugins.

```typescript
interface IntegrationEvent {
  readonly eventId: string;
  readonly timestamp: string;
}
```

---

*Full API documentation is generated from TypeScript source using TypeDoc.*
