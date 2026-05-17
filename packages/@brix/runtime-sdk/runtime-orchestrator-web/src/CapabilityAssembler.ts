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
 * @file Capability Assembler
 * @description Responsible for capability auto-assembly, dependency injection and initialization
 * @module @brix-sdk/runtime-orchestrator-web/CapabilityAssembler
 * @version 3.0.0
 * 
 * Design Notes:
 * CapabilityAssembler is the core component for capability assembly.
 * Similar to backend Spring's auto-wiring mechanism, it is responsible for:
 * - Analyzing capability dependencies
 * - Initializing capabilities in correct order
 * - Injecting dependencies
 * - Validating capability contracts
 * 
 * Dependency Injection Strategy:
 * 1. Constructor Injection - Inject dependencies when creating capability instances
 * 2. Property Injection - Inject dependencies via setter methods
 * 3. Context Injection - Obtain on-demand via PluginContext
 */

import type {
  CapabilityType,
  CapabilityProvider,
  CapabilityRegisterOptions,
  CapabilityId,
  CapabilityRegistry,
} from '@brix-sdk/runtime-sdk-api-web';

/**
 * Capability factory function type
 * 
 * Used to create capability instances with dependency injection support
 */
export type CapabilityFactory<T> = (deps: CapabilityFactoryDeps) => T;

/**
 * Capability factory dependencies
 * 
 * Dependencies object provided to factory functions
 */
export interface CapabilityFactoryDeps {
  /** Capability registry (for obtaining other capabilities) */
  registry: CapabilityRegistry;
  
  /** Get specified capability */
  get: <T>(capabilityType: CapabilityType<T>) => T | undefined;
  
  /** Get required capability (throws exception if not found) */
  getRequired: <T>(capabilityType: CapabilityType<T>) => T;
}

/**
 * Capability assembler configuration
 */
export interface CapabilityAssemblerConfig {
  /** Enable strict mode (stop immediately on error) */
  strictMode?: boolean;
  
  /** Whether to validate capability contracts */
  validateContracts?: boolean;
  
  /** Initialization timeout (milliseconds) */
  initTimeout?: number;
}

/**
 * Default configuration
 */
const DEFAULT_CONFIG: Required<CapabilityAssemblerConfig> = {
  strictMode: false,
  validateContracts: true,
  initTimeout: 10000,
};

/**
 * Capability assembly entry
 * 
 * Used internally to track capabilities to be assembled
 */
interface AssemblyEntry<T = unknown> {
  /** Capability type */
  capabilityType: CapabilityType<T>;
  
  /** Capability factory or provider */
  factory?: CapabilityFactory<T>;
  provider?: CapabilityProvider<T>;
  
  /** List of dependent capability IDs */
  dependencies: CapabilityId[];
  
  /** Registration options */
  options: CapabilityRegisterOptions;
  
  /** Priority (for sorting) */
  priority: number;
}

/**
 * Capability Assembler
 * 
 * Responsible for capability auto-assembly and dependency injection.
 */
export class CapabilityAssembler {
  /** Capability registry reference */
  private readonly registry: CapabilityRegistry;
  
  /** Configuration */
  private readonly config: Required<CapabilityAssemblerConfig>;
  
  /** Queue of capabilities to be assembled */
  private readonly assemblyQueue: AssemblyEntry[] = [];
  
  /** Set of assembled capability IDs */
  private readonly assembled = new Set<CapabilityId>();
  
  /**
   * Constructor
   * 
   * @param registry - Capability registry
   * @param config - Configuration options
   */
  constructor(
    registry: CapabilityRegistry,
    config: CapabilityAssemblerConfig = {}
  ) {
    this.registry = registry;
    this.config = { ...DEFAULT_CONFIG, ...config };
  }
  
  /**
   * Add capability (using factory function)
   * 
   * @param capabilityType - Capability type
   * @param factory - Capability factory function
   * @param dependencies - List of dependent capability types
   * @param options - Registration options
   */
  addWithFactory<T>(
    capabilityType: CapabilityType<T>,
    factory: CapabilityFactory<T>,
    dependencies: CapabilityType<unknown>[] = [],
    options: CapabilityRegisterOptions = {}
  ): this {
    this.assemblyQueue.push({
      capabilityType,
      factory,
      dependencies: dependencies.map(d => d.id),
      options,
      priority: options.priority ?? 0,
    } as AssemblyEntry);
    
    return this;
  }
  
