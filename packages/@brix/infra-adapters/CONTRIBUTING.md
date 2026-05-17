# Contributing to Brix Infrastructure Adapters

> Version: v3.2.0  
> Last updated: 2026-02-13

Thank you for contributing to Brix Infrastructure Adapters. This document explains how to make changes without breaking the Runtime Shell architecture.

## Development Environment

### Requirements

- Java 17+
- Maven 3.8+
- Node.js 18+
- pnpm 8+

### Local Setup

```bash
git clone https://github.com/brix-platform/infra-adapters.git
cd infra-adapters

mvn clean install
pnpm install

mvn test
pnpm test
pnpm lint
```

## Architecture Guidance

### Repository Responsibility

Infrastructure Adapters belongs to Layer 2C, the Capability Implementation Layer. Its responsibilities are:

- implement contracts defined in Runtime SDK API packages
- encapsulate concrete infrastructure technologies such as Redis, Kafka, HTTP, telemetry, storage, and native platform APIs
- provide replaceable implementations that a Host can assemble declaratively

### Runtime Shell Rules

| Rule | Description |
|------|-------------|
| Contracts stay above implementations | new capability interfaces belong in Runtime SDK, not in this repository |
| Plugins do not depend on infra-adapters | plugin code must stay portable across Hosts |
| Hosts remain ultra-thin | Hosts assemble adapters through dependencies and configuration only |
| Implementations must be replaceable | prefer conditional assembly and interface-based registration |

### Recommended Registration Pattern

```java
@Configuration
@ConditionalOnClass(RedisTemplate.class)
@ConditionalOnProperty(name = "brix.cache.type", havingValue = "redis")
public class RedisCacheAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CacheCapability.class)
    public CacheCapability cacheCapability(RedisTemplate<String, Object> redisTemplate) {
        return new RedisCacheCapability(redisTemplate);
    }
}
```

## Implementation Guidelines

### Java Adapters

- document public classes with clear English Javadoc
- describe the contract being implemented and why the implementation remains replaceable
- keep infrastructure details internal to the adapter implementation
- avoid leaking vendor-specific types into contract-facing APIs

### TypeScript Adapters

- document exported factories and public types with clear English comments
- preserve contract-first APIs and keep framework-specific details internal
- avoid coupling plugin-facing surfaces to concrete browser or framework implementations

### Naming Conventions

| Type | Java | TypeScript |
|------|------|------------|
| Adapter implementation | `XxxCapabilityImpl` or `XxxAdapter` | `createXxxCapability` |
| Auto-configuration | `XxxAutoConfiguration` | `XxxConfig` |
| Module name | `infra-adapter-xxx-yyy` | `@brix-sdk/infra-adapter-xxx-yyy` |

## Pull Request Expectations

### Branch Naming

- `feature/xxx` for new adapters or features
- `fix/xxx` for bug fixes
- `refactor/xxx` for internal restructuring
- `docs/xxx` for documentation updates

### Commit Example

```text
feat(infra-adapter-cache-redis): add TTL support

Add TTL expiration support to the Redis cache adapter.

Refs: #456
```

### Validation Checklist

Before opening a pull request, run:

```bash
mvn test -Dtest="*ArchitectureTest"
pnpm lint
mvn test
pnpm test
```

Confirm the following:

- the adapter implements an existing Runtime SDK contract
- registration is conditional and replaceable when appropriate
- no dependency points upward to Host or plugin modules
- no new contract interfaces were introduced in this repository

## Contact

- Issue tracking: GitHub Issues
- Technical discussion: GitHub Discussions
- Security reports: security@brix.dev

Thank you for contributing.

