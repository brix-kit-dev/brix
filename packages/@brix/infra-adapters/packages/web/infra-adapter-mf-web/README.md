# @brix-sdk/infra-adapter-mf-web

> Module Federation adapter for Brix Platform

## Overview

This package provides Module Federation integration for the Brix Runtime platform. It enables dynamic loading of remote plugins using Webpack's Module Federation.

## Features

- Dynamic remote module loading
- Shared dependency management
- Version negotiation
- Fallback handling
- Hot reload support

## Installation

```bash
npm install @brix-sdk/infra-adapter-mf-web
```

## Usage

```typescript
import { ModuleFederationAdapter, loadRemoteModule } from '@brix-sdk/infra-adapter-mf-web';

// Load a remote module
const RemoteComponent = await loadRemoteModule({
  remoteEntry: 'https://plugins.example.com/my-plugin/remoteEntry.js',
  scope: 'my_plugin',
  module: './App'
});

// Use with adapter
const adapter = new ModuleFederationAdapter({
  shared: {
    react: { singleton: true, requiredVersion: '^18.0.0' },
    'react-dom': { singleton: true, requiredVersion: '^18.0.0' }
  }
});

await adapter.loadPlugin('my-plugin');
```

## Configuration

```typescript
// rspack.config.js or webpack.config.js
const { ModuleFederationPlugin } = require('@module-federation/enhanced');

module.exports = {
  plugins: [
    new ModuleFederationPlugin({
      name: 'shell',
      shared: ['react', 'react-dom']
    })
  ]
};
```

## License

Apache-2.0
