# @brix-sdk/platform-tenant-web

Tenant web plugin contract for the ACTIVE Runtime Shell baseline.

This package exposes the manifest-backed route assembly and FIRST_OWNER
invitation acceptance workflow used during tenant activation. Tenant context,
selector, switcher and view-mode helpers are not part of the current clean
package surface.

## Public Surface

| Export | Contents |
| --- | --- |
| `@brix-sdk/platform-tenant-web` | Constants, UI manifest, route snapshot helpers, FIRST_OWNER page and repositories |

## Usage

```tsx
import {
  createPlatformTenantRouteSnapshot,
  FirstOwnerInvitationPage,
} from '@brix-sdk/platform-tenant-web';
```

The FIRST_OWNER invitation URL must be treated as a short-lived activation
credential. The token is consumed by the page workflow and must not be stored,
logged or propagated as a normal tenant/session token.

## Architecture

`platform-tenant-web` is a frontend plugin surface. Host code consumes route
snapshots and Runtime capabilities; package internals keep the standard
`Page -> Hook -> Repository -> HttpCapability` layering.

Tenant login, tenant selector and tenant switching must be reintroduced only
through the ACTIVE multi-tenant/auth contracts, not through legacy direct
tenant-id switching APIs.
