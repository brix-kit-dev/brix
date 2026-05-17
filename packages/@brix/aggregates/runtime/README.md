# @brix/aggregates — runtime

> **Status**: 🚧 WIP (Work In Progress)  
> **Target**: v4.0  
> **Purpose**: Aggregate entry-point for all runtime-sdk packages

This module is a **planned aggregate** that will re-export the runtime
SDK packages (api, orchestrator, manifest, react hooks) from a single
entry point for simplified plugin development setup.

## Planned Scope

```text
@brix/aggregates-runtime
  ├── re-exports from @brix-sdk/runtime-sdk-api-web
  ├── re-exports from @brix-sdk/runtime-sdk-react
  ├── re-exports from @brix-sdk/runtime-orchestrator-web
  └── re-exports from @brix-sdk/runtime-manifest-web
```

## Why Empty

The individual runtime packages are consumed directly today. This
aggregate will be created when the developer experience benefit of
`import { ... } from '@brix/runtime'` justifies the abstraction.
