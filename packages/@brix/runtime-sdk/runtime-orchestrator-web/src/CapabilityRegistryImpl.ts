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
 * @file Capability Registry Implementation
 * @description Manages UI runtime capability registration, querying and lifecycle
 * @module @brix/runtime-orchestrator-web/CapabilityRegistryImpl
 * @version 3.0.0
 * 
 * Design Notes:
 * CapabilityRegistryImpl is the default implementation of the capability registry interface.
 * Responsible for capability registration, instantiation, caching and lifecycle management.
 * Supports lazy initialization, singleton pattern and dependency injection.
 * 
 * Backend Alignment:
 * Consistent with the design philosophy of backend Spring IoC container:
 * - Unified management of object instances
 * - Support for dependency injection
 * - Management of object lifecycle
 */

import type {
  CapabilityId,
  CapabilityType,
  CapabilityProvider,
  CapabilityRegisterOptions,
  CapabilityStatus,
  CapabilityRuntimeInfo,
  CapabilityRegistry,
} from '@brix/runtime-sdk-api-web';

/**
 * Capability registration entry
 * 
 * Stores capability registration information and runtime state
 */
interface CapabilityEntry<T = unknown> {
  /** Capability type identifier - can be CapabilityType object or Symbol */
  capabilityType: CapabilityType<T> | symbol;
  
  /** Capability provider */
  provider: CapabilityProvider<T>;
  
  /** Capability instance (cached) */
  instance?: T;
  
  /** Registration options */
  options: CapabilityRegisterOptions;
  
  /** Current status */
  status: CapabilityStatus;
  
  /** Registration timestamp */
  registeredAt: number;
  
  /** Initialization timestamp */
  initializedAt?: number;
  
  /** Error information */
  error?: Error;
  
  /** Invocation count */
  invocationCount: number;
}

/**
 * Capability registry default implementation
 * 
 * Provides complete lifecycle management for capabilities.
 */
export class CapabilityRegistryImpl implements CapabilityRegistry {
  /** Capability registration map */
  private readonly entries = new Map<CapabilityId, CapabilityEntry>();
  
  /** Whether initialized */
  private initialized = false;
  
  /**
   * Extract capability ID from either a Symbol or a CapabilityType object
   * 
   * This supports both:
   * - Symbol: Used directly as ID (backward compatibility)
   * - CapabilityType object: Uses the .id property
   */
  private extractId<T>(capabilityType: CapabilityType<T> | symbol): CapabilityId {
    if (typeof capabilityType === 'symbol') {
      return capabilityType;
    }
    return capabilityType.id;
  }
  
  /**
   * Register capability
   * 
   * @param capabilityType - Capability type identifier
   * @param provider - Capability provider
   * @param options - Registration options
   */
  register<T>(
    capabilityType: CapabilityType<T> | symbol,
    provider: CapabilityProvider<T>,
    options: CapabilityRegisterOptions = {}
  ): void {
    const id = this.extractId(capabilityType);
    
    // Check if already registered
    if (this.entries.has(id) && !options.override) {
      throw new Error(
        `Capability "${String(id)}" already registered. To override, set options.override = true`
      );
    }
    
    // Create registration entry
    const entry: CapabilityEntry<T> = {
      capabilityType,
      provider,
      options,
      status: 'registered',
      registeredAt: Date.now(),
      invocationCount: 0,
    };
    
    this.entries.set(id, entry as CapabilityEntry);
    
    // If not lazy initialization and overall initialization complete, initialize immediately
    if (!options.lazy && this.initialized) {
      this.initializeEntry(entry);
    }
  }
  
  /**
   * Unregister capability
   */
  unregister<T>(capabilityType: CapabilityType<T> | symbol): boolean {
    const id = this.extractId(capabilityType);
    const entry = this.entries.get(id);
    
    if (!entry) {
      return false;
    }
    
    // Dispose capability instance
    if (entry.provider.dispose) {
      try {
        entry.provider.dispose();
      } catch (error) {
        console.warn(`Error disposing capability "${String(id)}":`, error);
      }
    }
    
    this.entries.delete(id);
    return true;
  }
  
  /**
   * Get capability instance
   */
  get<T>(capabilityType: CapabilityType<T> | symbol): T | undefined {
    const id = this.extractId(capabilityType);
    const entry = this.entries.get(id) as CapabilityEntry<T> | undefined;
    
    if (!entry) {
      return undefined;
    }
    
    // On-demand initialization: initialize when requested, even if initializeAll() wasn't called
    // This ensures capabilities are ready when first accessed
    if (entry.status === 'registered') {
      this.initializeEntry(entry);
    }
    
    // Return cached instance or create new instance
    if (!entry.instance && entry.status === 'ready') {
      entry.instance = entry.provider.provide();
    }
    
    entry.invocationCount++;
    return entry.instance;
  }
  
