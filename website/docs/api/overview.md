---
id: overview
title: API Reference Overview
sidebar_label: Overview
sidebar_position: 1
---

# API Reference Overview

This section contains the complete API reference documentation for the Brix Framework.

## API Documentation Types

### TypeScript API (Frontend)

Generated from TypeScript source using [TypeDoc](https://typedoc.org/). Covers:

- **@brix/runtime-sdk-api-web** - Frontend capability contracts
- **@brix/shared-runtime-web** - Shared React utilities and exports
- **@brix/platform-commons-web** - Common types and utilities

[View TypeScript API →](./typescript/)

### Java API (Backend)

Generated from Java source using [Javadoc](https://docs.oracle.com/javase/8/docs/technotes/tools/windows/javadoc.html). Covers:

- **io.brix.runtime.sdk.api** - Backend capability contracts
- **io.brix.platform.commons** - Common types and utilities
- **io.brix.infra.adapters** - Infrastructure adapters

[View Java API →](./java/)

---

## Core Capability Contracts

### Data Access

```java
// Java
public interface DataAccessCapability extends Capability {
    <T, ID> T findById(Class<T> type, ID id);
    <T> List<T> findAll(Class<T> type);
    <T> List<T> findBy(Class<T> type, Map<String, Object> criteria);
    <T> T save(T entity);
    <T> void delete(T entity);
}
```

```typescript
// TypeScript
interface DataAccessCapability extends Capability {
    findById<T>(type: string, id: string): Promise<T | null>;
    findAll<T>(type: string): Promise<T[]>;
    findBy<T>(type: string, criteria: Record<string, unknown>): Promise<T[]>;
    save<T>(entity: T): Promise<T>;
    delete<T>(entity: T): Promise<void>;
}
```

### Cache

```java
// Java
public interface CacheCapability extends Capability {
    <T> Optional<T> get(String key, Class<T> type);
    void set(String key, Object value, Duration ttl);
    void delete(String key);
    boolean exists(String key);
}
```

```typescript
// TypeScript
interface CacheCapability extends Capability {
    get<T>(key: string): Promise<T | null>;
    set<T>(key: string, value: T, ttlSeconds?: number): Promise<void>;
    delete(key: string): Promise<void>;
    exists(key: string): Promise<boolean>;
}
```

### Event Bus

```java
// Java
public interface EventBusCapability extends Capability {
    void publish(DomainEvent event);
    void publish(IntegrationEvent event);
    <T> void subscribe(String topic, Class<T> eventType, Consumer<T> handler);
}
```

```typescript
// TypeScript
interface EventBusCapability extends Capability {
    publish(event: DomainEvent | IntegrationEvent): Promise<void>;
    subscribe<T>(topic: string, handler: (event: T) => void): Subscription;
    unsubscribe(subscription: Subscription): void;
}
```

### HTTP Client

```java
// Java
public interface HttpCapability extends Capability {
    <T> T get(String url, Class<T> responseType);
    <T> T post(String url, Object body, Class<T> responseType);
    <T> T put(String url, Object body, Class<T> responseType);
    void delete(String url);
}
```

```typescript
// TypeScript
interface HttpCapability extends Capability {
    get<T>(url: string, options?: HttpOptions): Promise<T>;
    post<T>(url: string, body?: unknown, options?: HttpOptions): Promise<T>;
    put<T>(url: string, body?: unknown, options?: HttpOptions): Promise<T>;
    delete(url: string, options?: HttpOptions): Promise<void>;
}
```

### File Storage

```java
// Java
public interface FileStorageCapability extends Capability {
    String upload(String path, InputStream content, String contentType);
    InputStream download(String path);
    void delete(String path);
    String getUrl(String path, Duration expiration);
}
```

### Message Queue

```java
// Java
public interface MessageQueueCapability extends Capability {
    void send(String queue, Message message);
    void sendDelayed(String queue, Message message, Duration delay);
    void receive(String queue, Consumer<Message> handler);
}
```

### Lock

```java
// Java
public interface LockCapability extends Capability {
    Lock acquire(String key, Duration timeout);
    void release(Lock lock);
    boolean tryAcquire(String key);
}
```

### Scheduler

```java
// Java
public interface SchedulerCapability extends Capability {
    ScheduledTask schedule(String cronExpression, Runnable task);
    ScheduledTask scheduleOnce(Duration delay, Runnable task);
    void cancel(ScheduledTask task);
}
```

---

## Frontend React Hooks

All capabilities are accessed via the `useCapability` hook:

```typescript
import { useCapability, HttpCapability } from '@brix/runtime-sdk-api-web';

function MyComponent() {
    const http = useCapability(HttpCapability);
    // Use http...
}
```

### Common Hooks

| Hook | Purpose |
|------|---------|
| `useCapability(T)` | Access a capability by type |
| `useEventBus()` | Subscribe to events |
| `useHttp()` | Shorthand for HttpCapability |
| `useStorage()` | Local/session storage capability |

---

## Package Structure

```
io.brix.runtime.sdk
├── api/                        # Capability contracts
│   ├── Capability.java         # Base marker interface
│   ├── DataAccessCapability.java
│   ├── CacheCapability.java
│   ├── EventBusCapability.java
│   ├── HttpCapability.java
│   ├── FileStorageCapability.java
│   ├── MessageQueueCapability.java
│   ├── LockCapability.java
│   └── SchedulerCapability.java
├── event/                      # Event types
│   ├── DomainEvent.java
│   └── IntegrationEvent.java
└── exception/                  # Standard exceptions
    ├── CapabilityException.java
    └── CapabilityNotFoundException.java
```

---

## Version Compatibility

| Brix Version | Java | Spring Boot | TypeScript | React |
|--------------|------|-------------|------------|-------|
| 3.0.x        | 17+  | 3.2+        | 5.0+       | 18+   |
| 2.x          | 11+  | 2.7         | 4.9+       | 17+   |

---

## External Resources

- [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/)
- [React Documentation](https://react.dev/)
- [TypeScript Handbook](https://www.typescriptlang.org/docs/handbook/)
- [ArchUnit Documentation](https://www.archunit.org/userguide/html/000_Index.html)