  /**
   * Add capability (using provider)
   * 
   * @param capabilityType - Capability type
   * @param provider - Capability provider
   * @param options - Registration options
   */
  addWithProvider<T>(
    capabilityType: CapabilityType<T>,
    provider: CapabilityProvider<T>,
    options: CapabilityRegisterOptions = {}
  ): this {
    this.assemblyQueue.push({
      capabilityType,
      provider,
      dependencies: [],
      options,
      priority: options.priority ?? 0,
    } as AssemblyEntry);
    
    return this;
  }
  
  /**
   * Add capability (simplified form, directly provide instance)
   * 
   * @param capabilityType - Capability type
   * @param instance - Capability instance
   * @param options - Registration options
   */
  addInstance<T>(
    capabilityType: CapabilityType<T>,
    instance: T,
    options: CapabilityRegisterOptions = {}
  ): this {
    return this.addWithProvider(
      capabilityType,
      { provide: () => instance },
      options
    );
  }
  
  /**
   * Execute assembly
   * 
   * Initialize all capabilities in dependency order
   */
  async assemble(): Promise<void> {
    // Sort by priority
    const sorted = this.topologicalSort();
    
    // Create dependencies object
    const deps = this.createFactoryDeps();
    
    // Assemble in order
    for (const entry of sorted) {
      try {
        await this.assembleEntry(entry, deps);
      } catch (error) {
        console.error(
          `Failed to assemble capability "${entry.capabilityType.id}":`,
          error
        );
        
        if (this.config.strictMode) {
          throw error;
        }
      }
    }
  }
  
  /**
   * Validate assembly results
   * 
   * Check if all capabilities are correctly assembled
   */
  validate(): { success: boolean; errors: string[] } {
    const errors: string[] = [];
    
    for (const entry of this.assemblyQueue) {
      const id = entry.capabilityType.id;
      
      // Check if assembled
      if (!this.assembled.has(id)) {
        errors.push(`Capability "${id}" not assembled`);
        continue;
      }
      
      // Check if retrievable
      if (!this.registry.has(entry.capabilityType)) {
        errors.push(`Capability "${id}" assembled but not found in registry`);
        continue;
      }
      
      // Validate contract (if enabled)
      if (this.config.validateContracts) {
        const instance = this.registry.get(entry.capabilityType);
        
        if (!instance) {
          errors.push(`Capability "${id}" instance is null`);
        }
      }
    }
    
    return {
      success: errors.length === 0,
      errors,
    };
  }
  
  /**
   * Get assembly statistics
   */
  getStats(): {
    total: number;
    assembled: number;
    pending: number;
  } {
    return {
      total: this.assemblyQueue.length,
      assembled: this.assembled.size,
      pending: this.assemblyQueue.length - this.assembled.size,
    };
  }
  
  /**
   * Reset assembler state
   */
  reset(): void {
    this.assemblyQueue.length = 0;
    this.assembled.clear();
  }
  
  /**
   * Assemble single capability
   */
  private async assembleEntry(
    entry: AssemblyEntry,
    deps: CapabilityFactoryDeps
  ): Promise<void> {
    const id = entry.capabilityType.id;
    
    // Check if already assembled
    if (this.assembled.has(id)) {
      return;
    }
    
    // Check if dependencies are assembled
    for (const depId of entry.dependencies) {
      if (!this.assembled.has(depId) && !this.registry.has({ id: depId, __type: undefined as unknown })) {
        throw new Error(
          `Dependency "${depId}" of capability "${id}" not yet assembled`
        );
      }
    }
    
    // Create provider
    let provider: CapabilityProvider<unknown>;
    
    if (entry.factory) {
      // Create using factory function
      provider = {
        provide: () => entry.factory!(deps),
      };
    } else if (entry.provider) {
      // Use provider directly
      provider = entry.provider;
    } else {
      throw new Error(`Capability "${id}" has no factory or provider`);
    }
    
    // Register to registry
    this.registry.register(
      entry.capabilityType,
      provider,
      entry.options
    );
    
    this.assembled.add(id);
  }
  
  /**
   * Create factory dependencies object
   */
  private createFactoryDeps(): CapabilityFactoryDeps {
    return {
      registry: this.registry,
      get: <T>(capabilityType: CapabilityType<T>) => {
        return this.registry.get(capabilityType);
      },
      getRequired: <T>(capabilityType: CapabilityType<T>) => {
        return this.registry.getRequired(capabilityType);
      },
    };
  }
  
