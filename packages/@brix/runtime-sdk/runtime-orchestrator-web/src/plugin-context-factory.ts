/**
 * @file Plugin Context Factory
 * @description Factory functions for creating plugin runtime context
 * @module @brix/runtime-orchestrator-web/plugin-context-factory
 * @version 3.0.0
 * 
 * [v3.2 Extracted Module]
 * Extracted from PluginManager.ts to reduce file size.
 * The PluginContext provides plugins with controlled access to:
 * - Capability registration and lookup
 * - Event publishing and subscription
 * - Plugin metadata
 * 
 * 【中文技术要点】
 * 插件上下文工厂，为插件提供沙箱化的能力访问接口。
 * 通过 recordContribution 回调追踪插件注册的资源，支持插件卸载时自动清理。
 */

import type {
  PluginEntry,
  PluginContext,
  CapabilityRegistry,
} from '@brix/runtime-sdk-api-web';
import { EventBusCapabilityType } from '@brix/runtime-sdk-api-web';
import type { PluginContribution } from './plugin-manager-types';

/**
 * Dependencies required by createPluginContext
 */
export interface PluginContextDependencies {
  /** Capability registry for accessing/registering capabilities */
  registry: CapabilityRegistry;
  /** Callback to record plugin contributions for cleanup tracking */
  recordContribution: (pluginId: string, contribution: PluginContribution) => void;
}

/**
 * Create plugin context
 * 
 * Creates a sandboxed context object that plugins use to interact with
 * the runtime. Provides controlled access to capabilities and events.
 * 
 * @param entry - Plugin entry configuration
 * @param deps - Dependencies (registry, recordContribution)
 * @returns PluginContext for plugin activation
 * 
 * @example
 * ```typescript
 * const context = createPluginContext(entry, {
 *   registry: this.registry,
 *   recordContribution: (id, contrib) => this.recordContribution(id, contrib)
 * });
 * ```
 */
export function createPluginContext(
  entry: PluginEntry,
  deps: PluginContextDependencies
): PluginContext {
  const { registry, recordContribution } = deps;
  
  return {
    // Plugin meta information
    pluginId: entry.id,
    pluginVersion: entry.version,
    
    // Capability access
    getCapability: <T>(capabilityType: { id: string }): T | undefined => {
      return registry.get(capabilityType as { id: string; __type: T }) as T | undefined;
    },
    
    // Capability registration
    registerCapability: <T>(
      capabilityType: { id: string },
      provider: { provide: () => T }
    ): void => {
      registry.register(
        capabilityType as { id: string; __type: T },
        provider as { provide: () => T }
      );
      
      // Record contribution for cleanup tracking
      recordContribution(entry.id, {
        type: 'capability',
        id: capabilityType.id,
        cleanup: () => {
          registry.unregister(capabilityType as { id: string; __type: T });
        },
      });
    },
    
    // Event publishing
    emit: (eventType, payload): void => {
      const eventBus = registry.get(EventBusCapabilityType);
      
      if (eventBus) {
        eventBus.emit({
          type: eventType,
          payload,
          source: entry.id,
          timestamp: Date.now(),
        });
      }
    },
    
    // Event subscription
    subscribe: <T>(eventType: string, handler: (payload: T) => void) => {
      const eventBus = registry.get(EventBusCapabilityType);
      
      if (eventBus) {
        eventBus.on(eventType, handler);
        
        // Record contribution for cleanup tracking
        recordContribution(entry.id, {
          type: 'eventHandler',
          id: `${eventType}-${Date.now()}`,
          cleanup: () => {
            eventBus.off(eventType, handler);
          },
        });
      }
      
      return () => {
        eventBus?.off(eventType, handler);
      };
    },
  };
}
