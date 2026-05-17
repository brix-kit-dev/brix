/**
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * @file useEventBus Hook
 * @description Event Bus Capability React Hook
 * @module @brix-sdk/runtime-sdk-react/hooks/useEventBus
 * @version 3.2.0
 *
 * [v3.2 Refactoring Notes]
 * Migrated from @brix-sdk/runtime-sdk-api-web to a standalone React binding package.
 */

import { useMemo, useEffect, useCallback, useRef } from 'react';
import type { 
  EventBusCapability,
  EventHandler 
} from '@brix-sdk/runtime-sdk-api-web';
import { useRuntimeContext } from './useRuntimeContext';

/**
 * Event Bus Capability Type Identifier
 * @internal
 */
const EventBusCapabilityType = Symbol.for('EventBusCapability');

/**
 * useEventBus Hook Return Type
 */
export interface UseEventBusResult {
  /** Emit event */
  emit: <T = unknown>(eventType: string, payload?: T) => void;
  /** Subscribe to event (auto cleanup) */
  on: <T = unknown>(eventType: string, handler: EventHandler<T>) => void;
  /** Unsubscribe from event */
  off: <T = unknown>(eventType: string, handler: EventHandler<T>) => void;
  /** One-time event subscription */
  once: <T = unknown>(eventType: string, handler: EventHandler<T>) => void;
}

/**
 * Get Event Bus Capability Hook
 *
 * <p>Get event bus capability instance in React components, with automatic subscription lifecycle management.</p>
 *
 * <h3>Usage Example</h3>
 * ```tsx
 * function MyComponent() {
 *   const { emit, on } = useEventBus();
 *   
 *   useEffect(() => {
 *     on('user:updated', (user) => {
 *       console.log('User updated:', user);
 *     });
 *   }, [on]);
 *   
 *   const handleClick = () => {
 *     emit('button:clicked', { id: '123' });
 *   };
 *   // ...
 * }
 * ```
 *
 * @returns UseEventBusResult event bus methods
 * @throws Error if used outside RuntimeContextProvider
 * @throws Error if event bus capability is not registered
 */
export function useEventBus(): UseEventBusResult {
  const context = useRuntimeContext();
  
  // Store all subscriptions for automatic cleanup on component unmount
  const subscriptionsRef = useRef<Array<{ eventType: string; handler: EventHandler<unknown> }>>([]);

  const eventBusCapability = useMemo(() => {
    const capability = context.getCapability<EventBusCapability>(EventBusCapabilityType);
    if (!capability) {
      throw new Error(
        '[runtime-sdk-react] EventBusCapability is not registered in RuntimeContext'
      );
    }
    return capability;
  }, [context]);

  // Stabilize capability ref to prevent dependency chain cascading
  const capabilityRef = useRef(eventBusCapability);
  capabilityRef.current = eventBusCapability;

  // Automatically cleanup all subscriptions on component unmount
  useEffect(() => {
    return () => {
      subscriptionsRef.current.forEach(({ eventType, handler }) => {
        capabilityRef.current.off(eventType, handler);
      });
      subscriptionsRef.current = [];
    };
  }, []);

  const emit = useCallback(<T = unknown>(eventType: string, payload?: T) => {
    capabilityRef.current.emit(eventType, payload);
  }, []);

  const on = useCallback(<T = unknown>(eventType: string, handler: EventHandler<T>) => {
    const typedHandler = handler as EventHandler<unknown>;
    capabilityRef.current.on(eventType, typedHandler);
    subscriptionsRef.current.push({ eventType, handler: typedHandler });
  }, []);

  const off = useCallback(<T = unknown>(eventType: string, handler: EventHandler<T>) => {
    const typedHandler = handler as EventHandler<unknown>;
    capabilityRef.current.off(eventType, typedHandler);
    subscriptionsRef.current = subscriptionsRef.current.filter(
      (sub) => !(sub.eventType === eventType && sub.handler === typedHandler)
    );
  }, []);

  const once = useCallback(<T = unknown>(eventType: string, handler: EventHandler<T>) => {
    const wrappedHandler: EventHandler<T> = (payload) => {
      handler(payload);
      off(eventType, wrappedHandler);
    };
    on(eventType, wrappedHandler);
  }, [on, off]);

  // Memoize return value for referential stability, same pattern as useHttp.
  // Prevents consumers' useEffect([eventBus]) from re-triggering on every render.
  return useMemo(() => ({
    emit,
    on,
    off,
    once,
  }), [emit, on, off, once]);
}
