/**
 * @file infra-adapter-state-web module entry
 * @description Brix UI State Management Adapter - Zustand-based plugin state isolation implementation
 * @module @brix/infra-adapter-state-web
 * @version 3.0.0
 * 
 * [Module Description]
 * This module is the state management adapter layer in the v3.0 Runtime Shell architecture.
 * It encapsulates Zustand to implement namespace isolation for plugin state.
 * 
 * [Architectural Position]
 * - This module is an internal dependency of the Host layer
 * - Plugins should not use this module directly
 * - Plugins operate state through PluginStateCapability
 * 
 * [v3.0 Architectural Constraints (Red Lines)]
 * ❌ Plugins MUST NOT create global stores
 * ❌ Plugins MUST NOT access other plugins' state
 * ❌ Plugins MUST NOT use localStorage directly
 * ✅ Plugins operate isolated state through PluginStateCapability
 * ✅ Cross-plugin state sharing is done through EventBus
 * 
 * [Usage] (Host layer only)
 * ```typescript
 * import { ZustandAdapter } from '@brix/infra-adapter-state-web';
 * 
 * const adapter = new ZustandAdapter({
 *   onStateChange: (event) => console.log(event),
 * });
 * 
 * adapter.set('booking', 'selectedDate', new Date());
 * ```
 */

export { 
  ZustandAdapter,
  type PluginStoreState,
  type StateChangeEvent,
  type StateChangeListener,
  type ZustandAdapterOptions,
} from './ZustandAdapter';

// ========== Version Info ==========
export const VERSION = '3.0.0';
