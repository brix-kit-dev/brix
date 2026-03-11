# Changelog

All notable changes to Brix Runtime SDK are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-03-11

### Added
- Initial open-source release of Brix Runtime Shell Framework
- **runtime-sdk-api**: Core capability contracts and lifecycle abstractions
  - `HttpCapability`, `StateCapability`, `EventBusCapability`
  - `RouterCapability`, `AuthContextCapability`, `ObservabilityCapability`
  - Module lifecycle management interfaces
- **runtime-manifest**: Manifest parsing and validation for declarative runtime configuration
- **runtime-orchestrator**: Module registration, lifecycle management, and event routing
- **runtime-sdk-api-web**: Web capability contracts and shared frontend types
- **runtime-manifest-web**: Web manifest parsing and validation
- **runtime-orchestrator-web**: Web runtime composition and plugin lifecycle
- **runtime-sdk-react**: React integration helpers and providers
- **runtime-sdk-api-mobile**: Mobile capability contracts

### Architecture
- Implements Runtime Shell Architecture v3.0.7 blueprint
- Three-layer capability model: Contracts (2A), Shared Runtime (2B), Implementations (2C)
- Ultra-thin Host design principle
- Plugin isolation through capability contracts

---

[1.0.0]: https://github.com/brix-framework/brix/releases/tag/v1.0.0
