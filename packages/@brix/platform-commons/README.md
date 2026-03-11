# Platform Commons

> Version: 3.0.0-SNAPSHOT  
> License: Apache License 2.0

---

## Architecture Position

Platform Commons is part of Layer 2C in the Brix Runtime Shell architecture. It provides platform-level shared capability implementations such as authentication, gateway support, observability, configuration, navigation, and internationalization.

```text
Layer 3: Host Assembly Layer                 <- imports these modules for composition
    ↓
Layer 2C: Capability Implementation Layer    <- this repository
    ↓ implements
Layer 2A: Capability Contract Layer          <- runtime-sdk API packages
    ↑ only dependency allowed for plugins
Layer 1: Plugin Layer                        <- must not depend directly on platform implementations
```

### Architectural Guardrails

| Rule | Description |
|------|-------------|
| Plugins depend on contracts, not implementations | Plugin code must remain infrastructure-agnostic |
| Platform Commons must not depend on business solutions | Platform code stays reusable and neutral |
| Platform Commons may integrate with infra-adapters | Infrastructure integration belongs in Layer 2C |
| Hosts assemble capabilities declaratively | Host projects remain ultra-thin and configuration-driven |

---

## Package Layout

```text
packages/
├── client/
│   ├── platform-auth-service-web/
│   ├── platform-auth-ui-web/
│   ├── platform-auth-web/
│   ├── platform-config-web/
│   ├── platform-eventbus-web/
│   ├── platform-i18n-web/
│   ├── platform-navigation-web/
│   ├── platform-router-web/
│   ├── platform-shared/
│   └── platform-state-web/
└── server/
    ├── platform-auth/
    ├── platform-common/
    ├── platform-common-starter/
    ├── platform-config/
    ├── platform-gateway/
    ├── platform-observability/
    └── platform-parent/
```

## Representative Capability Areas

### Client Modules

| Module | Package | Primary Responsibility |
|--------|---------|------------------------|
| Authentication | `@brix/platform-auth-web`, `@brix/platform-auth-service-web`, `@brix/platform-auth-ui-web` | identity integration and user-facing auth flows |
| Eventing | `@brix/platform-eventbus-web` | governed event transport for web plugins |
| Configuration | `@brix/platform-config-web` | runtime configuration access |
| Navigation | `@brix/platform-navigation-web`, `@brix/platform-router-web` | routing and navigation orchestration |
| Shared runtime helpers | `@brix/platform-shared`, `@brix/platform-state-web`, `@brix/platform-i18n-web` | shared client-side platform capabilities |

### Server Modules

| Module | Coordinates | Primary Responsibility |
|--------|-------------|------------------------|
| Starter | `io.brix.platform:platform-common-starter` | Spring Boot auto-configuration entry point |
| Auth | `io.brix.platform:platform-auth` | authentication and authorization support |
| Gateway | `io.brix.platform:platform-gateway` | API gateway, routing, resilience, and policy integration |
| Observability | `io.brix.platform:platform-observability` | tracing, metrics, and logging support |
| Config | `io.brix.platform:platform-config` | configuration loading and related platform concerns |

---

## Getting Started

### Backend Dependency

```xml
<dependency>
    <groupId>io.brix.platform</groupId>
    <artifactId>platform-common-starter</artifactId>
    <version>3.0.0-SNAPSHOT</version>
</dependency>
```

### Frontend Dependency

```bash
pnpm add @brix/platform-auth-web
```

## Naming Conventions

| Item | Convention | Notes |
|------|------------|-------|
| npm scope | `@brix` | Open-source frontend package namespace |
| Maven GroupId | `io.brix.platform` | Open-source Java package namespace |
| License | Apache License 2.0 | See the repository license file |

## License

Apache License 2.0. See [LICENSE](LICENSE) for details.
