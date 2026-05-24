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
 * @file Layout State Type Definitions
 * @description Defines layout state and configuration
 * @module @brix-sdk/platform-frame-web/types
 * @version 3.0.0
 */

import type { LayoutState, LayoutChangeEvent, LayoutRequestResult } from '@brix-sdk/runtime-sdk-api-web';

export type LayoutChangeSet = Partial<{
  fullscreen: boolean;
  sidebarVisible: boolean;
  sidebarCollapsed: boolean;
  headerVisible: boolean;
  footerVisible: boolean;
}>;

/**
 * Layout Configuration
 */
export interface LayoutConfig {
  /**
   * Default sidebar visibility
   * @default true
   */
  defaultSidebarVisible?: boolean;
  
  /**
   * Default sidebar collapsed state
   * @default false
   */
  defaultSidebarCollapsed?: boolean;
  
  /**
   * Default header visibility
   * @default true
   */
  defaultHeaderVisible?: boolean;
  
  /**
   * Default footer visibility
   * @default true
   */
  defaultFooterVisible?: boolean;
  
  /**
   * Layout mode
   * @default 'console'
   */
  layoutMode?: 'console' | 'portal' | 'minimal';
  
  /**
   * Breakpoint configuration
   */
  breakpoints?: {
    xs?: number;  // < 576px
    sm?: number;  // >= 576px
    md?: number;  // >= 768px
    lg?: number;  // >= 992px
    xl?: number;  // >= 1200px
    xxl?: number; // >= 1600px
  };
  
  /**
   * Sidebar width (expanded state)
   * @default 256
   */
  sidebarWidth?: number;
  
  /**
   * Sidebar width (collapsed state)
   * @default 80
   */
  sidebarCollapsedWidth?: number;
  
  /**
   * Header height
   * @default 64
   */
  headerHeight?: number;
  
  /**
   * Footer height
   * @default 0
   */
  footerHeight?: number;
  
  /**
   * Governance policy
   */
  governancePolicy?: LayoutGovernancePolicy;
}

/**
 * Layout Governance Policy
 * 
 * Determines what layout changes plugins can request from Host
 */
export interface LayoutGovernancePolicy {
  /**
   * Allow plugins to request fullscreen
   * @default true
   */
  allowFullscreen?: boolean;
  
  /**
   * Allow plugins to request hide sidebar
   * @default true
   */
  allowHideSidebar?: boolean;
  
  /**
   * Allow plugins to request hide header
   * @default false
   */
  allowHideHeader?: boolean;
  
  /**
   * Allowed plugin ID list (whitelist)
   * If empty, all plugins are allowed
   */
  allowedPlugins?: string[];
  
  /**
   * Blocked plugin ID list (blacklist)
   */
  blockedPlugins?: string[];
}

/**
 * Layout Change Request
 */
export interface LayoutChangeRequest {
  /**
   * Request type
   */
  type: 'fullscreen' | 'sidebar' | 'header' | 'batch';
  
  /**
   * Requesting plugin ID
   */
  pluginId: string;
  
  /**
   * Requested changes
   */
  changes: LayoutChangeSet;
  
  /**
   * Request timestamp
   */
  timestamp: number;
}

// Re-export types from SDK
export type { LayoutState, LayoutChangeEvent, LayoutRequestResult };
