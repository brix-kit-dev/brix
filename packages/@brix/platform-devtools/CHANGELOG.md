# Changelog

All notable changes to Platform DevTools are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/).

## [3.2.0] - 2026-02-13

### Added
- `architecture-guard`: added implementations for architectural guardrails R6 and R7
- `eslint-config-architecture`: added frontend cross-layer dependency checks

### Changed
- upgraded ArchUnit to the 1.2.x line for clearer rule definitions
- improved validation messages with more actionable remediation guidance

### Fixed
- fixed false positives in Manifest declaration checks
- fixed compatibility issues between ESLint rules and TypeScript 5.3

---

## [3.1.0] - 2026-01-15

### Added
- architecture guard rules:
  - `DependencyDirectionRule` for dependency direction checks (R2)
  - `NoCircularDependencyRule` for circular dependency detection (R1)
  - `CapabilityInterfaceRule` for capability contract enforcement (R3)
  - `ManifestDeclarationRule` for manifest declaration checks (R4)
  - `ContractLocationRule` for contract placement checks (R5)
- scaffolding and design tools:
  - `create-brix`
  - `design-tokens`
- `eslint-config-architecture` for frontend architecture linting

### Changed
- rebuilt architecture validation around ArchUnit

---

## [3.0.0] - 2025-12-01

### Added
- the initial Brix development tooling suite
- architecture guardrail definitions and detection mechanisms for R1 through R7

### Changed
- moved architectural validation from runtime checks to build-time checks
- adopted ArchUnit as the Java architecture testing framework

---

## [2.x] - Archived

Version 2.x has been archived and is no longer maintained. Upgrade to 3.x.

---

[3.2.0]: https://github.com/brix-platform/platform-devtools/compare/v3.1.0...v3.2.0
[3.1.0]: https://github.com/brix-platform/platform-devtools/compare/v3.0.0...v3.1.0
[3.0.0]: https://github.com/brix-platform/platform-devtools/releases/tag/v3.0.0
