# @brix-sdk/infra-adapter-iframe-web

> Iframe integration adapter for Brix Platform

## Overview

This package provides iframe-based plugin integration for the Brix Runtime platform. It enables legacy applications or third-party widgets to be embedded as plugins within the Brix shell.

## Features

- Secure iframe sandboxing
- Cross-origin message passing
- Capability bridging to iframe content
- Size and position management
- Event forwarding

## Installation

```bash
npm install @brix-sdk/infra-adapter-iframe-web
```

## Usage

```typescript
import { IframeAdapter, createIframePlugin } from '@brix-sdk/infra-adapter-iframe-web';

// Create an iframe-based plugin
const plugin = createIframePlugin({
  src: 'https://legacy-app.example.com',
  sandbox: ['allow-scripts', 'allow-same-origin'],
  capabilities: ['navigation', 'auth']
});

// Mount the iframe plugin
adapter.mount(containerElement, plugin);
```

## Security

The adapter supports CSP-compliant sandboxing:

```typescript
const adapter = new IframeAdapter({
  sandbox: 'allow-scripts allow-forms',
  allowedOrigins: ['https://trusted.example.com']
});
```

## License

Apache-2.0
