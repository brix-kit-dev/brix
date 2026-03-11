# Changelog

All notable changes to Platform Commons are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [1.0.0] - 2026-03-11

### Added
- Initial open-source release of Brix Platform Commons
- **Client modules**:
  - `platform-auth-web`: Authentication and authorization for web
  - `platform-config-web`: Runtime configuration access
  - `platform-eventbus-web`: Web event bus integration
  - `platform-i18n-web`: Internationalization support
  - `platform-navigation-web`: Navigation management
  - `platform-router-web`: Router integration
  - `platform-state-web`: State management
- **Server modules**:
  - `platform-auth`: Authentication common functionality
  - `platform-common`: Shared server utilities
  - `platform-config`: Configuration management
  - `platform-gateway`: API gateway support
  - `platform-observability`: Logging, metrics, and tracing

### Architecture
- Implements Layer 2C of Runtime Shell Architecture
- Platform-level shared capabilities for Host assembly
- Business-neutral, reusable platform code

---

[1.0.0]: https://github.com/brix-framework/brix/releases/tag/v1.0.0
