# @brix-sdk/infra-adapter-state-web

> State management adapter for Brix Platform

## Overview

This package provides state management infrastructure for the Brix Runtime platform. It enables plugin-scoped state management with persistence and cross-plugin state sharing.

## Features

- Plugin-scoped state isolation
- State persistence (localStorage/sessionStorage)
- Cross-plugin state sharing via EventBus
- DevTools integration
- Zustand-compatible API

## Installation

```bash
npm install @brix-sdk/infra-adapter-state-web
```

## Usage

```typescript
import { StateAdapter, createPluginStore } from '@brix-sdk/infra-adapter-state-web';

// Create a plugin-scoped store
const useMyStore = createPluginStore('my-plugin', {
  count: 0,
  increment: (state) => ({ count: state.count + 1 }),
  decrement: (state) => ({ count: state.count - 1 })
});

// Use in components
function Counter() {
  const { count, increment, decrement } = useMyStore();
  
  return (
    <div>
      <span>{count}</span>
      <button onClick={increment}>+</button>
      <button onClick={decrement}>-</button>
    </div>
  );
}
```

## Persistence

```typescript
const usePersistedStore = createPluginStore('my-plugin', initialState, {
  persist: {
    key: 'my-plugin-state',
    storage: 'localStorage'
  }
});
```

## License

Apache-2.0
