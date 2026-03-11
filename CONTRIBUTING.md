# Contributing to Brix

Thank you for your interest in contributing to Brix! This document provides guidelines and information for contributors.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [Architecture Guidelines](#architecture-guidelines)
- [Code Style](#code-style)
- [Commit Convention](#commit-convention)
- [Pull Request Process](#pull-request-process)
- [Testing](#testing)

## Code of Conduct

Please read and follow our [Code of Conduct](CODE_OF_CONDUCT.md). We expect all contributors to be respectful and inclusive.

## Getting Started

### Types of Contributions

- 🐛 **Bug Fixes**: Fix issues and improve stability
- ✨ **Features**: Add new capabilities or enhance existing ones
- 📚 **Documentation**: Improve guides, API docs, or examples
- 🧪 **Tests**: Add or improve test coverage
- 🔧 **Tooling**: Enhance build, CI/CD, or development tools

### Before You Start

1. Check [existing issues](https://github.com/brix-kit-dev/brix/issues) to avoid duplicates
2. For major changes, open an issue first to discuss the approach
3. Read the [Architecture Blueprint](docs/v3.0.7-运行壳架构设计蓝图.md) to understand the design

## Development Setup

### Prerequisites

| Tool | Version | Purpose |
|------|---------|---------|
| Node.js | >= 18.0.0 | Frontend runtime |
| pnpm | >= 8.0.0 | Package manager |
| Java | 17+ | Backend runtime |
| Maven | 3.8+ | Java build tool |

### Initial Setup

```bash
# Clone your fork
git clone https://github.com/YOUR_USERNAME/brix.git
cd brix

# Add upstream remote
git remote add upstream https://github.com/brix-kit-dev/brix.git

# Install frontend dependencies
pnpm install

# Build all frontend packages
pnpm build

# Build Java modules
mvn clean install
```

### Development Workflow

```bash
# Create a feature branch
git checkout -b feature/your-feature-name

# Make changes and test
pnpm test          # Frontend tests
mvn test           # Backend tests

# Commit and push
git add .
git commit -m "feat: your feature description"
git push origin feature/your-feature-name
```

## Architecture Guidelines

Brix follows the **Runtime Shell Architecture v3.0.7**. All contributions must adhere to these principles:

### Core Constraints

| Constraint | Description |
|------------|-------------|
| **Plugin Isolation** | Plugins depend ONLY on capability contracts (Layer 2A) |
| **No Infrastructure Leakage** | No Kafka/Redis/HTTP client imports in plugins |
| **Event-Driven Communication** | Cross-plugin communication via events only |
| **Ultra-Thin Host** | Host contains zero implementation code |

### Layer Dependency Rules

```text
✅ Allowed:
  - Plugin → runtime-sdk-api (contracts)
  - Host → infra-adapters, platform-commons (implementations)

❌ Forbidden:
  - Plugin → infra-adapters (bypasses contracts)
  - Plugin → Plugin (direct coupling)
  - Host → business logic code
```

### Architecture Verification

All code is validated by **Architecture Guard** (ArchUnit rules):

```bash
# Run architecture tests
mvn test -Dtest=ArchitectureTest

# Check for violations
mvn verify
```

## Code Style

### Java

- Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- Use Lombok annotations where appropriate
- Write Javadoc for all public APIs (in English)

```java
/**
 * Brief description of the class.
 *
 * <p>Detailed description with usage examples if needed.</p>
 *
 * @author Your Name
 * @since 3.1.0
 */
public class ExampleClass {
    // Implementation
}
```

### TypeScript/JavaScript

- Follow the ESLint configuration in the repository
- Use TypeScript strict mode
- Document public APIs with JSDoc

```typescript
/**
 * Brief description of the function.
 *
 * @param param1 - Description of first parameter
 * @returns Description of return value
 * @example
 * ```typescript
 * const result = exampleFunction('input');
 * ```
 */
export function exampleFunction(param1: string): Result {
    // Implementation
}
```

### File Naming

| Type | Frontend | Backend |
|------|----------|---------|
| Components | `PascalCase.tsx` | N/A |
| Utilities | `camelCase.ts` | `PascalCase.java` |
| Tests | `*.test.ts` / `*.spec.ts` | `*Test.java` |
| Configs | `kebab-case.ts` | `kebab-case.yml` |

## Commit Convention

We use [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <description>

[optional body]

[optional footer(s)]
```

### Types

| Type | Description |
|------|-------------|
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation only |
| `style` | Formatting, missing semicolons, etc. |
| `refactor` | Code change that neither fixes a bug nor adds a feature |
| `perf` | Performance improvement |
| `test` | Adding or updating tests |
| `chore` | Build process, auxiliary tools, or libraries |

### Examples

```bash
feat(runtime-sdk): add HttpCapability for REST calls

fix(infra-adapters): resolve Kafka connection timeout issue

docs(readme): update quick start guide

refactor(platform-commons)!: rename AuthCapability methods

BREAKING CHANGE: AuthCapability.getUser() renamed to AuthCapability.getCurrentUser()
```

## Pull Request Process

### Before Submitting

- [ ] Code follows the style guidelines
- [ ] All tests pass locally
- [ ] Architecture tests pass (`mvn test -Dtest=ArchitectureTest`)
- [ ] Documentation updated if needed
- [ ] CHANGELOG.md updated for notable changes

### PR Title Format

Follow the same convention as commits:

```
feat(scope): brief description
```

### Review Process

1. Create PR against `main` branch
2. Fill out the PR template completely
3. Wait for CI checks to pass
4. Address reviewer feedback
5. Squash and merge when approved

## Testing

### Running Tests

```bash
# All frontend tests
pnpm test

# Specific package tests
pnpm --filter @brix/runtime-sdk test

# All backend tests
mvn test

# Specific module tests
mvn test -pl packages/@brix/infra-adapters/packages/server/infra-adapter-kafka

# Architecture guard tests only
mvn test -Dtest=**/ArchitectureTest
```

### Writing Tests

- Aim for high test coverage on public APIs
- Use descriptive test names
- Test edge cases and error conditions
- Mock external dependencies appropriately

## Questions?

- Open a [Discussion](https://github.com/brix-kit-dev/brix/discussions)
- Check [existing issues](https://github.com/brix-kit-dev/brix/issues)

---

Thank you for contributing to Brix! 🎉
