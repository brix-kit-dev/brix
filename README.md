# Brix Framework

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Build Status](https://img.shields.io/github/actions/workflow/status/brix-framework/brix/ci.yml?branch=main)](https://github.com/brix-framework/brix/actions)

**Brix** is an open-source Runtime Shell framework that provides capability contracts for building modular, pluggable applications.

## Architecture

Brix implements the **Runtime Shell Architecture** (v3.0.4), featuring:

- **Capability Contract Model**: Abstract interfaces that plugins depend on
- **Plugin Isolation**: Plugins communicate only through events
- **Infrastructure Agnostic**: No direct dependencies on Kafka, Redis, etc.
- **Host Assembly**: Ultra-thin configuration-driven capability orchestration

## Packages

| Package | Description | Status |
|---------|-------------|--------|
| `@brix/runtime-sdk` | Runtime capability contracts and orchestration | Stable |
| `@brix/infra-adapters` | Infrastructure adapter implementations | Stable |
| `@brix/platform-commons` | Platform-level common capabilities | Stable |
| `@brix/platform-devtools` | Development and build tools | Stable |

## Quick Start

### Prerequisites

- Node.js >= 18.0.0
- pnpm >= 8.0.0
- Java 17+ (for backend modules)
- Maven 3.8+

### Installation

```bash
# Clone the repository
git clone https://github.com/brix-framework/brix.git
cd brix

# Install dependencies
pnpm install

# Build all packages
pnpm build

# For Java modules
mvn clean install -DskipTests
```

## Documentation

- [Architecture Blueprint](docs/architecture-blueprint.md)
- [Capability Contract Reference](docs/capability-contracts.md)
- [Plugin Development Guide](docs/plugin-development.md)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for contribution guidelines.

## License

Apache License 2.0 - see [LICENSE](LICENSE) for details.

---

Copyright 2026 Brix Platform Authors
