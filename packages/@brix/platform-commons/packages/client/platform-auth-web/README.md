# @brix-sdk/platform-auth-web

Web implementation of the Brix `AuthCapability` contract.

This package is intentionally narrow: it adapts Host-owned auth state into
provider-verified session and route-guard snapshots. UI components belong in
`@brix-sdk/platform-auth-ui-web`; browser auth transport belongs in
`@brix-sdk/platform-auth-service-web`.

## Usage

```typescript
import { AuthCapabilityImpl } from '@brix-sdk/platform-auth-web';

const auth = new AuthCapabilityImpl({
  getAuthState: () => authStore.getState(),
  subscribeAuthChange: authStore.subscribe,
  login: authHostBridge.login,
  logout: authHostBridge.logout,
});

const session = auth.getVerifiedSession();
const decision = auth.canAccessRoute({
  allowedContexts: ['platform'],
  tenantContext: 'forbidden',
  permissions: ['platform:tenant:read'],
});
```

Plugins and route guards should use verified context methods such as
`getVerifiedSession()`, `getVerifiedPlatformContext()`, `getVerifiedActorContext()`,
and `canAccessRoute()`. They must not decode JWTs or request raw bearer tokens.

## Public Surface

```typescript
export {
  AuthCapabilityImpl,
  type AuthCapabilityConfig,
  type InternalAuthState,
  type AuthChangeHandler,
} from '@brix-sdk/platform-auth-web';
```

## License

Apache-2.0
