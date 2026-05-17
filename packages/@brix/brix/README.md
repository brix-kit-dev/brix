# @brix-sdk/brix

Runtime Shell SDK facade for Brix frontend plugins.

This package exposes only Runtime Capability contracts, React hooks, runtime
context helpers, manifest types, and shared-runtime utilities. Platform
capability implementations and infrastructure adapters are intentionally not
exported from this package; Hosts assemble those implementations internally and
plugins access them only through RuntimeContext or hooks.

## Installation

```bash
pnpm add @brix-sdk/brix
```

## Usage

```typescript
import { useAuth, useHttp, useNavigation, useConfig } from '@brix-sdk/brix';

export function PluginToolbar() {
  const auth = useAuth();
  const http = useHttp();
  const navigation = useNavigation();
  const config = useConfig();

  return {
    auth,
    http,
    navigation,
    config,
  };
}
```

## Subpaths

| Path | Description |
| ---- | ----------- |
| `@brix-sdk/brix` | Hooks and public Runtime Capability types |
| `@brix-sdk/brix/runtime` | Runtime SDK contracts, context, manifest, and shared-runtime utilities |
| `@brix-sdk/brix/hooks` | React hooks backed by RuntimeContext |

## Architecture Contract

Plugins must not import platform implementations or infrastructure adapters.
HTTP, UI, navigation, state, auth, events, and configuration are obtained
through Runtime Capability contracts provided by the Host runtime shell.
