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
 * @file usePluginLifecycle Hook
 * @description Plugin lifecycle phase management and state tracking.
 * @module @brix-sdk/runtime-orchestrator-web/hooks/usePluginLifecycle
 * @version 3.2.0
 *
 * Architectural Positioning:
 * Extracted from the monolithic usePluginSystem hook (P2-2 — Blueprint v3.0.9).
 * Maps discovery sub-phases to the canonical plugin lifecycle model defined
 * in §4 of the Architecture Blueprint:
 *
 *   idle → discovering → loading → activating → running → (error)
 *
 * This module contains only lifecycle enum types, state derivation logic, and
 * the React hook that translates discovery progress into lifecycle phases.
 * No infrastructure dependencies.
 *
 * @see usePluginSystem — façade hook
 * @see usePluginDiscovery — supplies the discovery phase input
 */

import { useMemo } from 'react';

// ============================================================================
// Types
// ============================================================================

/**
 * Canonical lifecycle phase for the plugin system.
 *
 * Maps directly to Blueprint §4 Plugin Lifecycle:
 *   idle → discovering → loading → activating → running → error
 */
export type PluginSystemLifecyclePhase =
  | 'idle'        // Not started
  | 'discovering' // Discovering backend + local plugins
  | 'loading'     // Fetching manifests
  | 'activating'  // Marking plugins as ready
  | 'running'     // Steady state
  | 'error';      // Fatal error

/**
 * Individual plugin state tracked during and after discovery.
 */
export interface PluginState {
  /** Plugin status within its own lifecycle */
  status: 'registered' | 'loading' | 'loaded' | 'active' | 'error';
  /** Epoch timestamp when the plugin was activated */
  activatedAt?: number;
  /** Aggregate health indicator */
  healthStatus?: 'healthy' | 'degraded' | 'unhealthy';
}

/**
 * Input from usePluginDiscovery that drives lifecycle phase transitions.
 */
export interface LifecycleInput {
  /** Whether discovery is still in progress */
  loading: boolean;
  /** Discovery error message, if any */
  error: string | null;
  /** Sub-phase reported by the discovery hook */
  discoveryPhase: 'idle' | 'discovering' | 'loading' | 'done' | 'error';
}

// ============================================================================
// Hook Implementation
// ============================================================================

/**
 * Derive the canonical lifecycle phase from discovery state.
 *
 * The mapping is intentionally simple:
 *   discoveryPhase='idle'        → 'idle'
 *   discoveryPhase='discovering' → 'discovering'
 *   discoveryPhase='loading'     → 'loading'
 *   discoveryPhase='done'        → 'running'  (with a transient 'activating' when first resolved)
 *   discoveryPhase='error'       → 'error'
 *
 * The "activating" phase was previously an explicit state transition in the
 * monolithic hook. After the split, it is a logical transition that occurs
 * instantaneously when discovery completes — the consuming code can still
 * observe 'running' to know that activation has finished.
 *
 * @param input - Current discovery state
 * @returns The canonical lifecycle phase
 */
export function usePluginLifecycle(input: LifecycleInput): PluginSystemLifecyclePhase {
  const { loading, error, discoveryPhase } = input;

  return useMemo<PluginSystemLifecyclePhase>(() => {
    // Discovery sub-phases map directly
    if (discoveryPhase === 'idle') return 'idle';
    if (discoveryPhase === 'discovering') return 'discovering';
    if (discoveryPhase === 'loading') return 'loading';

    // When discovery finished (done) — enter running.
    // Even if there was a non-fatal error (e.g., one plugin failed),
    // the system is still considered "running" with degraded state.
    if (discoveryPhase === 'done' && !loading) return 'running';

    // Still loading after "done" shouldn't happen, but guard defensively
    if (loading) return 'loading';

    // Fatal discovery error
    if (error) return 'error';

    return 'running';
  }, [loading, error, discoveryPhase]);
}
