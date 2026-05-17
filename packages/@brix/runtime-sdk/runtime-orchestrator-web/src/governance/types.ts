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
 * @file Governance Types
 * @description Type definitions for the runtime governance system.
 *              Defines capability whitelists, event routing rules,
 *              UI budgets, and feature flags.
 * @module @brix-sdk/runtime-orchestrator-web/governance
 * @version 3.2.1
 *
 * [Architecture Compliance]
 * - Blueprint v3.0.9: Governance belongs in runtime-orchestrator, not Host layer
 * - Phase 2.8: Governance types and enforcer down-sunk from Host to SDK layer
 *
 * @since 3.2.1
 */

/**
 * Complete governance configuration for the runtime.
 *
 * Defines policies for capability access, event routing,
 * resource budgets, and feature flags.
 */
export interface GovernanceConfig {
  /** Per-plugin capability access whitelist. Key '*' defines the default. */
  capabilityWhitelist: CapabilityWhitelist;
  /** Event routing rules keyed by event type or pattern. */
  eventRouting: EventRoutingRules;
  /** UI resource budget limits. */
  budget: UIBudget;
  /** Feature flags for conditional feature enablement. */
  featureFlags: Record<string, boolean>;
}

/** Maps plugin IDs (or '*' wildcard) to allowed capability names. */
export type CapabilityWhitelist = Record<string, string[]>;

/** Maps event types (or 'prefix:*' patterns) to routing rules. */
export type EventRoutingRules = Record<string, EventRoutingRule>;

/**
 * Defines how events of a given type are routed.
 *
 * - 'global': Event is delivered to all subscribers.
 * - 'plugin': Event is scoped to a specific target plugin (plus source and host).
 */
export interface EventRoutingRule {
  scope: 'global' | 'plugin';
  /** Target plugin ID when scope is 'plugin'. */
  target?: string;
}

/**
 * UI resource budget limits for governance enforcement.
 */
export interface UIBudget {
  /** Maximum bundle size in bytes. */
  maxBundleSize: number;
  /** Maximum memory usage in bytes. */
  maxMemory: number;
  /** Maximum API calls per minute. */
  maxApiCallsPerMinute: number;
}
