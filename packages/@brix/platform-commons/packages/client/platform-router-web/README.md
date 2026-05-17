# @brix-sdk/platform-router-web

> Router capability implementation for Brix Platform

## Overview

This package implements the RouterCapability interface, providing route management and contribution capabilities for the Brix Runtime platform.

## Features

- Dynamic route registration
- Plugin route contributions
- Route metadata management
- Lazy loading support
- Route matching utilities

## Installation

```bash
npm install @brix-sdk/platform-router-web
```

## Usage

```typescript
import { createRouterCapability } from '@brix-sdk/platform-router-web';

// Create router capability
const router = createRouterCapability();

// Register routes from plugins
router.registerRoutes('my-plugin', [
  {
    path: '/my-plugin',
    element: <PluginRoot />,
    children: [
      { path: 'feature', element: <Feature /> },
      { path: 'settings', element: <Settings /> }
    ]
  }
]);

// Get all registered routes
const allRoutes = router.getRoutes();

// Check if a route exists
const exists = router.hasRoute('/my-plugin/feature');
```

## Route Contributions

```typescript
// Plugin can contribute routes to existing paths
router.contributeRoute('/settings', {
  path: 'my-plugin-settings',
  element: <MyPluginSettings />
});
```

## Route Metadata

```typescript
router.registerRoutes('my-plugin', [
  {
    path: '/my-plugin',
    element: <PluginRoot />,
    meta: {
      title: 'My Plugin',
      requiresAuth: true,
      permissions: ['read', 'write']
    }
  }
]);
```

## License

Apache-2.0
