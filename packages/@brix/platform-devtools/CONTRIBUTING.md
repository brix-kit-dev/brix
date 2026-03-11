# Contributing to Brix Platform - Platform DevTools
# Contributing to Brix Platform DevTools

> Version: v3.2.0  
> Last updated: 2026-02-13

Thank you for contributing to Platform DevTools. This repository contains the scaffolding, linting, and architecture-governance tooling used to keep Brix aligned with the Runtime Shell blueprint.

## Development Environment

### Requirements

- Java 17+
- Maven 3.8+
- Node.js 18+
- pnpm 8+

### Local Setup

```bash
git clone https://github.com/brix-platform/platform-devtools.git
cd platform-devtools

mvn clean install -DskipTests
pnpm install

mvn test -Dtest="*ArchitectureTest"
```

## Architecture Guidance

### Repository Responsibility

Platform DevTools provides development-time tooling only:

- architecture validation in CI and local builds
- scaffolding that generates blueprint-compliant module structures
- lint rules for frontend layer boundaries and architectural constraints
- shared design tokens for development workflows

### Quality Rules

| Rule | Description |
|------|-------------|
| Tooling must reflect the current blueprint | rules and generators must match the v3.0.7 Runtime Shell design |
| Generated code must preserve layer boundaries | no scaffolding output may introduce cross-layer coupling |
| Public tooling docs stay in English | public-facing guidance and comments should be English |
| Guard rules need executable proof | architecture rules should include representative validation cases |

### Architecture Guard Coverage

| Guardrail | Implementation |
|-----------|----------------|
| R1 | `NoCircularDependencyRule.java` |
| R2 | `DependencyDirectionRule.java` |
| R3 | `CapabilityInterfaceRule.java` |
| R4 | `ManifestDeclarationRule.java` |
| R5 | `ContractLocationRule.java` |
| R6 | `PluginConfigExternalizationRule.java` |
| R7 | `RouteDeclarationRule.java` |

## Implementation Guidelines

### ArchUnit Rules

- explain the architectural intent in English Javadoc
- keep rules precise enough to prevent false positives in compliant modules
- prefer explicit package and dependency assertions over heuristic matching when possible

### ESLint Rules

- keep rule messages actionable and architecture-specific
- enforce contract-first frontend boundaries without leaking Host-specific assumptions into plugin code
- update rule documentation when patterns or repository structure changes

### Scaffolding

- generated modules must follow the Runtime Shell layering model
- generated documentation and comments must be English for public-facing templates
- generator defaults must use current Brix naming, not legacy brand identifiers

## Pull Request Expectations

### Branch Naming

- `feature/xxx` for new rules or tooling
- `fix/xxx` for bug fixes
- `docs/xxx` for documentation updates

### Commit Example

```text
feat(architecture-guard): add manifest declaration rule

Add the R4 guard implementation for Manifest declaration validation.

Refs: #234
```

### Validation Checklist

For architecture-rule changes, confirm that:

- the rule maps to a specific architectural guardrail
- both compliant and violating examples are covered where practical
- messages clearly explain how to resolve a violation
- existing compliant code is not broken unintentionally

## Contact

- Issue tracking: GitHub Issues
- Technical discussion: GitHub Discussions
- Security reports: security@brix.dev

Thank you for contributing.