  /**
   * Get required capability
   */
  getRequired<T>(capabilityType: CapabilityType<T> | symbol): T {
    const instance = this.get(capabilityType);
    
    if (!instance) {
      const capId = this.extractId(capabilityType);
      throw new Error(
        `Required capability "${String(capId)}" not registered or initialization failed`
      );
    }
    
    return instance;
  }
  
  /**
   * Check if capability is registered
   */
  has<T>(capabilityType: CapabilityType<T> | symbol): boolean {
    return this.entries.has(this.extractId(capabilityType));
  }
  
  /**
   * Check if capability is ready
   */
  isReady<T>(capabilityType: CapabilityType<T> | symbol): boolean {
    const entry = this.entries.get(this.extractId(capabilityType));
    return entry?.status === 'ready';
  }
  
  /**
   * Get capability runtime information
   */
  getInfo<T>(capabilityType: CapabilityType<T> | symbol): CapabilityRuntimeInfo | undefined {
    const entry = this.entries.get(this.extractId(capabilityType));
    
    if (!entry) {
      return undefined;
    }
    
    return {
      meta: entry.capabilityType,
      status: entry.status,
      registeredAt: entry.registeredAt,
      initializedAt: entry.initializedAt,
      error: entry.error,
      invocationCount: entry.invocationCount,
    };
  }
  
  /**
   * Get all registered capability IDs
   */
  getRegisteredIds(): CapabilityId[] {
    return Array.from(this.entries.keys());
  }
  
  /**
   * Get runtime information for all capabilities
   */
  getAllInfo(): Map<CapabilityId, CapabilityRuntimeInfo> {
    const result = new Map<CapabilityId, CapabilityRuntimeInfo>();
    
    for (const [id, entry] of this.entries) {
      result.set(id, {
        meta: entry.capabilityType,
        status: entry.status,
        registeredAt: entry.registeredAt,
        initializedAt: entry.initializedAt,
        error: entry.error,
        invocationCount: entry.invocationCount,
      });
    }
    
    return result;
  }
  
  /**
   * Filter capabilities by status
   */
  getByStatus(status: CapabilityStatus): CapabilityId[] {
    const result: CapabilityId[] = [];
    
    for (const [id, entry] of this.entries) {
      if (entry.status === status) {
        result.push(id);
      }
    }
    
    return result;
  }
  
  /**
   * Filter capabilities by tag
   */
  getByTag(tag: string): CapabilityId[] {
    const result: CapabilityId[] = [];
    
    for (const [id, entry] of this.entries) {
      if (entry.capabilityType.tags?.includes(tag)) {
        result.push(id);
      }
    }
    
    return result;
  }
  
  /**
   * Initialize all registered capabilities
   */
  async initializeAll(): Promise<boolean> {
    let success = true;
    
    // Initialize in registration order (simple implementation, dependency order not handled)
    for (const entry of this.entries.values()) {
      if (entry.status === 'registered' && !entry.options.lazy) {
        try {
          this.initializeEntry(entry);
        } catch (error) {
          success = false;
          console.error(`Failed to initialize capability "${this.extractId(entry.capabilityType).toString()}":`, error);
        }
      }
    }
    
    this.initialized = true;
    return success;
  }
  
  /**
   * Dispose all capabilities
   */
  async disposeAll(): Promise<void> {
    // Dispose in reverse registration order
    const entries = Array.from(this.entries.values()).reverse();
    
    for (const entry of entries) {
      if (entry.provider.dispose) {
        try {
          entry.provider.dispose();
        } catch (error) {
          console.warn(`Error disposing capability "${this.extractId(entry.capabilityType).toString()}":`, error);
        }
      }
      entry.status = 'disposed';
    }
    
    this.initialized = false;
  }
  
  /**
   * Reset registry
   */
  reset(): void {
    this.entries.clear();
    this.initialized = false;
  }
  
  /**
   * Initialize single capability entry
   */
  private initializeEntry<T>(entry: CapabilityEntry<T>): void {
    try {
      entry.status = 'initializing';
      entry.instance = entry.provider.provide();
      entry.status = 'ready';
      entry.initializedAt = Date.now();
    } catch (error) {
      entry.status = 'error';
      entry.error = error instanceof Error ? error : new Error(String(error));
      throw error;
    }
  }
}
