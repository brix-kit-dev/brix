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
 * @file Governance Enforcer
 * @description Runtime enforcement of governance policies: capability whitelist,
 *              event routing rules, and feature flags.
 * @module @brix-sdk/runtime-orchestrator-web/governance/GovernanceEnforcer
 * @version 3.2.1
 *
 * [Architecture Positioning]
 * SDK layer — runtime-orchestrator-web. Consumed by Host layer during
 * capability assembly and event bus initialization.
 *
 * [Architecture Compliance]
 * - Blueprint v3.0.9: Governance logic belongs in runtime-orchestrator, not Host
 * - Phase 2.8: Down-sunk from host-shell-standalone-web/governance/
 *
 * All plugins share one RuntimeContext, so capability-level blocking is not
 * possible without per-plugin context isolation. Instead, the enforcer logs
 * violations and provides a validation API that the Host can call at
 * capability-assembly time.
 *
 * @since 3.2.1
 * @see GovernanceConfig
 */

import type { GovernanceConfig, EventRoutingRule } from './types';

/**
 * Record of a governance policy violation for diagnostics.
 */
export interface GovernanceViolation {
  type: 'capability' | 'event-routing';
  pluginId: string;
  detail: string;
  timestamp: number;
}

/**
 * Runtime governance enforcer that validates capability access
 * and event routing against the declared governance policy.
 *
 * @example
 * ```typescript
 * import { GovernanceEnforcer } from '@brix-sdk/runtime-orchestrator-web';
 *
 * const enforcer = new GovernanceEnforcer(governanceConfig);
 * enforcer.logPolicySummary();
 *
 * if (enforcer.isCapabilityAllowed('booking', 'auth')) {
 *   // allow access
 * }
 * ```
 */
export class GovernanceEnforcer {
  private readonly config: GovernanceConfig;
  private readonly violations: GovernanceViolation[] = [];
  private readonly maxViolationLog = 500;

  constructor(config: GovernanceConfig) {
    this.config = config;
  }

  /**
   * Check whether a plugin is allowed to access a given capability.
   *
   * @param pluginId - Plugin requesting the capability
   * @param capabilityName - Capability key (e.g. 'auth', 'event-bus')
   * @returns true if allowed
   */
  isCapabilityAllowed(pluginId: string, capabilityName: string): boolean {
    const { capabilityWhitelist } = this.config;

    // Host itself is always allowed
    if (pluginId === 'host') return true;

    // Check plugin-specific whitelist, then fallback to wildcard '*'
    const pluginList = capabilityWhitelist[pluginId];
    const wildcardList = capabilityWhitelist['*'];

    const allowed = pluginList ?? wildcardList;
    if (!allowed) {
      this.recordViolation('capability', pluginId,
        `No whitelist entry for plugin "${pluginId}" and no wildcard ('*') defined. ` +
        `Denying access to capability "${capabilityName}".`);
      return false;
    }

    if (!allowed.includes(capabilityName)) {
      this.recordViolation('capability', pluginId,
        `Plugin "${pluginId}" is not whitelisted for capability "${capabilityName}". ` +
        `Allowed: [${allowed.join(', ')}].`);
      return false;
    }

    return true;
  }

  /**
   * Resolve the event routing rule for an event type.
   *
   * Matching strategy:
   * 1. Exact match on event type
   * 2. Prefix wildcard match (e.g. 'booking:*' matches 'booking:created')
   *
   * @param eventType - The event type to look up
   * @returns The matching rule or undefined if no rule applies
   */
  resolveEventRoutingRule(eventType: string): EventRoutingRule | undefined {
    const { eventRouting } = this.config;

    // Exact match first
    if (eventRouting[eventType]) {
      return eventRouting[eventType];
    }

    // Wildcard prefix match (e.g. 'booking:*' matches 'booking:created')
    for (const pattern of Object.keys(eventRouting)) {
      if (pattern.endsWith(':*')) {
        const prefix = pattern.slice(0, -1); // 'booking:'
        if (eventType.startsWith(prefix)) {
          return eventRouting[pattern];
        }
      }
    }

    return undefined;
  }

  /**
   * Validate whether an event should be delivered to a specific subscriber
   * based on event routing rules.
   *
   * @param eventType - The event type being published
   * @param sourcePluginId - The plugin that emitted the event
   * @param subscriberPluginId - The plugin that would receive the event
   * @returns true if delivery is allowed
   */
  isEventDeliveryAllowed(
    eventType: string,
    sourcePluginId: string,
    subscriberPluginId: string,
  ): boolean {
    const rule = this.resolveEventRoutingRule(eventType);

    // No routing rule → allow (default open)
    if (!rule) return true;

    if (rule.scope === 'global') {
      return true;
    }

    if (rule.scope === 'plugin') {
      const allowed = subscriberPluginId === sourcePluginId
        || subscriberPluginId === rule.target
        || subscriberPluginId === 'host';
      if (!allowed) {
        this.recordViolation('event-routing', subscriberPluginId,
          `Event "${eventType}" is plugin-scoped to "${rule.target}" ` +
          `but subscriber "${subscriberPluginId}" is not the target.`);
      }
      return allowed;
    }

    return true;
  }

  /**
   * Check whether a feature flag is enabled.
   *
   * @param featureKey - Feature flag key
   * @returns true if the feature is enabled, false otherwise
   */
  isFeatureEnabled(featureKey: string): boolean {
    return this.config.featureFlags[featureKey] ?? false;
  }

  /**
   * Get the current UI budget configuration.
   */
  getBudget() {
    return this.config.budget;
  }

  /**
   * Get recorded governance violations for diagnostics.
   */
  getViolations(): readonly GovernanceViolation[] {
    return this.violations;
  }

  /**
   * Log the active governance policy summary.
   *
   * <p>Uses {@code console.warn} (not {@code console.log}) to comply with
   * architecture red line R7 (no {@code console.log} in production code) —
   * see {@code @brix-sdk/eslint-config-architecture}.</p>
   */
  logPolicySummary(): void {
    const { capabilityWhitelist, eventRouting, budget, featureFlags } = this.config;
    const pluginCount = Object.keys(capabilityWhitelist).filter(k => k !== '*').length;
    const routeCount = Object.keys(eventRouting).length;
    const flagCount = Object.keys(featureFlags).length;

    console.warn(
      `[Governance] Policy active — ${pluginCount} plugin whitelist(s), ` +
      `${routeCount} event routing rule(s), ` +
      `bundle budget: ${Math.round(budget.maxBundleSize / 1024)}KB, ` +
      `${flagCount} feature flag(s)`
    );
  }

  private recordViolation(type: GovernanceViolation['type'], pluginId: string, detail: string): void {
    if (this.violations.length < this.maxViolationLog) {
      this.violations.push({ type, pluginId, detail, timestamp: Date.now() });
    }
    console.warn(`[Governance] ${type} violation: ${detail}`);
  }
}
