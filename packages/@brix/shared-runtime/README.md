# @brix-sdk/shared-runtime

Brix Platform Shared Runtime - Frontend Runtime Dependencies Management

## Overview

This monorepo contains shared runtime packages for the Brix Platform frontend. These packages implement **Layer 2B (Shared Runtime Layer)** of the v3.0.7 Architecture Blueprint.

## Packages

| Package | Description |
|---------|-------------|
| [@brix-sdk/shared-runtime-web](./shared-runtime-web) | Web runtime dependencies (React, Router, MUI, Zustand) |

## Purpose

In Module Federation environments, proper runtime dependency management is critical to prevent:

1. Multiple React instances causing Hook failures
2. Context fragmentation between Host and Plugins
3. Version drift leading to runtime incompatibilities
4. Bundle bloat from duplicate dependencies

## Architecture

```
┌─────────────────────────────────────────────────────────�?
�? Layer 3: Host Shell (Standalone/Embedded)              �?
�? - Uses getHostSharedConfig() with eager: true          �?
�? - Provides runtime dependencies to all plugins         �?
└─────────────────────────────────────────────────────────�?
                           �?
                           �?
┌─────────────────────────────────────────────────────────�?
�? Layer 2B: @brix-sdk/shared-runtime-web                     �?
�? - Single Source of Truth for versions                  �?
�? - MF shared configuration                              �?
�? - Runtime re-exports                                   �?
└─────────────────────────────────────────────────────────�?
                           �?
                           �?
┌─────────────────────────────────────────────────────────�?
�? Layer 1 & 2C: Plugins & Adapters                       �?
�? - Use getRemoteSharedConfig() with eager: false        �?
�? - Import from @brix-sdk/shared-runtime-web/*               �?
└─────────────────────────────────────────────────────────�?
```

## Quick Start

```bash
# Install
pnpm add @brix-sdk/shared-runtime-web

# In rspack.config.mjs (Plugin)
import { getRemoteSharedConfig } from '@brix-sdk/shared-runtime-web/mf-config';

new ModuleFederationPlugin({
  name: 'my_plugin',
  shared: getRemoteSharedConfig(),
});

# In code
import { useState } from '@brix-sdk/shared-runtime-web/react';
import { Button } from '@brix-sdk/shared-runtime-web/ui';
```

## Development

```bash
# Build all packages
pnpm build

# Test all packages
pnpm test

# Clean all packages
pnpm clean
```

## License

Apache-2.0

---

**Brix Platform Team**
