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
 * @file Application Header Component
 * @description Top navigation bar, includes Logo, sidebar toggle button, and user info area
 * @module @brix-sdk/platform-frame-web/layouts/components/AppHeader
 * @version 3.2.0
 *
 * [Design Notes]
 * AppHeader provides standard admin system top bar layout:
 * - Left: Logo + sidebar collapse button
 * - Right: User info, notifications, settings, etc. (provided by Host via headerRight slot)
 *
 * [Architecture Position]
 * ```text
 * +-------------------------------------------------------------------------+
 * |  AppLayout                                                              |
 * |  +-- ConsoleLayout                                                      |
 * |       +-- AppHeader (this file)                                         |
 * |            +-- Logo / Brand                                             |
 * |            +-- Sidebar collapse button                                  |
 * |            +-- headerRight slot                                         |
 * +-------------------------------------------------------------------------+
 * ```
 */

import { type FC, type ReactNode, type CSSProperties, memo } from 'react';

// ============================================================================
// Type Definitions
// ============================================================================

/**
 * AppHeader Props (Layout Slot Variant)
 * 
 * Props for the layout-focused AppHeader component that accepts
 * ReactNode slots for flexible content composition.
 * 
 * [Note: Component Variant]
 * This is different from AppHeaderProps in ./components/AppHeader.tsx
 * which is a full-featured variant with specific user info, logout, etc.
 * Both components serve different use cases within the Shell layer.
 */
export interface AppHeaderProps {
  /** Logo component or text */
  logo?: ReactNode;
  /** Header right content (user info, notifications, etc.) */
  headerRight?: ReactNode;
  /** Sidebar collapse toggle callback */
  onToggleSidebar?: () => void;
}

// ============================================================================
// Style Constants
// ============================================================================

const HEADER_BG_COLOR = '#001529';
const HEADER_TEXT_COLOR = '#fff';
const HEADER_SECONDARY_COLOR = 'rgba(255, 255, 255, 0.65)';

// ============================================================================
// Component Implementation
// ============================================================================

/**
 * Application Header Component
 *
 * Provides standard top navigation bar layout.
 *
 * [Usage Example]
 * ```tsx
 * <AppHeader
 *   logo={<img src="/logo.png" alt="Logo" />}
 *   headerRight={<UserDropdown user={currentUser} />}
 *   onToggleSidebar={() => setCollapsed(!collapsed)}
 * />
 * ```
 */
export const AppHeader: FC<AppHeaderProps> = memo(({
  logo,
  headerRight,
  onToggleSidebar,
}) => {
  // Container style
  const containerStyle: CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    height: '100%',
    padding: '0 24px',
    backgroundColor: HEADER_BG_COLOR,
    color: HEADER_TEXT_COLOR,
  };

  // Left section style
  const leftSectionStyle: CSSProperties = {
    display: 'flex',
    alignItems: 'center',
    gap: '16px',
  };

  // Toggle button style
  const toggleButtonStyle: CSSProperties = {
    background: 'none',
    border: 'none',
    color: HEADER_TEXT_COLOR,
    cursor: 'pointer',
    fontSize: '18px',
    padding: '4px 8px',
    transition: 'opacity 0.2s',
  };

  // Version number style
  const versionStyle: CSSProperties = {
    color: HEADER_SECONDARY_COLOR,
  };

  return (
    <header style={containerStyle}>
      {/* Left: Logo + collapse button */}
      <div style={leftSectionStyle}>
        {/* Sidebar collapse button */}
        {onToggleSidebar && (
          <button
            onClick={onToggleSidebar}
            style={toggleButtonStyle}
            aria-label="Toggle sidebar"
            title="Collapse/Expand sidebar"
          >
            ☰
          </button>
        )}
        
        {/* Logo area */}
        {logo || (
          <span style={{ fontSize: '18px', fontWeight: 'bold' }}>
            Brix Platform
          </span>
        )}
      </div>

      {/* Right: User info, etc. */}
      <div>
        {headerRight || (
          <span style={versionStyle}>
            v3.2.0
          </span>
        )}
      </div>
    </header>
  );
});

AppHeader.displayName = 'AppHeader';

export default AppHeader;