  /**
   * Topological sort (by dependency order)
   */
  private topologicalSort(): AssemblyEntry[] {
    const result: AssemblyEntry[] = [];
    const visited = new Set<CapabilityId>();
    const visiting = new Set<CapabilityId>();
    
    // Create ID to entry mapping
    const entryMap = new Map<CapabilityId, AssemblyEntry>();
    for (const entry of this.assemblyQueue) {
      entryMap.set(entry.capabilityType.id, entry);
    }
    
    const visit = (id: CapabilityId): void => {
      if (visited.has(id)) {
        return;
      }
      
      if (visiting.has(id)) {
        throw new Error(`Circular dependency detected: ${id}`);
      }
      
      const entry = entryMap.get(id);
      
      if (!entry) {
        // External dependency, skip
        return;
      }
      
      visiting.add(id);
      
      // Visit dependencies first
      for (const depId of entry.dependencies) {
        visit(depId);
      }
      
      visiting.delete(id);
      visited.add(id);
      result.push(entry);
    };
    
    // Visit after sorting by priority
    const sorted = [...this.assemblyQueue].sort(
      (a, b) => b.priority - a.priority
    );
    
    for (const entry of sorted) {
      visit(entry.capabilityType.id);
    }
    
    return result;
  }
}

/**
 * Create capability assembler instance
 * 
 * Factory function for convenient assembler creation
 * 
 * @param registry - Capability registry
 * @param config - Configuration options
 * @returns Capability assembler instance
 */
export function createCapabilityAssembler(
  registry: CapabilityRegistry,
  config?: CapabilityAssemblerConfig
): CapabilityAssembler {
  return new CapabilityAssembler(registry, config);
}

/**
 * Capability access whitelist type
 * 
 * Defines mapping from plugin ID to list of allowed capabilities.
 * Special key '*' represents default whitelist, applicable to all plugins without explicit configuration.
 * 
 * @example
 * ```typescript
 * const whitelist: CapabilityAccessWhitelist = {
 *   'identity-plugin': ['AuthCapability', 'HttpCapability'],
 *   'booking-plugin': ['EventBusCapability', 'StateCapability', 'HttpCapability'],
 *   '*': ['HttpCapability', 'LoggingCapability'], // Default allows all plugins to access
 * };
 * ```
 */
export type CapabilityAccessWhitelist = Record<string, string[]>;

/**
 * Validate if plugin has permission to access specified capability
 * 
 * <h2>Architectural Positioning (v3.0.4 Architectural Constraint Fix)</h2>
 * <p>
 * Migrated from host-shell-standalone-web's CapabilityAssembler to Orchestrator layer.
 * Host Layer should not contain business validation logic; validation logic belongs to Orchestrator layer responsibility.
 * </p>
 * 
 * <h2>Validation Rules</h2>
 * <ol>
 *   <li>First check plugin-specific whitelist configuration</li>
 *   <li>If no plugin-specific configuration, check default whitelist ('*' key)</li>
 *   <li>If no default whitelist, deny access</li>
 * </ol>
 * 
 * @param pluginId - Plugin ID requesting capability access
 * @param capabilityName - Capability name
 * @param whitelist - Capability access whitelist configuration
 * @returns Returns true if access allowed, false otherwise
 * 
 * @example
 * ```typescript
 * const whitelist = {
 *   'identity-plugin': ['AuthCapability', 'HttpCapability'],
 *   '*': ['HttpCapability'],
 * };
 * 
 * // true - identity-plugin can access AuthCapability
 * validateCapabilityAccess('identity-plugin', 'AuthCapability', whitelist);
 * 
 * // true - any plugin can access HttpCapability (in default whitelist)
 * validateCapabilityAccess('other-plugin', 'HttpCapability', whitelist);
 * 
 * // false - other-plugin cannot access AuthCapability
 * validateCapabilityAccess('other-plugin', 'AuthCapability', whitelist);
 * ```
 */
export function validateCapabilityAccess(
  pluginId: string,
  capabilityName: string,
  whitelist: CapabilityAccessWhitelist
): boolean {
  // First check plugin-specific whitelist configuration
  const pluginCapabilities = whitelist[pluginId];
  if (pluginCapabilities) {
    return pluginCapabilities.includes(capabilityName);
  }

  // If no plugin-specific configuration, check default whitelist
  const defaultCapabilities = whitelist['*'];
  if (defaultCapabilities) {
    return defaultCapabilities.includes(capabilityName);
  }

  // No whitelist configuration, deny access
  return false;
}
