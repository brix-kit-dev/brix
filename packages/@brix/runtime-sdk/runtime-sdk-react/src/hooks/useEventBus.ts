/**
 * @file useEventBus Hook
 * @description Event Bus Capability React Hook
 * @module @brix/runtime-sdk-react/hooks/useEventBus
 * @version 3.2.0
 *
 * [v3.2 Refactoring Notes]
 * Migrated from @brix/runtime-sdk-api-web to a standalone React binding package.
 */

import { useMemo, useEffect, useCallback, useRef } from 'react';
import type { 
  EventBusCapability,
  EventHandler 
} from '@brix/runtime-sdk-api-web';
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

  // Automatically cleanup all subscriptions on component unmount
  useEffect(() => {
    return () => {
      subscriptionsRef.current.forEach(({ eventType, handler }) => {
        eventBusCapability.off(eventType, handler);
      });
      subscriptionsRef.current = [];
    };
  }, [eventBusCapability]);

  const emit = useCallback(<T = unknown>(eventType: string, payload?: T) => {
    eventBusCapability.emit(eventType, payload);
  }, [eventBusCapability]);

  const on = useCallback(<T = unknown>(eventType: string, handler: EventHandler<T>) => {
    const typedHandler = handler as EventHandler<unknown>;
    eventBusCapability.on(eventType, typedHandler);
    subscriptionsRef.current.push({ eventType, handler: typedHandler });
  }, [eventBusCapability]);

  const off = useCallback(<T = unknown>(eventType: string, handler: EventHandler<T>) => {
    const typedHandler = handler as EventHandler<unknown>;
    eventBusCapability.off(eventType, typedHandler);
    subscriptionsRef.current = subscriptionsRef.current.filter(
      (sub) => !(sub.eventType === eventType && sub.handler === typedHandler)
    );
  }, [eventBusCapability]);

  const once = useCallback(<T = unknown>(eventType: string, handler: EventHandler<T>) => {
    const wrappedHandler: EventHandler<T> = (payload) => {
      handler(payload);
      off(eventType, wrappedHandler);
    };
    on(eventType, wrappedHandler);
  }, [on, off]);

  return {
    emit,
    on,
    off,
    once,
  };
}
