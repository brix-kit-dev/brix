# @brix-sdk/runtime-manifest-web

> Runtime manifest handling for Brix Platform

## Overview

This package provides manifest parsing and handling capabilities for the Brix Runtime platform. It manages plugin manifests, route definitions, and capability declarations.

## Features

- Manifest parsing and validation
- Plugin route registration
- Capability declaration handling
- Module Federation remote entry management

## Installation

```bash
npm install @brix-sdk/runtime-manifest-web
```

## Usage

```typescript
import { ManifestLoader, parseManifest } from '@brix-sdk/runtime-manifest-web';

// Load and parse plugin manifest
const manifest = await ManifestLoader.load('/plugins/my-plugin/manifest.json');

// Access manifest properties
console.log(manifest.name);
console.log(manifest.routes);
console.log(manifest.capabilities);
```

## Manifest Structure

```json
{
  "name": "my-plugin",
  "version": "1.0.0",
  "remoteEntry": "./remoteEntry.js",
  "routes": [
    {
      "path": "/my-feature",
      "component": "./pages/MyFeature"
    }
  ],
  "capabilities": {
    "required": ["auth", "http"],
    "optional": ["analytics"]
  }
}
```

## License

Apache-2.0
