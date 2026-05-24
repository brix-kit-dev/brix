# @brix-sdk/platform-admin-web

Platform Super-Admin Web SDK: pages, hooks, route guards, and repositories for
the `platform-admin` backend module.

This package implements the v2.0 super-admin web flow from
`docs/v2.0-平台超管功能最小实现-设计蓝图.md`.

## Architecture

`platform-admin-web` is the client mirror of a Layer 2C platform capability. It
lives in `platform-commons`, not in `enterprise-solutions`.

The layering is:

```text
View / Page -> Hook -> Repository -> HttpCapability
```

Business plugins must not depend on this package, and this package must not
depend on any `enterprise-*` package.

## Public Surface

| Sub-export | Contents |
| --- | --- |
| `@brix-sdk/platform-admin-web` | Convenience barrel |
| `@brix-sdk/platform-admin-web/module` | Runtime module definition and route contributions |
| `@brix-sdk/platform-admin-web/constants` | Routes, permissions, API paths, audit actions |
| `@brix-sdk/platform-admin-web/repositories` | Repository factories |
| `@brix-sdk/platform-admin-web/hooks` | React view-model hooks |
| `@brix-sdk/platform-admin-web/pages` | Page components |

## Red Lines

- No direct `fetch`, `axios`, or `window.fetch`; use Repository -> `HttpCapability`.
- No direct `antd`, `@mui/material`, or concrete UI library imports; use `useUI()`.
- No platform auth decisions from legacy response booleans; use token scope and permissions.
- No plaintext credentials, setup token, setup URL, or MFA secret in UI state, API response handling, or logs.
- No permission fallback such as `permissions.canX || true`.
