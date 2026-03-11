# Changelog

All notable changes to Brix Infrastructure Adapters are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-03-11

### Added
- Initial open-source release of Brix Infrastructure Adapters
- **Web adapters**:
  - `infra-adapter-http-web`: Fetch API integration with interceptor support
  - `infra-adapter-state-web`: Zustand-based state management
  - `infra-adapter-router-web`: React Router integration
  - `infra-adapter-mf-web`: Module Federation support
  - `infra-adapter-native-web`: Browser-native API integration
  - `infra-adapter-ui-mui`: MUI component integration
- **Server adapters**:
  - `infra-adapter-kafka`: Kafka-based event bus
  - `infra-adapter-redis`: Redis caching and distributed locking
  - `infra-adapter-database`: Database access abstractions
  - `infra-adapter-minio`: Object storage integration
  - `infra-adapter-otel`: OpenTelemetry observability

### Architecture
- Implements Layer 2C of Runtime Shell Architecture
- Conditional assembly pattern for replaceable implementations
- Infrastructure isolation from plugin code

---

[1.0.0]: https://github.com/brix-framework/brix/releases/tag/v1.0.0
