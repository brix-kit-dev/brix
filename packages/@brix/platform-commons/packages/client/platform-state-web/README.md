# @brix-sdk/platform-state-web

> State management capability implementation for Brix Platform

## Overview

This package implements the PluginStateCapability interface, providing plugin-scoped state management for the Brix Runtime platform.

## Features

- Plugin-isolated state
- Type-safe state access
- State persistence options
- State synchronization
- DevTools integration

## Installation

```bash
npm install @brix-sdk/platform-state-web
```

## Usage

```typescript
import { createStateCapability } from '@brix-sdk/platform-state-web';

// Create state capability
const stateCapability = createStateCapability();

// Get or create plugin state
const state = stateCapability.getPluginState('my-plugin', {
  count: 0,
  items: []
});

// Update state
state.setState({ count: state.getState().count + 1 });

// Subscribe to changes
const unsubscribe = state.subscribe((newState) => {
  console.log('State changed:', newState);
});
```

## Persistence

```typescript
const state = stateCapability.getPluginState('my-plugin', initialState, {
  persist: true,
  storageKey: 'my-plugin-state'
});

// State is automatically saved to localStorage
```

## Selectors

```typescript
// Subscribe to specific state slice
state.subscribe(
  (state) => state.items,
  (items) => {
    console.log('Items changed:', items);
  }
);
```

## License

Apache-2.0
