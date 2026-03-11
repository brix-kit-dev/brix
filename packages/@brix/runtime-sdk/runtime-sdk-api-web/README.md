# brix-runtime-sdk-api-web

[![npm version](https://img.shields.io/npm/v/brix-runtime-sdk-api-web.svg?style=flat-square)](https://www.npmjs.com/package/brix-runtime-sdk-api-web)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg?style=flat-square)](https://opensource.org/licenses/Apache-2.0)

**Brix Runtime SDK Web API** - UI capability contracts and shared types for the [Brix Framework](https://github.com/brix-kit-dev/brix).

This package provides framework-agnostic TypeScript type definitions and contracts for building plugins in the Brix Runtime Shell architecture.

## Features

- 🔌 **Framework Agnostic** - No dependency on React, Vue, Angular, or any UI framework
- 📦 **Pure Type Definitions** - Contains only contracts, no runtime implementations
- 🎯 **Full TypeScript Support** - Complete type safety with IntelliSense
- 🏗️ **Modular Design** - Import only what you need

## Installation

```bash
npm install brix-runtime-sdk-api-web
# or
pnpm add brix-runtime-sdk-api-web
# or
yarn add brix-runtime-sdk-api-web
```

## Capability Categories

The SDK provides contracts for the following runtime capabilities:

| Capability | Description |
|------------|-------------|
| **Navigation** | Page navigation, router management, route contributions |
| **Auth** | User identity, authentication, permission verification |
| **State** | Plugin state management and persistence |
| **EventBus** | Cross-plugin communication and event handling |
| **Config** | Runtime configuration reading |
| **Http** | Unified HTTP request handling |
| **Theme** | Theme management and customization |
| **I18n** | Internationalization support |
| **Layout** | Layout management and contribution |

## Usage

### Import Types

```typescript
import type {
  // Capabilities
  NavigationCapability,
  AuthCapability,
  StateCapability,
  EventBusCapability,
  ConfigCapability,
  HttpCapability,
  
  // Common types
  PluginDefinition,
  RouteContribution,
  MenuItem,
  
  // Context
  RuntimeContext,
} from 'brix-runtime-sdk-api-web';
```

### RuntimeContext

The `RuntimeContext` is the primary interface for plugins to access runtime capabilities:

```typescript
import type { RuntimeContext } from 'brix-runtime-sdk-api-web';

function initPlugin(context: RuntimeContext) {
  // Access navigation capability
  const navigation = context.getCapability('navigation');
  navigation.navigateTo('/dashboard');
  
  // Access auth capability
  const auth = context.getCapability('auth');
  const user = auth.getCurrentUser();
  
  // Access event bus
  const eventBus = context.getCapability('eventBus');
  eventBus.emit('plugin:ready', { pluginId: 'my-plugin' });
}
```

### Plugin Definition

Define your plugin using the `PluginDefinition` type:

```typescript
import type { PluginDefinition } from 'brix-runtime-sdk-api-web';

const myPlugin: PluginDefinition = {
  id: 'my-plugin',
  name: 'My Plugin',
  version: '1.0.0',
  routes: [
    {
      path: '/my-plugin',
      component: MyComponent,
    },
  ],
  menuItems: [
    {
      id: 'my-plugin-menu',
      label: 'My Plugin',
      path: '/my-plugin',
      icon: 'plugin-icon',
    },
  ],
};
```

## Module Structure

```
src/
├── index.ts              # Main entry point
├── context/              # RuntimeContext definitions
│   └── RuntimeContext.ts
└── types/                # Type definitions
    ├── auth.ts           # Authentication types
    ├── capability.ts     # Capability contracts
    ├── common.ts         # Common/shared types
    ├── config.ts         # Configuration types
    ├── event.ts          # Event bus types
    ├── http.ts           # HTTP client types
    ├── i18n.ts           # Internationalization types
    ├── layout.ts         # Layout types
    ├── module.ts         # Module types
    ├── navigation.ts     # Navigation types
    ├── plugin.ts         # Plugin definition types
    ├── state.ts          # State management types
    ├── theme.ts          # Theme types
    └── ui/               # UI component types
```

## Framework Bindings

For React-specific bindings (hooks, providers), use `@brix/runtime-sdk-react` instead:

```bash
npm install @brix/runtime-sdk-react
```

## Related Packages

- [`@brix/runtime-sdk-react`](https://github.com/brix-kit-dev/brix) - React bindings with hooks and providers
- [`@brix/runtime-host`](https://github.com/brix-kit-dev/brix) - Runtime host implementation

## License

[Apache License 2.0](https://opensource.org/licenses/Apache-2.0)

## Contributing

We welcome contributions! Please see our [Contributing Guide](https://github.com/brix-kit-dev/brix/blob/main/CONTRIBUTING.md) for details.
