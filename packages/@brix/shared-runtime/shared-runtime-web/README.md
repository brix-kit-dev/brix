# @brix-sdk/shared-runtime-web

> Brix Platform Web Runtime Dependencies - Single Source of Truth for Module Federation

[![npm version](https://img.shields.io/npm/v/@brix-sdk/shared-runtime-web.svg)](https://www.npmjs.com/package/@brix-sdk/shared-runtime-web)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

## Overview

`@brix-sdk/shared-runtime-web` is the **Single Source of Truth** for frontend runtime dependencies in the Brix Platform. It implements **Layer 2B (Shared Runtime Layer)** of the v3.0.7 Architecture Blueprint.

### Why This Package Exists

In Module Federation environments, multiple remotes (plugins) can each bundle their own copies of React, Router, and other libraries. This causes:

| Problem | Symptom |
|---------|---------|
| Multiple React instances | "Invalid Hook Call" errors |
| Context fragmentation | Plugins can't share state with Host |
| Version drift | Incompatible library versions at runtime |
| Bundle bloat | Duplicate code across plugin bundles |

This package solves these by:

1. âœ?Providing canonical versions for all runtime dependencies
2. âœ?Exporting pre-configured Module Federation shared configurations  
3. âœ?Re-exporting runtime APIs for consistent imports
4. âœ?Offering utilities for global injection and version checking

## Installation

```bash
pnpm add @brix-sdk/shared-runtime-web
```

## Package Exports

| Export Path | Description |
|-------------|-------------|
| `@brix-sdk/shared-runtime-web` | Version constants, MF config, global injection |
| `@brix-sdk/shared-runtime-web/react` | React and ReactDOM re-exports |
| `@brix-sdk/shared-runtime-web/router` | React Router re-exports |
| `@brix-sdk/shared-runtime-web/state` | Zustand state management re-exports |
| `@brix-sdk/shared-runtime-web/ui` | MUI and Emotion re-exports |
| `@brix-sdk/shared-runtime-web/mf-config` | Module Federation shared configuration |
| `@brix-sdk/shared-runtime-web/versions` | Version constants and utilities |
| `@brix-sdk/shared-runtime-web/globals` | Global window injection utilities |

## Usage

### Host Application

```typescript
// rspack.config.mjs
import { getHostSharedConfig } from '@brix-sdk/shared-runtime-web/mf-config';

export default {
  plugins: [
    new ModuleFederationPlugin({
      name: 'brix_host',
      remotes: {
        partners: 'partners@http://localhost:3001/remoteEntry.js',
      },
      shared: getHostSharedConfig(),
    }),
  ],
};
```

```typescript
// bootstrap.ts
import { injectGlobals } from '@brix-sdk/shared-runtime-web';
import { createRoot } from '@brix-sdk/shared-runtime-web/react';

// Inject globals for legacy compatibility (optional)
injectGlobals();

// Create React root
const root = createRoot(document.getElementById('root')!);
root.render(<App />);
```

### Plugin Development

```typescript
// rspack.config.mjs
import { getRemoteSharedConfig } from '@brix-sdk/shared-runtime-web/mf-config';

export default {
  plugins: [
    new ModuleFederationPlugin({
      name: 'partners',
      filename: 'remoteEntry.js',
      exposes: {
        './PartnersApp': './src/PartnersApp.tsx',
      },
      shared: getRemoteSharedConfig(),
    }),
  ],
};
```

```typescript
// PartnersApp.tsx
import { useState, useEffect } from '@brix-sdk/shared-runtime-web/react';
import { useNavigate, Link } from '@brix-sdk/shared-runtime-web/router';
import { create } from '@brix-sdk/shared-runtime-web/state';
import { Button, TextField, Box } from '@brix-sdk/shared-runtime-web/ui';

// Plugin component using shared runtime
export function PartnersApp() {
  const [name, setName] = useState('');
  const navigate = useNavigate();
  
  return (
    <Box p={2}>
      <TextField 
        value={name} 
        onChange={(e) => setName(e.target.value)} 
      />
      <Button variant="contained" onClick={() => navigate('/dashboard')}>
        Go to Dashboard
      </Button>
    </Box>
  );
}
```

### Adapter Development

```typescript
// infra-adapters/ui-adapter-web/rspack.config.mjs
import { getAdapterSharedConfig } from '@brix-sdk/shared-runtime-web/mf-config';

export default {
  plugins: [
    new ModuleFederationPlugin({
      name: 'ui_adapter',
      shared: getAdapterSharedConfig(),
    }),
  ],
};
```

## Runtime Versions

The following versions are centrally managed:

| Package | Version | Notes |
|---------|---------|-------|
| react | ^18.2.0 | React 18 LTS with concurrent features |
| react-dom | ^18.2.0 | Must match react version |
| react-router-dom | ^6.22.0 | v6 data router API |
| zustand | ^4.5.0 | Latest v4 with middleware improvements |
| @mui/material | ^7.0.0 | MUI v7 with improved theming |
| @emotion/react | ^11.11.0 | Required by MUI |
| @emotion/styled | ^11.11.0 | Required by MUI |

## API Reference

### Version APIs

```typescript
import { 
  RUNTIME_VERSIONS,
  getRuntimeVersion,
  getAllRuntimeDependencies,
  isRuntimeDependency 
} from '@brix-sdk/shared-runtime-web/versions';

// Get all versions
console.log(RUNTIME_VERSIONS);
// { react: '^18.2.0', 'react-dom': '^18.2.0', ... }

// Get specific version
const reactVersion = getRuntimeVersion('react'); // '^18.2.0'

// Check if package is managed
isRuntimeDependency('react'); // true
isRuntimeDependency('lodash'); // false
```

### Module Federation APIs

```typescript
import {
  getHostSharedConfig,
  getRemoteSharedConfig,
  getAdapterSharedConfig,
  mergeSharedConfig,
  getSharedPackageNames,
} from '@brix-sdk/shared-runtime-web/mf-config';

// Get shared config for Host (eager: true)
const hostConfig = getHostSharedConfig();

// Get shared config for Remote/Plugin (eager: false)
const remoteConfig = getRemoteSharedConfig();

// Merge with custom dependencies
const customConfig = mergeSharedConfig(remoteConfig, {
  'my-custom-lib': {
    singleton: true,
    requiredVersion: '^1.0.0',
    eager: false,
  },
});

// Get list of shared package names
const packages = getSharedPackageNames();
// ['react', 'react-dom', 'react-router-dom', ...]
```

### Global Injection APIs

```typescript
import {
  injectGlobals,
  checkGlobalsInjected,
  getGlobalReact,
  getGlobalReactDOM,
  clearGlobals,
} from '@brix-sdk/shared-runtime-web/globals';

// Inject React into window (Host only)
injectGlobals();

// Check injection status
if (checkGlobalsInjected()) {
  console.log('Globals available');
}

// Access global React (for debugging)
const react = getGlobalReact();
console.log('React version:', react?.version);
```

## Architecture Context

According to v3.0.7 Architecture Blueprint Constraint 8:

> "All frontend runtime dependencies (React, Router, State, UI) MUST be obtained from @brix-sdk/shared-runtime-web."

This package sits at **Layer 2B** and is consumed by:

- **Layer 1**: Plugins (business logic)
- **Layer 2C**: infra-adapters (UI adapters)  
- **Layer 3**: Host shell (provides dependencies at runtime)

### Dependency Direction

```
Layer 3 (Host)
    â†?provides runtime via eager: true
Layer 2B (shared-runtime-web) â†?Single Source of Truth
    â†?consumes via peerDependencies
Layer 2C (infra-adapters) + Layer 1 (plugins)
```

## Development

```bash
# Install dependencies
pnpm install

# Build
pnpm build

# Run tests
pnpm test

# Type check
pnpm typecheck
```

## License

Apache-2.0 - See [LICENSE](LICENSE) for details.

---

**Brix Platform Team** | v1.0.0
