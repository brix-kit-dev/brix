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
 * @file Menu Icon Mapping
 * @description Provides mapping from menu icon names to Emoji
 * @module @brix-sdk/platform-frame-web/layouts/components/menuIcons
 * @version 3.2.0
 *
 * [Design Notes]
 * This module provides simple icon mapping to convert icon names from Manifest to displayable Emoji.
 * In production environments, consider replacing with Ant Design Icons or other icon libraries.
 *
 * [Extension Methods]
 * 1. Replace with Ant Design Icons: import { DashboardOutlined } from '@ant-design/icons'
 * 2. Replace with SVG icon components
 * 3. Replace with Icon Font
 */

// ============================================================================
// Icon Mapping Table
// ============================================================================

/**
 * Icon name to Emoji mapping
 * 
 * [Notes]
 * This is a fallback implementation when UIAdapter Icon component is not available.
 * Maps icon names from ui-manifest.json to Emoji for basic display.
 */
const ICON_MAP: Record<string, string> = {
  // Navigation & Menu
  dashboard: '📊',
  home: '🏠',
  settings: '⚙️',
  config: '🔧',
  menu: '☰',
  apps: '📱',
  
  // User & Auth
  user: '👤',
  users: '👥',
  person: '👤',
  group: '👥',
  person_add: '➕👤',
  person_remove: '➖👤',
  identity: '🔐',
  profile: '📋',
  security: '🔒',
  lock: '🔒',
  lock_open: '🔓',
  vpn_key: '🔑',
  login: '🔐',
  logout: '🚪',
  account_circle: '👤',
  verified: '✅',
  
  // Business functions
  product: '📦',
  products: '📦',
  shopping_cart: '🛒',
  inventory: '📦',
  partner: '🤝',
  partners: '🤝',
  business: '🏢',
  work: '💼',
  account_balance: '🏛️',
  payment: '💳',
  local_shipping: '🚚',
  receipt: '🧾',
  
  // Calendar & Events
  booking: '📅',
  calendar: '📅',
  calendar_today: '📅',
  event: '📅',
  access_time: '⏰',
  
  // Communication
  message: '💬',
  messenger: '💬',
  email: '📧',
  notifications: '🔔',
  phone: '📞',
  chat: '💬',
  forum: '💬',
  support_agent: '🎧',
  
  // Files and content
  file: '📄',
  files: '📂',
  document: '📝',
  carousel: '🖼️',
  image: '🖼️',
  folder: '📁',
  file_open: '📂',
  description: '📝',
  attach_file: '📎',
  videocam: '📹',
  mic: '🎤',
  
  // Actions
  add: '➕',
  edit: '✏️',
  delete: '🗑️',
  save: '💾',
  cancel: '❌',
  close: '✖️',
  check: '✓',
  search: '🔍',
  refresh: '🔄',
  sync: '🔄',
  filter_list: '🔍',
  sort: '↕️',
  share: '📤',
  link: '🔗',
  copy: '📋',
  print: '🖨️',
  
  // Status
  error: '❌',
  warning: '⚠️',
  info: 'ℹ️',
  help: '❓',
  help_outline: '❓',
  check_circle: '✅',
  task_alt: '✅',
  announcement: '📢',
  campaign: '📣',
  
  // Views & Layout
  view_list: '📋',
  view_module: '🔲',
  grid_on: '⊞',
  table_chart: '📊',
  
  // Charts & Data
  assessment: '📈',
  bar_chart: '📊',
  pie_chart: '🥧',
  timeline: '📈',
  trending_up: '📈',
  trending_down: '📉',
  
  // Contracts
  contract: '📑',
  contracts: '📑',
  
  // Cloud & Storage
  cloud: '☁️',
  cloud_upload: '⬆️☁️',
  cloud_download: '⬇️☁️',
  storage: '💾',
  
  // Navigation arrows
  arrow_back: '←',
  arrow_forward: '→',
  arrow_upward: '↑',
  arrow_downward: '↓',
  expand_more: '▼',
  expand_less: '▲',
  chevron_left: '‹',
  chevron_right: '›',
  more_vert: '⋮',
  more_horiz: '⋯',
  
  // Visibility
  visibility: '👁️',
  visibility_off: '🙈',
  
  // Default
  default: '📋',
};

// ============================================================================
// Export Functions
// ============================================================================

/**
 * Get menu icon
 * 
 * @param iconName - Icon name (from Manifest)
 * @returns Corresponding Emoji string
 * 
 * [Usage Example]
 * ```tsx
 * const icon = getMenuIcon('dashboard');  // Returns '📊'
 * const icon = getMenuIcon('unknown');    // Returns '📋' (default)
 * ```
 */
export function getMenuIcon(iconName: string): string {
  // Convert to lowercase for matching
  const normalizedName = iconName.toLowerCase();
  return ICON_MAP[normalizedName] || ICON_MAP.default;
}

/**
 * Check if icon exists
 * 
 * @param iconName - Icon name
 * @returns Whether the icon mapping exists
 */
export function hasIcon(iconName: string): boolean {
  const normalizedName = iconName.toLowerCase();
  return normalizedName in ICON_MAP;
}

/**
 * Get all available icon names
 * 
 * @returns Array of icon names
 */
export function getAvailableIcons(): string[] {
  return Object.keys(ICON_MAP).filter(key => key !== 'default');
}

export default getMenuIcon;
