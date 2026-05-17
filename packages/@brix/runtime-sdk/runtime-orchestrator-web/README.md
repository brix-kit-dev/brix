# @brix-sdk/runtime-orchestrator-web

> Runtime orchestration for Brix micro-frontend platform

## Overview

This package provides the core orchestration layer for the Brix Runtime, managing:
- Plugin lifecycle (load, mount, unmount)
- Capability registration and injection
- Module Federation coordination
- Runtime initialization

## Features

- Plugin lifecycle management
- Capability dependency resolution
- Dynamic plugin loading via Module Federation
- Error boundary handling
- Hot module replacement support

## Installation

```bash
npm install @brix-sdk/runtime-orchestrator-web
```

## Usage

```typescript
import { RuntimeOrchestrator } from '@brix-sdk/runtime-orchestrator-web';

// Initialize the orchestrator
const orchestrator = new RuntimeOrchestrator({
  manifestUrl: '/api/manifests',
  capabilities: [authCapability, httpCapability]
});

// Start the runtime
await orchestrator.start();

// Load a plugin dynamically
await orchestrator.loadPlugin('my-plugin');
```

## Architecture

```
+----------------------------------+
|        Shell Application         |
+----------------------------------+
|      RuntimeOrchestrator         |  <- This package
+----------------------------------+
|   Capability Registry | Plugins  |
+----------------------------------+
```

## License

Apache-2.0
