# @brix-sdk/runtime-sdk-react

> React bindings for Brix Runtime SDK - Hooks and Context providers

## Overview

This package provides React-specific bindings for the Brix Runtime SDK, including:
- React Context for RuntimeContext
- Pre-built hooks for all capabilities (auth, http, navigation, etc.)
- Type-safe integration with React components

## Installation

```bash
npm install @brix-sdk/runtime-sdk-react
```

## Usage

### Context Provider

```tsx
import { RuntimeContextProvider } from '@brix-sdk/runtime-sdk-react';

function App() {
  return (
    <RuntimeContextProvider value={runtimeContext}>
      <YourApp />
    </RuntimeContextProvider>
  );
}
```

### Hooks

```tsx
import {
  useAuth,
  useHttp,
  useNavigation,
  useConfig,
  useEventBus,
  usePluginState,
  useTheme,
  useLayout,
  useUI
} from '@brix-sdk/runtime-sdk-react';

function MyComponent() {
  const { isAuthenticated, user, login, logout } = useAuth();
  const http = useHttp();
  const { navigate } = useNavigation();
  const config = useConfig();

  // Use capabilities...
}
```

## Available Hooks

| Hook | Description |
|------|-------------|
| `useRuntimeContext` | Access the full runtime context |
| `useAuth` | Authentication state and methods |
| `useHttp` | HTTP client for API requests |
| `useHttpRequest` | HTTP request with loading/error state |
| `useNavigation` | Navigation and routing |
| `useConfig` | Runtime configuration |
| `useEventBus` | Cross-plugin event communication |
| `usePluginState` | Plugin-scoped state management |
| `usePluginLoader` | Dynamic plugin loading |
| `useTheme` | Theme customization |
| `useLayout` | Layout configuration |
| `useUI` | UI component adapter |
| `useResponsive` | Responsive breakpoint detection |

## Peer Dependencies

```json
{
  "react": ">=18.0.0",
  "brix-runtime-sdk-api-web": "^1.0.0"
}
```

## License

Apache-2.0
