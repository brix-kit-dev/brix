# Brix Infrastructure Adapters

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-17+-green.svg)](https://openjdk.org/)

Infrastructure Adapters is the Layer 2C implementation repository in the Brix Runtime Shell architecture.

---

## Architecture Position

This repository provides concrete infrastructure implementations for Runtime Capability Contracts defined in the Runtime SDK. It encapsulates infrastructure details such as Kafka, Redis, HTTP, object storage, telemetry, and platform-specific runtime integration.

```text
Layer 3: Host Assembly Layer                 <- assembles implementations from this repository
    ↓
Layer 2C: Capability Implementation Layer    <- this repository
    ↓ implements
Layer 2A: Capability Contract Layer          <- runtime-sdk API packages
    ↑ only dependency allowed for plugins
Layer 1: Plugin Layer                        <- must never depend on this repository
```

### Architectural Guardrails

| Rule | Description |
|------|-------------|
| Plugins must not depend on infra-adapters | Plugins may depend only on Runtime Capability Contracts |
| Infra adapters must not depend on business modules | Infrastructure code stays isolated from solution logic |
| Infra adapters must not define new contracts | Contracts belong to Layer 2A |
| Infra adapters may depend on infrastructure SDKs | Kafka, Redis, OpenTelemetry, cloud SDKs, and similar libraries are allowed here |
| Hosts assemble adapters through configuration | Host projects remain ultra-thin and contain no implementation logic |

---

## Package Layout

```text
packages/
├── server/
│   ├── infra-adapter-dataaccess/
│   ├── infra-adapter-database/
│   ├── infra-adapter-fallback/
│   ├── infra-adapter-idgen/
│   ├── infra-adapter-kafka/
│   ├── infra-adapter-minio/
│   ├── infra-adapter-otel/
│   ├── infra-adapter-outbox/
│   ├── infra-adapter-redis/
│   ├── infra-adapter-simple/
│   └── infra-adapter-webhook/
├── web/
│   ├── infra-adapter-http-web/
│   ├── infra-adapter-iframe-web/
│   ├── infra-adapter-mf-web/
│   ├── infra-adapter-native-web/
│   ├── infra-adapter-router-web/
│   ├── infra-adapter-state-web/
│   ├── infra-adapter-ui-mui/
│   └── infra-adapter-ui-native/
└── mobile/
    ├── infra-adapter-biometric-mobile/
    ├── infra-adapter-device-mobile/
    ├── infra-adapter-module-mobile/
    ├── infra-adapter-navigation-mobile/
    ├── infra-adapter-push-mobile/
    └── infra-adapter-storage-mobile/
```

## Representative Capabilities

| Adapter Area | Typical Capabilities |
|--------------|----------------------|
| Eventing | `EventBusCapability`, outbox processing, webhook-based delivery |
| State and locking | `StateStoreCapability`, `LockCapability`, cache-oriented persistence |
| Data access | relational data access, object storage, identifier generation |
| Observability | tracing, metrics, and logging integration |
| Frontend runtime integration | HTTP, router, module federation, native bridge, UI runtime adapters |
| Mobile integration | device APIs, navigation, storage, push, and biometric support |

---

## Getting Started

### Maven Dependencies

```xml
<dependency>
    <groupId>io.brix</groupId>
    <artifactId>infra-adapter-kafka</artifactId>
    <version>3.0.0-SNAPSHOT</version>
</dependency>

<dependency>
    <groupId>io.brix</groupId>
    <artifactId>infra-adapter-redis</artifactId>
    <version>3.0.0-SNAPSHOT</version>
</dependency>
```

### Spring Boot Auto-Configuration

Most server-side adapters expose Spring Boot auto-configuration. A Host can enable them declaratively through dependencies and configuration.

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:9092
  redis:
    host: localhost
    port: 6379
```

## Naming Conventions

| Item | Convention | Notes |
|------|------------|-------|
| Maven GroupId | `io.brix` | Open-source package namespace |
| Java package | `io.infra.adapter.*` | Neutral code package naming |
| npm scope | `@brix` | Shared frontend package namespace |
| License | Apache License 2.0 | See the repository license file |

## Related Repositories

- Runtime contracts and orchestration live in the Runtime SDK repository.
- Platform-level shared capabilities live in Platform Commons.
- Host projects assemble these adapters through dependency declarations and configuration only.

## License

Apache License 2.0. See [LICENSE](LICENSE) for details.
