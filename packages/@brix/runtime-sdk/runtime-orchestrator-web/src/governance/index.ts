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
 * @file Governance module barrel export
 * @description Exports governance types and enforcer for Host-layer consumption.
 * @module @brix-sdk/runtime-orchestrator-web/governance
 * @version 3.2.1
 *
 * [Architecture Note]
 * GovernedEventRouter is NOT included here because it extends EventRouter
 * from platform-eventbus-web, which would add a cross-layer dependency.
 * GovernedEventRouter remains in the Host/enterprise layer as integration glue.
 */

// Types
export type {
  GovernanceConfig,
  CapabilityWhitelist,
  EventRoutingRules,
  EventRoutingRule,
  UIBudget,
} from './types';

// Enforcer
export { GovernanceEnforcer } from './GovernanceEnforcer';
export type { GovernanceViolation } from './GovernanceEnforcer';
