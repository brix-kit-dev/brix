# @brix-sdk/platform-admin-web

Platform Super-Admin Web SDK: manifest-backed route assembly and contract types
for the `platform-admin` backend module.

This package implements the active v3.0.10 Runtime Shell frontend slice for the
platform administration UI.

## Architecture

`platform-admin-web` is a Layer 1 platform-operational UI module. It lives in
`platform-commons`, not in `enterprise-solutions`.

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
| `@brix-sdk/platform-admin-web/manifest` | UI manifest contract and validation helpers |

Page, Hook and Repository files are internal. Hosts and other plugins must use
the package root or `./module` route snapshot instead of importing UI internals.

## Red Lines

- No direct `fetch`, `axios`, or `window.fetch`; use Repository -> `HttpCapability`.
- No direct `antd`, `@mui/material`, or concrete UI library imports; use `useUI()`.
- No platform auth decisions from legacy response booleans; use token scope and permissions.
- No plaintext credentials, setup token, setup URL, or MFA secret in UI state, API response handling, or logs.
- No permission fallback such as `permissions.canX || true`.
