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
 * @file iframe Communication Type Definitions
 * @description Message formats and interfaces for iframe cross-origin communication
 * @module @brix-sdk/infra-adapter-iframe-web/types
 * @version 3.2.0
 *
 * ¡¾v3.2 Architecture Notes¡¿
 * Common manifest/instance base contracts have been promoted to runtime-sdk-api-web.
 * This file defines iframe-specific extension types and communication protocols.
 * 
 * ¡¾Architectural Position¡¿
 * ```text
 * ©°©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©´
 * ©¦ runtime-sdk-api-web (Contract Layer)                                   ©¦
 * ©¦ ©¸©¤©¤ PluginManifest, PluginInstance base interfaces                     ©¦
 * ©À©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©È
 * ©¦ infra-adapter-iframe-web (This Module) ?                              ©¦
 * ©¦ ©¸©¤©¤ Extends iframe-specific fields (url, sandbox, bridge protocol, etc)©¦
 * ©¸©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¤©¼
 * ```
 */

import type {
  PluginManifest as BasePluginManifest,
  PluginInstance as BasePluginInstance,
} from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// Iframe Plugin Status
// ============================================================================

/**
 * Iframe Plugin Status Enum
 */
export type IframePluginStatus =
  | 'pending'    // Pending load
  | 'loading'    // Loading  
  | 'ready'      // Ready
  | 'error'      // Error
  | 'unloaded';  // Unloaded

// ============================================================================
// Iframe Plugin Manifest (extends base contract)
// ============================================================================

/**
 * Iframe Plugin Manifest
 * 
 * Extends base PluginManifest with iframe-specific configuration.
 */
export interface IframePluginManifest extends BasePluginManifest {
  /** iframe page URL */
  readonly url: string;
  /** Allowed communication origins (security configuration) */
  readonly allowedOrigins?: readonly string[];
  /** iframe sandbox attribute */
  readonly sandbox?: string;
  /** iframe width */
  readonly width?: string;
  /** iframe height */
  readonly height?: string;
}

// ============================================================================
// Iframe Plugin Instance
// ============================================================================

/**
 * Iframe Plugin Instance
 * 
 * Represents a created iframe plugin, containing DOM element reference.
 */
export interface IframePluginInstance extends BasePluginInstance<IframePluginManifest> {
  /** iframe DOM element */
  readonly iframe: HTMLIFrameElement;
  /** Current status */
  status: IframePluginStatus;
}

// ============================================================================
// Iframe Bridge Message Protocol
// ============================================================================

/**
 * Iframe Bridge Message Type Enum
 * 
 * Defines communication message types between Host and iframe plugins.
 */
export enum IframeBridgeMessageType {
  /** Initialize */
  INIT = 'BRIX:INIT',
  /** Ready confirmation */
  READY = 'BRIX:READY',
  /** Destroy */
  DESTROY = 'BRIX:DESTROY',
  /** Navigation request */
  NAV_REQUEST = 'BRIX:NAV_REQUEST',
  /** Navigation response */
  NAV_RESPONSE = 'BRIX:NAV_RESPONSE',
  /** Authentication request */
  AUTH_REQUEST = 'BRIX:AUTH_REQUEST',
  /** Authentication response */
  AUTH_RESPONSE = 'BRIX:AUTH_RESPONSE',
  /** Event emit */
  EVENT_EMIT = 'BRIX:EVENT_EMIT',
  /** Event forward */
  EVENT_FORWARD = 'BRIX:EVENT_FORWARD',
  /** State request */
  STATE_REQUEST = 'BRIX:STATE_REQUEST',
  /** State response */
  STATE_RESPONSE = 'BRIX:STATE_RESPONSE',
  /** Layout request */
  LAYOUT_REQUEST = 'BRIX:LAYOUT_REQUEST',
  /** Layout response */
  LAYOUT_RESPONSE = 'BRIX:LAYOUT_RESPONSE',
  /** Error */
  ERROR = 'BRIX:ERROR',
}

/**
 * Iframe Bridge Message Structure
 * 
 * @template T - Message payload type
 */
export interface IframeBridgeMessage<T = unknown> {
  /** Message type */
  readonly type: IframeBridgeMessageType;
  /** Message unique identifier */
  readonly messageId: string;
  /** Message payload */
  readonly payload: T;
  /** Source plugin ID */
  readonly sourcePluginId: string;
  /** Target (plugin ID or 'HOST') */
  readonly target: string | 'HOST';
  /** Timestamp */
  readonly timestamp: number;
}

/**
 * Initialize payload
 */
export interface InitPayload {
  /** Plugin ID */
  pluginId: string;
  /** Configuration options */
  config?: Record<string, unknown>;
  /** User information */
  user?: { id: string; name: string; roles: string[] };
  /** Theme */
  theme?: 'light' | 'dark';
}

/**
 * Navigation request payload
 */
export interface NavRequestPayload {
  /** Target page ID */
  pageId: string;
  /** Parameters */
  params?: Record<string, unknown>;
}

/**
 * Navigation response payload
 */
export interface NavResponsePayload {
  /** Whether successful */
  success: boolean;
  /** Failure reason */
  reason?: 'permission_denied' | 'feature_disabled' | 'page_not_found' | 'host_rejected';
}

/**
 * Event payload
 */
export interface EventPayload {
  /** Event type */
  eventType: string;
  /** Event data */
  data: unknown;
  /** Scope */
  scope: 'plugin' | 'host' | 'global';
}

/**
 * State request payload
 */
export interface StateRequestPayload {
  /** Operation type */
  operation: 'get' | 'set' | 'remove';
  /** State key */
  key: string;
  /** State value (required for set) */
  value?: unknown;
}

/**
 * State response payload
 */
export interface StateResponsePayload {
  /** Whether successful */
  success: boolean;
  /** Retrieved value */
  value?: unknown;
  /** Error message */
  error?: string;
}

/**
 * Iframe Load Error
 * 
 * Represents errors during iframe plugin loading.
 */
export class IframeLoadError extends Error {
  /** Plugin ID */
  readonly pluginId: string;
  /** Original error */
  readonly cause?: Error;
  
  constructor(pluginId: string, message: string, cause?: Error) {
    super(`[IframeLoadError] Plugin "${pluginId}" loading failed: ${message}`);
    this.name = 'IframeLoadError';
    this.pluginId = pluginId;
    this.cause = cause;
  }
}

/**
 * Iframe Bridge Communication Error
 * 
 * Represents errors during iframe plugin communication.
 */
export class IframeBridgeError extends Error {
  /** Plugin ID */
  readonly pluginId: string;
  /** Message type */
  readonly messageType: IframeBridgeMessageType;
  
  constructor(pluginId: string, messageType: IframeBridgeMessageType, message: string) {
    super(`[IframeBridgeError] Plugin "${pluginId}" communication failed (${messageType}): ${message}`);
    this.name = 'IframeBridgeError';
    this.pluginId = pluginId;
    this.messageType = messageType;
  }
}