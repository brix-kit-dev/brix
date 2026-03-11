# Platform DevTools

Platform DevTools contains development-time tooling for Brix. These packages support scaffolding, architectural governance, and design-system support, but they are not runtime dependencies of production Hosts or plugins.

## Tooling Scope

| Package | Purpose | Typical Usage |
|---------|---------|---------------|
| `@brix/create-brix` | scaffolding CLI for plugins, applications, and services | `pnpm create @brix/brix` |
| `@brix/design-tokens` | shared design tokens for frontend development | `devDependencies` |
| `architecture-guard` | architecture conformance checks for Java modules | build and CI validation |
| `eslint-config-architecture` | architecture-aware ESLint rules for frontend code | frontend linting |

## Quick Start

### Create a Plugin

```bash
pnpm create @brix/brix plugin
```

### Create a Service

```bash
pnpm create @brix/brix service
```

## Repository Layout

```text
platform-devtools/
├── @brix/
│   ├── create-brix/
│   └── design-tokens/
├── architecture-guard/
└── eslint-config-architecture/
```

## Usage Rules

| Rule | Description |
|------|-------------|
| Runtime modules must not depend on devtools at runtime | DevTools remain build-time and authoring-time only |
| Generated code must follow the Runtime Shell architecture | Scaffolding must preserve layer boundaries and contract-first design |
| Architecture rules must reflect the blueprint | Governance logic must stay aligned with the v3.0.7 Runtime Shell design |

## Development

```bash
pnpm install
pnpm build
pnpm dev
```

## License

Apache License 2.0. See [LICENSE](LICENSE) for details.
