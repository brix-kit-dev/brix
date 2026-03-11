# Contributing to Brix Platform Commons

> Version: v3.2.0  
> Last updated: 2026-02-13

Thank you for contributing to Platform Commons. This repository contains platform-level capability implementations and shared modules that must remain aligned with the Runtime Shell blueprint.

## Development Environment

### Requirements

- Java 17+
- Maven 3.8+
- Node.js 18+
- pnpm 8+

### Local Setup

```bash
git clone https://github.com/brix-platform/platform-commons.git
cd platform-commons

mvn clean install
pnpm install

mvn test
pnpm test
```

## Architecture Guidance

### Repository Responsibility

Platform Commons belongs to Layer 2C, the Capability Implementation Layer. It provides reusable platform capabilities that Hosts can assemble, such as authentication, gateway integration, observability, navigation, state, and configuration support.

### Runtime Shell Rules

| Rule | Description |
|------|-------------|
| Plugins depend on contracts, not implementations | do not require plugins to import platform modules directly |
| Platform code stays business-neutral | no solution-specific or domain-specific logic in shared modules |
| Public APIs must remain stable | changes to exported contracts and module surfaces require careful review |
| Documentation must be in English | public-facing Javadoc, JSDoc, and repository docs should be maintained in English |

## Implementation Guidelines

### Java Modules

- keep Spring Boot starter and platform modules focused on platform concerns only
- document public classes and configuration properties in English
- prefer extension points and configuration over ad hoc branching logic

### TypeScript Modules

- keep exported APIs framework-appropriate and well documented in English
- avoid embedding business workflows in shared components or utilities
- preserve cross-platform clarity between shared types, UI helpers, and runtime integration

### Naming Conventions

| Type | Java | TypeScript |
|------|------|------------|
| Utility class | `XxxUtils` | `xxxUtils.ts` |
| Constants | `XxxConstants` | `xxxConstants.ts` |
| UI component | n/a | `PascalCase.tsx` |
| Platform package | `platform-xxx` | `@brix/platform-xxx` |

### Testing Expectations

- utility logic should include thorough unit coverage
- UI modules should cover critical behavior and edge cases
- public APIs should remain backward compatible unless a documented breaking change is intentional

## Pull Request Expectations

### Branch Naming

- `feature/xxx` for new capabilities
- `fix/xxx` for bug fixes
- `docs/xxx` for documentation changes

### Commit Example

```text
feat(platform-common): add date formatting utilities

Add reusable date formatting utilities for platform modules.

Refs: #789
```

### Validation Checklist

- no business logic was introduced
- public API changes are intentional and reviewed
- Javadoc or JSDoc has been updated where applicable
- tests cover the changed behavior

## Contact

- Issue tracking: GitHub Issues
- Technical discussion: GitHub Discussions
- Security reports: security@brix.dev

Thank you for contributing.
     */
