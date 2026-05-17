# @brix/aggregates — platform-web

> **Status**: 🚧 WIP (Work In Progress)  
> **Target**: v4.0  
> **Purpose**: Aggregate entry-point for all web platform-commons packages

This module is a **planned aggregate** that will re-export all web-side
platform capability implementations (auth, router, i18n, frame, etc.)
from a single entry point.

## Planned Scope

```text
@brix/aggregates-platform-web
  ├── re-exports from @brix-sdk/platform-frame-web
  ├── re-exports from @brix-sdk/platform-auth-web
  ├── re-exports from @brix-sdk/platform-router-web
  ├── re-exports from @brix-sdk/platform-i18n-web
  └── ...
```

## Why Empty

The individual platform packages are consumed directly today. This
aggregate will be created when the package count warrants a convenience
meta-package.
