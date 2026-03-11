---
id: shared-runtime-web
title: Shared Runtime Web
sidebar_label: shared-runtime-web
---

# @brix/shared-runtime-web

Shared React runtime for Brix plugins. Ensures single React instance across all plugins.

## Installation

```bash
pnpm add @brix/shared-runtime-web
```

## Why Shared Runtime?

When multiple plugins bundle their own React, you get "Multiple React instances" errors. Shared Runtime solves this by providing a single React source.

## Usage

**Always import React from shared-runtime:**

```typescript
// ✅ CORRECT
import { React, useState, useEffect, useCallback, useMemo } from '@brix/shared-runtime-web';

// ❌ WRONG - causes multiple instances
import React from 'react';
import { useState } from 'react';
```

## Exports

### React Core

```typescript
export { 
  React,
  useState,
  useEffect,
  useContext,
  useReducer,
  useCallback,
  useMemo,
  useRef,
  useLayoutEffect,
  useImperativeHandle,
  useDebugValue,
  useDeferredValue,
  useTransition,
  useId,
  useSyncExternalStore,
  useInsertionEffect,
} from '@brix/shared-runtime-web';
```

### React Types

```typescript
export type {
  FC,
  ReactNode,
  ReactElement,
  ComponentType,
  ComponentProps,
  PropsWithChildren,
  CSSProperties,
  ChangeEvent,
  FormEvent,
  MouseEvent,
  KeyboardEvent,
} from '@brix/shared-runtime-web';
```

### Suspense & Lazy

```typescript
export { 
  Suspense, 
  lazy,
  startTransition,
} from '@brix/shared-runtime-web';
```

### Fragments & Portals

```typescript
export {
  Fragment,
  createPortal,
} from '@brix/shared-runtime-web';
```

## Example Component

```tsx
import { 
  React, 
  useState, 
  useCallback, 
  type FC 
} from '@brix/shared-runtime-web';

interface CounterProps {
  initial?: number;
}

export const Counter: FC<CounterProps> = ({ initial = 0 }) => {
  const [count, setCount] = useState(initial);
  
  const increment = useCallback(() => {
    setCount(prev => prev + 1);
  }, []);
  
  return (
    <div>
      <span>Count: {count}</span>
      <button onClick={increment}>+</button>
    </div>
  );
};
```

## Configuration

In your plugin's build config, mark React as external:

```javascript
// vite.config.js
export default defineConfig({
  build: {
    rollupOptions: {
      external: ['react', 'react-dom'],
    },
  },
});
```

---

*Full API reference matches React 18.x documentation.*
