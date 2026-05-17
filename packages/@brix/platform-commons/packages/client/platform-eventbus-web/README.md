# @brix-sdk/platform-eventbus-web

> Event bus capability implementation for Brix Platform

## Overview

This package implements the EventBusCapability interface, providing cross-plugin event communication for the Brix Runtime platform.

## Features

- Pub/Sub event system
- Typed event payloads
- Wildcard subscriptions
- Event history and replay
- Plugin-scoped events

## Installation

```bash
npm install @brix-sdk/platform-eventbus-web
```

## Usage

```typescript
import { createEventBusCapability } from '@brix-sdk/platform-eventbus-web';

// Create event bus capability
const eventBus = createEventBusCapability();

// Subscribe to events
const unsubscribe = eventBus.on('user:login', (payload) => {
  console.log('User logged in:', payload.userId);
});

// Publish events
eventBus.emit('user:login', { userId: '123', timestamp: Date.now() });

// Unsubscribe when done
unsubscribe();
```

## Typed Events

```typescript
interface AppEvents {
  'user:login': { userId: string; timestamp: number };
  'user:logout': { userId: string };
  'cart:update': { items: CartItem[] };
}

const eventBus = createEventBusCapability<AppEvents>();

// TypeScript will enforce correct payload types
eventBus.emit('user:login', { userId: '123', timestamp: Date.now() });
```

## Wildcard Subscriptions

```typescript
// Subscribe to all user events
eventBus.on('user:*', (event, payload) => {
  console.log(`User event: ${event}`, payload);
});
```

## License

Apache-2.0
