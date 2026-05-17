# @brix-sdk/platform-admin-web

> Platform Super-Admin Web SDK — Pages, Hooks and Repositories for the
> [`platform-admin`](../../server/platform-admin) backend module.
>
> Implements **§9 Frontend Page Design** of
> [`v1.0-平台超管最小实现-唯一真相来源.md`](../../../../../../../docs/v1.0-平台超管最小实现-唯一真相来源.md)
> (SSOT v1.1, frozen).

---

## Architectural Position (Layer 2C — Client Mirror)

```
@brix-sdk/platform-admin-web   ←  this package
        │ peerDependencies
        ▼
@brix-sdk/runtime-sdk-react   →  hooks: useHttp, useUI, useAuth, useTheme, …
@brix-sdk/runtime-sdk-api-web →  HttpCapability / AuthCapability / UIAdapter contracts
```

This package is published from the `brix` (open-source) monorepo. It belongs to
**platform-commons**, NOT to `enterprise-solutions/*`. Business plugins MUST NOT
depend on this package, and this package MUST NOT depend on any
`enterprise-*` package (SSOT §11 R-1 / R-2).

---

## Public Surface

| Sub-export | Contents |
|------------|----------|
| `@brix-sdk/platform-admin-web`              | Convenience barrel (re-exports below) |
| `@brix-sdk/platform-admin-web/constants`    | `PLATFORM_ADMIN_PERMISSIONS`, `PLATFORM_ADMIN_ROUTES`, `PLATFORM_AUDIT_ACTIONS` |
| `@brix-sdk/platform-admin-web/repositories` | Repository factories (no React deps) |
| `@brix-sdk/platform-admin-web/hooks`        | React hooks (require `RuntimeContextProvider`) |
| `@brix-sdk/platform-admin-web/pages`        | Page components (require `UIAdapter` + `react-router-dom`) |

---

## Architectural Red Lines (enforced by ESLint plugin `@brix-sdk/eslint-plugin-brix`)

* ❌ Direct `fetch` / `axios` / `window.fetch` — **always** go through `Repository → HttpCapability`.
* ❌ Direct imports of `antd` / `@mui/material` / any concrete UI library — use `useUI()` and `useTheme().tokens`.
* ❌ Hard-coded role strings (`"SUPER_ADMIN"`) — use `RoleCode` constants re-exported from `@brix-sdk/platform-admin-web/constants`.
* ❌ Hard-coded route paths — use `PLATFORM_ADMIN_ROUTES`.

---

## Quick Start

```tsx
import {
  PlatformLoginPage,
  SuperAdminListPage,
  PlatformDashboardPage,
} from '@brix-sdk/platform-admin-web/pages';
import { PLATFORM_ADMIN_ROUTES } from '@brix-sdk/platform-admin-web/constants';
import { Routes, Route } from 'react-router-dom';

export const PlatformAdminRouter = () => (
  <Routes>
    <Route path={PLATFORM_ADMIN_ROUTES.LOGIN}     element={<PlatformLoginPage />} />
    <Route path={PLATFORM_ADMIN_ROUTES.DASHBOARD} element={<PlatformDashboardPage />} />
    <Route path={PLATFORM_ADMIN_ROUTES.ADMINS}    element={<SuperAdminListPage />} />
    {/* ... */}
  </Routes>
);
```

## License

Apache-2.0 © Brix Platform Authors
