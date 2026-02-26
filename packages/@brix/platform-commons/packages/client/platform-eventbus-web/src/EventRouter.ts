/**
 * @file Event Router
 * @description Responsible for event distribution, filtering, and routing
 * @module @brix/platform-eventbus-web/EventRouter
 * @version 3.0.0
 * 
 * Architecture Overview:
 * EventRouter is the core component of the event bus, responsible for:
 * 1. Event registration and distribution
 * 2. Scope-based event filtering
 * 3. Subscription management
 * 
 * Event Scopes:
 * - plugin: Propagate only within the plugin
 * - host: Cross-plugin propagation (default)
 */

import type { GovernedEvent, GovernedEventHandler, Unsubscribe } from '@brix/runtime-sdk-api-web';

/**
 * Subscription information
 */
interface Subscription {
  /**
   * Event handler function
   */
  handler: GovernedEventHandler;
  
  /**
   * Plugin ID of the subscriber
   */
  pluginId: string;
  
  /**
   * Whether to subscribe only once
   */
  once: boolean;
}

/**
 * Event Router
 * 
 * Responsible for event distribution and routing.
 * 
 * Usage Example:
 * ```typescript
 * const router = new EventRouter();
 * 
 * // Subscribe to event
 * const unsubscribe = router.subscribe('booking:created', (event) => {
 *   console.log('Received event:', event);
 * }, 'notification');
 * 
 * // Publish event
 * router.publish({
 *   type: 'booking:created',
 *   payload: { id: '123' },
 *   metadata: {
 *     sourcePlugin: 'booking',
 *     scope: 'host',
 *     traceId: 'trace-123',
 *     timestamp: Date.now(),
 *   }
 * });
 * ```
 */
export class EventRouter {
  /**
   * Event subscription table
   * key: Event type
   * value: Subscription info set
   */
  private subscriptions: Map<string, Set<Subscription>> = new Map();
  
  /**
   * Wildcard subscriptions (subscribe to all events)
   */
  private wildcardSubscriptions: Set<Subscription> = new Set();
  
  /**
   * Subscribe to event
   * 
   * @typeParam T - Event payload type
   * @param eventType - Event type (supports * wildcard)
   * @param handler - Event handler function
   * @param pluginId - Subscriber's plugin ID
   * @param once - Whether to subscribe only once
   * @returns Unsubscribe function
   */
  subscribe<T = unknown>(
    eventType: string,
    handler: GovernedEventHandler<T>,
    pluginId: string,
    once: boolean = false
  ): Unsubscribe {
    const subscription: Subscription = {
      handler: handler as GovernedEventHandler,
      pluginId,
      once,
    };
    
    if (eventType === '*') {
      // Wildcard subscription
      this.wildcardSubscriptions.add(subscription);
      return () => {
        this.wildcardSubscriptions.delete(subscription);
      };
    }
    
    // Specific event subscription
    if (!this.subscriptions.has(eventType)) {
      this.subscriptions.set(eventType, new Set());
    }
    
    this.subscriptions.get(eventType)!.add(subscription);
    
    return () => {
      this.subscriptions.get(eventType)?.delete(subscription);
    };
  }
  
  /**
   * Publish event
   * 
   * @typeParam T - Event payload type
   * @param event - Event object
   */
  publish<T = unknown>(event: GovernedEvent<T>): void {
    const { type } = event;
    
    // Collect subscriptions to notify
    const toNotify: Subscription[] = [];
    const toRemove: { set: Set<Subscription>; sub: Subscription }[] = [];
    
    // 1. Handle specific event type subscriptions
    const typeSubscriptions = this.subscriptions.get(type);
    if (typeSubscriptions) {
      for (const sub of typeSubscriptions) {
        if (this.shouldNotify(sub, event)) {
          toNotify.push(sub);
          
          if (sub.once) {
            toRemove.push({ set: typeSubscriptions, sub });
          }
        }
      }
    }
    
    // 2. Handle wildcard subscriptions
    for (const sub of this.wildcardSubscriptions) {
      if (this.shouldNotify(sub, event)) {
        toNotify.push(sub);
        
        if (sub.once) {
          toRemove.push({ set: this.wildcardSubscriptions, sub });
        }
      }
    }
    
    // 3. Execute notifications
    for (const sub of toNotify) {
      try {
        sub.handler(event as GovernedEvent);
      } catch (error) {
        console.error(`[EventRouter] Event handler execution error (${type}):`, error);
      }
    }
    
    // 4. Clean up one-time subscriptions
    for (const { set, sub } of toRemove) {
      set.delete(sub);
    }
  }
  
  /**
   * Determine whether to notify subscriber
   * 
   * @param subscription - Subscription info
   * @param event - Event object
   * @returns Whether to notify
   */
  private shouldNotify(subscription: Subscription, event: GovernedEvent): boolean {
    const { metadata } = event;
    const { scope, source } = metadata;
    const { pluginId } = subscription;
    
    // plugin scope: only notify subscribers from the same plugin
    if (scope === 'plugin') {
      return pluginId === source;
    }
    
    // host scope: notify all subscribers
    return true;
  }
  
  /**
   * Check if there are subscribers
   * 
   * @param eventType - Event type
   * @returns Whether there are subscribers
   */
  hasSubscribers(eventType: string): boolean {
    if (this.wildcardSubscriptions.size > 0) {
      return true;
    }
    
    const typeSubscriptions = this.subscriptions.get(eventType);
    return typeSubscriptions ? typeSubscriptions.size > 0 : false;
  }
  
  /**
   * Get subscriber count
   * 
   * @param eventType - Event type (optional, returns total if not provided)
   * @returns Subscriber count
   */
  getSubscriberCount(eventType?: string): number {
    if (eventType) {
      const typeSubscriptions = this.subscriptions.get(eventType);
      return (typeSubscriptions?.size ?? 0) + this.wildcardSubscriptions.size;
    }
    
    let total = this.wildcardSubscriptions.size;
    for (const subs of this.subscriptions.values()) {
      total += subs.size;
    }
    return total;
  }
  
  /**
   * Clear all subscriptions for a specific plugin
   * 
   * @param pluginId - Plugin ID
   */
  clearByPlugin(pluginId: string): void {
    // Clear specific event subscriptions
    for (const subs of this.subscriptions.values()) {
      for (const sub of subs) {
        if (sub.pluginId === pluginId) {
          subs.delete(sub);
        }
      }
    }
    
    // Clear wildcard subscriptions
    for (const sub of this.wildcardSubscriptions) {
      if (sub.pluginId === pluginId) {
        this.wildcardSubscriptions.delete(sub);
      }
    }
  }
  
  /**
   * Clear all subscriptions
   */
  clear(): void {
    this.subscriptions.clear();
    this.wildcardSubscriptions.clear();
  }
}

export type { Unsubscribe };
