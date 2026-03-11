# Contributing to Brix Runtime SDK

> Version: v3.2.0  
> Last updated: 2026-02-13

Thank you for contributing to Brix Runtime SDK. This document provides the guidelines required to participate in project development.

## Table of Contents

1. [Development Environment Setup](#development-environment-setup)
2. [Architecture Guide](#architecture-guide)
3. [Development Standards](#development-standards)
4. [Submission Process](#submission-process)

---

## Development Environment Setup

### Requirements

- **Node.js**: >= 18.0.0
- **pnpm**: >= 8.0.0
- **TypeScript**: >= 5.0.0

### Quick Start

```bash
# 1. Clone the repository
git clone https://github.com/brix-platform/runtime-sdk.git
cd runtime-sdk

# 2. Install dependencies
pnpm install

# 3. Build the project
pnpm build

# 4. Run tests
pnpm test

# 5. Run lint checks
pnpm lint
```

### Project Structure

```
runtime-sdk/
├── runtime-manifest/           # Manifest definitions (Java)
├── runtime-manifest-web/       # Manifest definitions (Web)
├── runtime-orchestrator/       # Runtime orchestrator (Java)
├── runtime-orchestrator-web/   # Runtime orchestrator (Web)
├── runtime-sdk-api/            # SDK API contracts (Java)
├── runtime-sdk-api-web/        # SDK API contracts (Web)
└── architecture-guard/         # Architecture guard
```

---

## Architecture Guide

### Ultra-Thin Host Architecture Principle

Runtime SDK follows the "Ultra-Thin Host Architecture" principle:

- **runtime-sdk-api(-web)** is a pure contract layer, containing no framework-specific code
- React Hooks and other framework bindings should be placed in the **runtime-sdk-react** package
- Capability implementations belong in **infra-adapters** or **platform-commons**

### Architecture Red Lines

Before submitting code, ensure you do not violate the following red lines:

| Red Line | Rule | Description |
|----------|------|-------------|
| **R1** | No circular dependencies | Circular references between modules are not allowed |
| **R2** | No implementations in contract layer | runtime-sdk-api only defines interfaces |
| **R3** | No direct HTTP clients | Use `HttpCapability` instead of fetch/axios |
| **R4** | No direct state storage | Use `StateStoreCapability` instead of localStorage |
| **R5** | No cross-layer dependencies | Upper layers may depend on lower layers, not vice versa |

### Layer Structure

```
Layer 4: Commercial Layer
    ↓
Layer 3: Host Layer (Ultra-Thin Assembly Layer)
    ↓
Layer 2.5: Capability Implementation Layer (infra-adapters, platform-commons)
    ↓
Layer 2: Capability Contract Layer (runtime-sdk-api) ← This repository
    ↓
Layer 1: Plugin Layer (enterprise-solutions)
```

---

## Development Standards

### TypeScript Standards

```typescript
// ✅ Correct: Export interface definitions
export interface HttpCapability {
  get<T>(url: string, params?: Record<string, unknown>): Promise<T>;
  post<T>(url: string, data?: unknown): Promise<T>;
}

// ❌ Wrong: Including implementation in contract layer
export class HttpCapabilityImpl implements HttpCapability {
  // Implementation code should be in infra-adapters
}
```

### Naming Conventions

| Type | Naming Rule | Example |
|------|-------------|---------|
| Interface | PascalCase | `HttpCapability`, `EventBusCapability` |
| Type | PascalCase | `NavigationOptions`, `PluginManifest` |
| Function | camelCase | `createHttpCapability`, `useHttp` |
| Constant | UPPER_SNAKE_CASE | `DEFAULT_TIMEOUT`, `API_VERSION` |

### Documentation Standards

All public APIs must include JSDoc/TSDoc comments:

```typescript
/**
 * HTTP Capability Interface
 * 
 * [Architecture Note]
 * This interface is defined in the capability contract layer, with implementation in infra-adapters.
 * Plugin layers obtain instances through dependency injection; direct instantiation is prohibited.
 * 
 * @since 3.0.0
 * @see v3.0-runtime-shell-architecture-blueprint.md §7.1
 */
export interface HttpCapability {
  /**
   * Send a GET request
   * 
   * @typeParam T - Response data type
   * @param url - Request URL
   * @param params - Query parameters
   * @returns Promise containing response data
   */
  get<T>(url: string, params?: Record<string, unknown>): Promise<T>;
}
```

---

## Submission Process

### Branch Naming

- `feature/xxx` - New features
- `fix/xxx` - Bug fixes
- `refactor/xxx` - Refactoring
- `docs/xxx` - Documentation updates

### Commit Standards

Use Conventional Commits format:

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Types**:
- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation
- `refactor`: Refactoring
- `test`: Tests
- `chore`: Build/tooling

**Example**:
```
feat(runtime-sdk-api-web): add ConfigStoreCapability interface

Add configuration storage capability interface for unified config access.
Complies with architecture red line R6.

Refs: #123
```

### Pull Request Process

1. **Before creating a PR**
   ```bash
   # Run architecture tests
   mvn test -Dtest="*ArchitectureTest"
   
   # Run lint
   pnpm lint
   
   # Run unit tests
   pnpm test
   ```

2. **PR title format**: `[module-name] Short description`

3. **PR description template**:
   ```markdown
   ## Change Description
   - Describe the content of this change
   
   ## Related Issues
   - Closes #xxx
   
   ## Test Verification
   - [ ] Unit tests pass
   - [ ] Architecture tests pass
   - [ ] Lint checks pass
   
   ## Architecture Compliance
   - [ ] Does not violate architecture red lines
   - [ ] Complies with Ultra-Thin Host principle
   ```

4. **Code review**: Requires approval from at least 1 maintainer

---

## Contact

- **Issue Tracking**: GitHub Issues
- **Technical Discussions**: GitHub Discussions
- **Security Issues**: security@brix.dev

---

Thank you for your contribution!
