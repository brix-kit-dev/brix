# @brix/aggregates — adapters-web

> **Status**: 🚧 WIP (Work In Progress)  
> **Target**: v4.0  
> **Purpose**: Aggregate entry-point for all web infra-adapter packages

This module is a **planned aggregate** that will re-export all web-side
infrastructure adapter implementations from a single entry point,
simplifying dependency declarations for Host and plugin consumers.

## Planned Scope

```text
@brix/aggregates-adapters-web
  ├── re-exports from @brix-sdk/infra-adapter-http-web
  ├── re-exports from @brix-sdk/infra-adapter-eventbus-web
  ├── re-exports from @brix-sdk/infra-adapter-config-web
  └── ...
```

## Why Empty

The individual adapters are consumed directly today. This aggregate will
be created when the adapter count warrants a convenience meta-package.
