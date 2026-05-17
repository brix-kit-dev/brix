# Brix Runtime SDK

> Version: 3.0.0-SNAPSHOT  
> License: Apache License 2.0  
> Naming strategy: Maven coordinates use the `io.brix` brand, while Java packages remain implementation-neutral under `io.runtime.*`

---

## Architecture Position

The Runtime SDK is the contract-first core of the Brix Runtime Shell architecture. It defines the capability interfaces that plugins may depend on, plus the manifest and orchestration modules needed to bootstrap and govern runtime behavior.

```text
Layer 3: Host Assembly Layer                 <- assembles implementations around this repository
        �?
Layer 2C: Capability Implementation Layer    <- infra-adapters and platform-commons
        �?implements
Layer 2A / 2B: Contracts and Shared Runtime  <- this repository is centered here
        �?only dependency allowed for plugins
Layer 1: Plugin Layer                        <- depends only on API contracts
```

### Architectural Guardrails

| Rule | Description |
|------|-------------|
| API packages must not depend on implementation modules | No dependency on infra-adapters, platform-commons, or business code |
| Plugins depend on contracts only | Plugin code stays portable across Hosts |
| Infrastructure details stay outside plugin code | No Kafka, Redis, JDBC, or raw HTTP client coupling in plugins |
| Runtime requirements are declared, not hard-wired | Manifests and configuration drive assembly |

---

## Repository Layout

```text
runtime-sdk/
├── runtime-sdk-api/          # Java capability contracts, annotations, and support types
├── runtime-manifest/         # Java manifest parsing and validation
├── runtime-orchestrator/     # Java lifecycle orchestration and event routing
├── runtime-sdk-api-web/      # Web capability contracts and shared frontend types
├── runtime-manifest-web/     # Web manifest parsing and validation
├── runtime-orchestrator-web/ # Web runtime orchestration
├── runtime-sdk-react/        # React bindings for the web runtime
└── runtime-sdk-api-mobile/   # Mobile capability contracts
```

## Naming Conventions

| Dimension | Convention | Notes |
|-----------|------------|-------|
| Maven GroupId | `io.brix` | Open-source package namespace |
| Maven ArtifactId | `runtime-sdk-api`, `runtime-orchestrator`, `runtime-manifest` | Purpose-driven artifact naming |
| Java packages | `io.runtime.sdk.*`, `io.runtime.manifest.*`, `io.runtime.orchestrator.*` | Neutral implementation naming |
| npm scope | `@brix` | Shared frontend package namespace |

### Java Modules

| Module | Responsibility | Notes |
|--------|----------------|-------|
| `runtime-sdk-api` | core capability contracts, annotations, lifecycle abstractions | primary dependency for Java plugins |
| `runtime-manifest` | manifest parsing and validation | keeps runtime requirements declarative |
| `runtime-orchestrator` | module registration, lifecycle management, event routing | used by Host-side assembly |

### Web Modules

| Module | Responsibility | Notes |
|--------|----------------|-------|
| `@brix-sdk/runtime-sdk-api-web` | web capability contracts and shared types | primary dependency for web plugins |
| `@brix-sdk/runtime-manifest-web` | manifest parsing and validation | supports declarative plugin loading |
| `@brix-sdk/runtime-orchestrator-web` | runtime composition and plugin lifecycle | used by web Hosts |
| `@brix-sdk/runtime-sdk-react` | React integration helpers and providers | binds capability access into React applications |

### Mobile Module

| Module | Responsibility | Notes |
|--------|----------------|-------|
| `@brix-sdk/runtime-sdk-api-mobile` | mobile capability contracts and shared types | primary dependency for mobile plugins |

---

## Quick Start

### Java Plugin Dependency

```xml
<dependency>
        <groupId>io.brix</groupId>
        <artifactId>runtime-sdk-api</artifactId>
        <version>3.0.0-SNAPSHOT</version>
</dependency>
```

### Web Plugin Dependency

```bash
pnpm add @brix-sdk/runtime-sdk-api-web
```

### Example Java Module

```java
import io.runtime.sdk.annotation.EventHandler;
import io.runtime.sdk.annotation.Module;
import io.runtime.sdk.context.RuntimeContext;
import io.runtime.sdk.support.AbstractModule;

@Module(id = "my-module", name = "My Module")
public class MyModule extends AbstractModule {

        @Override
        protected void doInit(RuntimeContext context) {
                // Acquire capabilities from the runtime context.
        }

        @Override
        protected void doStart() {
                // Start module-owned workflows.
        }

        @EventHandler
        public void onUserCreated(UserCreatedEvent event) {
                // Handle declared events without coupling to infrastructure details.
        }
}
```

### Example Manifest

```yaml
module:
    id: my-module
    name: My Module
    version: 1.0.0

capabilities:
    required:
        - event-bus
        - state-store

events:
    publishes:
        - type: com.example.MyEvent
    subscribes:
        - type: com.example.UserCreatedEvent
            handler: com.example.MyModule.onUserCreated
```

---

## Core Capabilities

| Capability | Interface | Purpose |
|------------|-----------|---------|
| Event bus | `EventBusCapability` | publish and subscribe to domain or integration events |
| State store | `StateStoreCapability` | abstract key-value or durable state access |
| Auth context | `AuthContextCapability` | current principal, tenant, and permission information |
| Observability | `ObservabilityCapability` | logging, metrics, and tracing hooks |
| Config store | `ConfigStoreCapability` | runtime configuration access |
| Lifecycle | `LifecycleCapability` | lifecycle coordination callbacks |
| Resilience | `ResilienceCapability` | retry, circuit breaker, and degradation support |
| Locking | `LockCapability` | distributed locking support when required |
| Scheduling | `SchedulingCapability` | runtime-driven task scheduling |

---

## Build

```bash
mvn clean install
```

## License

Apache License 2.0. See [LICENSE](LICENSE) for details.
