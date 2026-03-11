---
id: changelog
title: Changelog
sidebar_label: Changelog
sidebar_position: 2
---

# Changelog

All notable changes to Brix Framework are documented here.

## [3.0.0] - 2024-01-15

### 🚀 Major Release - Runtime Shell Architecture

This release introduces the **Runtime Shell Architecture**, a fundamental shift in how plugins interact with infrastructure.

### ✨ Added

- **Capability Contracts** - All infrastructure accessed through interfaces
  - `DataAccessCapability` - Database operations
  - `CacheCapability` - Caching operations
  - `EventBusCapability` - Event publishing/subscribing
  - `HttpCapability` - HTTP client
  - `FileStorageCapability` - Object storage
  - `LockCapability` - Distributed locks
  - `MessageQueueCapability` - Message queues
  - `SchedulerCapability` - Job scheduling

- **Architecture Guard** - ArchUnit-based enforcement of 13 red-line rules
  - Adapter isolation rule
  - No cross-plugin dependencies rule
  - Ultra-thin host rule
  - And 10 more...

- **Shared Runtime** - Single React instance across all plugins
  - `@brix/shared-runtime-web` package
  - Prevents multiple React instances error

- **Event-Driven Communication** - Cross-plugin events
  - Domain events (within plugin)
  - Integration events (across plugins)
  - Idempotent event handlers

- **Plugin Archetype** - Maven archetype for new plugins
  ```bash
  mvn archetype:generate -DarchetypeGroupId=io.brix ...
  ```

### 🔄 Changed

- **Java 17 required** (was Java 11)
- **Spring Boot 3.2** (was 2.7)
- Package structure: `io.brix.runtime.sdk.api.*`
- Frontend imports from shared-runtime

### 🗑️ Removed

- Direct JPA access in plugins
- Direct Kafka/Redis access in plugins
- `@Repository` annotation in plugin code
- `@KafkaListener` annotation (use `@EventHandler`)

### 📦 Migration

See [Migration Guide](./from-v2) for detailed instructions.

---

## [2.7.0] - 2023-10-01

### ✨ Added

- Spring Boot 2.7 support
- Improved plugin discovery
- Better error messages

### 🐛 Fixed

- Memory leak in event processing
- Connection pool exhaustion under load

---

## [2.6.0] - 2023-07-15

### ✨ Added

- Multi-tenant support
- Async event processing
- Health check endpoints

### 🔄 Changed

- Upgraded Kafka client to 3.4
- Improved startup time by 30%

---

## [2.5.0] - 2023-04-01

### ✨ Added

- Plugin hot-reload in development
- OpenAPI 3.0 documentation generation
- Metrics export to Prometheus

### 🐛 Fixed

- Race condition in cache invalidation
- Timezone handling in date serialization

---

## [2.4.0] - 2023-01-15

### ✨ Added

- GraphQL support
- Batch processing utilities
- Rate limiting

### 🔄 Changed

- PostgreSQL 15 support
- Redis 7 support

---

## [2.3.0] - 2022-10-01

### ✨ Added

- Plugin dependency management
- Circular dependency detection
- Plugin versioning

### 🐛 Fixed

- Transaction rollback on event failure
- Memory usage optimization

---

## [2.2.0] - 2022-07-01

### ✨ Added

- Event sourcing support
- Saga pattern helper
- Outbox pattern implementation

---

## [2.1.0] - 2022-04-01

### ✨ Added

- Distributed tracing
- Log correlation
- Performance profiling

---

## [2.0.0] - 2022-01-01

### 🚀 Major Release

- Initial public release
- Modular plugin architecture
- Event-driven communication
- Multi-database support

---

## Version Policy

Brix follows [Semantic Versioning](https://semver.org/):

- **Major** (x.0.0): Breaking changes, architecture shifts
- **Minor** (0.x.0): New features, backward compatible
- **Patch** (0.0.x): Bug fixes, security patches

## Support Policy

| Version | Status | Support Until |
|---------|--------|---------------|
| 3.0.x   | Active | Current       |
| 2.7.x   | LTS    | Jan 2025      |
| 2.6.x   | EOL    | Jul 2024      |
| < 2.6   | EOL    | -             |

## Upgrade Recommendations

- **2.x → 3.x**: Follow [migration guide](./from-v2) carefully
- **2.6 → 2.7**: Direct upgrade, no breaking changes
- **< 2.6**: Upgrade to 2.7 first, then to 3.x
