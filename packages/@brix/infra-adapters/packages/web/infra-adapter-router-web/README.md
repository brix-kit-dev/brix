# @brix-sdk/infra-adapter-router-web

> Router adapter for Brix Platform

## Overview

This package provides routing infrastructure for the Brix Runtime platform. It bridges the runtime's navigation capability with react-router-dom.

## Features

- Route registration from plugins
- Dynamic route loading
- Route guards and middleware
- Nested routing support
- History management

## Installation

```bash
npm install @brix-sdk/infra-adapter-router-web
```

## Usage

```typescript
import { RouterAdapter, createBrixRouter } from '@brix-sdk/infra-adapter-router-web';

// Create router with Brix integration
const router = createBrixRouter({
  routes: [
    { path: '/', element: <Home /> },
    { path: '/dashboard', element: <Dashboard /> }
  ],
  plugins: pluginRoutes
});

// Use in your app
function App() {
  return <RouterProvider router={router} />;
}
```

## Plugin Route Registration

```typescript
// In your plugin
const pluginRoutes = [
  {
    path: '/my-plugin/*',
    element: <PluginLayout />,
    children: [
      { path: 'feature', element: <Feature /> }
    ]
  }
];
```

## Peer Dependencies

```json
{
  "react-router-dom": ">=6.0.0"
}
```

## License

Apache-2.0
