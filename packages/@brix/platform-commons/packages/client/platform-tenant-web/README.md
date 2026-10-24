# @brix-sdk/platform-tenant-web

Tenant web plugin contract for the ACTIVE Runtime Shell baseline.

This package exposes the manifest-backed route assembly of the staff console (`/tenant`), which
runs on the BFF session of the identity protocol surface. Tenant context, selector, switcher and
view-mode helpers are not part of the current clean package surface.

## Public Surface

| Export | Contents |
| --- | --- |
| `@brix-sdk/platform-tenant-web` | Constants, UI manifest, route snapshot helper, staff console home page |

## Usage

```tsx
import { createPlatformTenantRouteSnapshot } from '@brix-sdk/platform-tenant-web';
```

## FIRST_OWNER invitations

Invitees set up their account and accept the invitation on the hosted pages of the identity
protocol surface (`/auth/setup`, `/auth/invitation`; identity-auth BRX-IA-ONBOARD-002), never in a
console. The links carry their one-time token in the URL fragment. Acceptance itself is the tenant
Owner's entry `POST /api/tenant/first-owner-invitations/accept`.

## Architecture

`platform-tenant-web` is a frontend plugin surface. Host code consumes route
snapshots and Runtime capabilities; package internals keep the standard
`Page -> Hook -> Repository -> HttpCapability` layering.

Tenant login, tenant selector and tenant switching must be reintroduced only
through the ACTIVE multi-tenant/auth contracts, not through legacy direct
tenant-id switching APIs.
