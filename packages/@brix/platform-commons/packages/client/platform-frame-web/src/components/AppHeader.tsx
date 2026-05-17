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
 * @file AppHeader Component
 * @description Application top navigation bar component - Shell layer pre-assembled component
 * @module @brix-sdk/platform-frame-web/components/AppHeader
 * @version 3.2.0
 *
 * [Architectural Position]
 * AppHeader is a pre-assembled component in the Shell layer, providing standard top navigation bar implementation.
 * The Host layer imports and uses it directly, without implementing any top navigation logic.
 *
 * [Design Principles]
 * - Follows the v3.0.4 blueprint Host thin-layer principle
 * - All styles are inline, no external CSS dependencies
 * - Receives configuration via props, no hardcoded business logic
 *
 * [Usage Example]
 * ```tsx
 * import { AppHeader } from '@brix-sdk/platform-frame-web';
 *
 * <AppHeader
 *   sidebarCollapsed={collapsed}
 *   onToggleSidebar={() => setCollapsed(!collapsed)}
 *   username={user.name}
 *   onLogout={handleLogout}
 *   onNavigate={navigate}
 *   branding={{ appName: 'My App' }}
 * />
 * ```
 */

import { useState, useMemo, useRef, useEffect, type CSSProperties, type ReactNode } from 'react';
import { useTheme, useUIOptional } from '@brix-sdk/runtime-sdk-react';
import { withAlpha } from './styleUtils';

/**
 * AppHeader Props (Full-Featured Variant)
 * 
 * Props for the full-featured AppHeader component with user info,
 * logout, navigation, and branding capabilities.
 * 
 * [Note: Component Variant]
 * This is different from AppHeaderProps in ./layouts/components/AppHeader.tsx
 * which is a simpler layout-focused variant accepting ReactNode slots.
 * Both components serve different use cases within the Shell layer.
 */
export interface AppHeaderProps {
  /** Whether the sidebar is collapsed */
  sidebarCollapsed: boolean;
  /** Callback to toggle sidebar collapse state */
  onToggleSidebar: () => void;
  /** Username */
  username?: string;
  /** Avatar URL (optional) */
  avatar?: string;
  /** Logout callback */
  onLogout: () => void;
  /** Navigation callback */
  onNavigate: (path: string) => void;
  /** Branding configuration (optional) */
  branding?: {
    /** Application name */
    appName?: string;
    /** Theme color */
    primaryColor?: string;
  };
}

/**
 * Application top navigation bar component
 *
 * Provides standard top navigation bar implementation, including:
 * - Sidebar collapse toggle button
 * - Notifications entry
 * - User info display
 * - Settings entry
 * - Logout button
 *
 * @param props - Component props
 * @returns React node
 */
export function AppHeader({
  sidebarCollapsed,
  onToggleSidebar,
  username = 'User',
  avatar,
  onLogout,
  onNavigate,
  branding,
}: AppHeaderProps): ReactNode {
  const { tokens } = useTheme();
  const ui = useUIOptional();
  const Icon = ui?.Icon;
  const primaryColor = branding?.primaryColor ?? tokens.colors.brand.primary;
  const shellGlassRadius = `calc(${tokens.shape.lg} + ${tokens.space.sm})`;
  const controlGlassRadius = `calc(${tokens.shape.lg} + ${tokens.space.xs})`;
  const controlGlassBackground = 'transparent';
  const controlHoverBackground = withAlpha(primaryColor, 0.08);
  const hoverBackground = withAlpha(primaryColor, 0.08);

  // ========== Style Definitions ==========

  const headerStyle = useMemo<CSSProperties>(
    () => ({
      height: '100%',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'space-between',
      padding: '0 18px',
      position: 'relative',
      overflow: 'visible',
      // 劳模模式：surface.card 实色面板 + 1px 边 + 轻阴影。
      background: tokens.colors.surface.card,
      color: tokens.colors.layout.headerText,
      border: `1px solid ${withAlpha(tokens.colors.text.primary, 0.08)}`,
      borderRadius: shellGlassRadius,
      boxShadow:
        `0 1px 2px ${withAlpha(tokens.colors.text.primary, 0.04)}, ` +
        `0 8px 24px ${withAlpha(tokens.colors.text.primary, 0.04)}`,
    }),
    [primaryColor, shellGlassRadius, tokens]
  );

  const leftStyle = useMemo<CSSProperties>(
    () => ({
      display: 'flex',
      alignItems: 'center',
      gap: '16px',
      position: 'relative',
    }),
    []
  );

  const rightStyle = useMemo<CSSProperties>(
    () => ({
      display: 'flex',
      alignItems: 'center',
      gap: '16px',
      position: 'relative',
    }),
    []
  );

  const toggleButtonStyle = useMemo<CSSProperties>(
    () => ({
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      width: '40px',
      height: '40px',
      border: `1px solid ${withAlpha(primaryColor, 0.18)}`,
      background: controlGlassBackground,
      cursor: 'pointer',
      fontSize: '18px',
      color: primaryColor,
      borderRadius: controlGlassRadius,
      boxShadow: 'none',
      transition: `background ${tokens.motion.durationShort} ${tokens.motion.easing}, border-color ${tokens.motion.durationShort} ${tokens.motion.easing}, box-shadow ${tokens.motion.durationShort} ${tokens.motion.easing}`,
    }),
    [controlGlassBackground, controlGlassRadius, primaryColor, tokens]
  );

  const userInfoStyle = useMemo<CSSProperties>(
    () => ({
      display: 'flex',
      alignItems: 'center',
      gap: '8px',
      cursor: 'pointer',
      padding: '5px 12px 5px 6px',
      border: `1px solid ${withAlpha(primaryColor, 0.16)}`,
      borderRadius: tokens.shape.full,
      background: controlGlassBackground,
      color: tokens.colors.text.primary,
      font: 'inherit',
      appearance: 'none',
      boxShadow: 'none',
      transition: `background ${tokens.motion.durationShort} ${tokens.motion.easing}, border-color ${tokens.motion.durationShort} ${tokens.motion.easing}, box-shadow ${tokens.motion.durationShort} ${tokens.motion.easing}`,
    }),
    [controlGlassBackground, primaryColor, tokens]
  );

  const avatarStyle = useMemo<CSSProperties>(
    () => ({
      width: '32px',
      height: '32px',
      borderRadius: '50%',
      background: primaryColor,
      color: tokens.colors.brand.primaryContrast,
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      fontSize: '14px',
      fontWeight: 700,
      boxShadow: 'none',
    }),
    [primaryColor, tokens]
  );

  const dropdownStyle = useMemo<CSSProperties>(
    () => ({
      position: 'relative' as const,
    }),
    []
  );

  // ========== Render ==========

  // Dropdown state
  const [dropdownOpen, setDropdownOpen] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  // Close dropdown when clicking outside
  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setDropdownOpen(false);
      }
    }
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const dropdownMenuStyle = useMemo<CSSProperties>(
    () => ({
      position: 'absolute',
      top: '100%',
      right: 0,
      marginTop: '8px',
      minWidth: '200px',
      background: tokens.colors.surface.card,
      borderRadius: shellGlassRadius,
      boxShadow: `0 14px 32px ${withAlpha(tokens.colors.text.primary, 0.08)}`,
      border: `1px solid ${withAlpha(tokens.colors.border.default, 0.80)}`,
      overflow: 'hidden',
      zIndex: 20,
    }),
    [shellGlassRadius, tokens]
  );

  const dropdownItemStyle = useMemo<CSSProperties>(
    () => ({
      display: 'flex',
      alignItems: 'center',
      gap: '12px',
      padding: '12px 16px',
      border: 'none',
      background: 'transparent',
      width: '100%',
      textAlign: 'left',
      cursor: 'pointer',
      fontSize: '14px',
      color: tokens.colors.text.primary,
      fontFamily: tokens.typography.fontFamily,
      transition: `background ${tokens.motion.durationShort} ${tokens.motion.easing}`,
    }),
    [tokens]
  );

  const dropdownDividerStyle = useMemo<CSSProperties>(
    () => ({
      height: '1px',
      backgroundColor: tokens.colors.border.subtle,
      margin: '4px 0',
    }),
    [tokens.colors.border.subtle]
  );

  return (
    <div style={headerStyle}>
      {/* Left section */}
      <div style={leftStyle}>
        {/* Sidebar collapse button */}
        <button
          type="button"
          style={toggleButtonStyle}
          onClick={onToggleSidebar}
          onMouseEnter={(e) => {
            e.currentTarget.style.background = controlHoverBackground;
            e.currentTarget.style.borderColor = withAlpha(primaryColor, 0.24);
            e.currentTarget.style.boxShadow = 'none';
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.background = controlGlassBackground;
            e.currentTarget.style.borderColor = withAlpha(primaryColor, 0.18);
            e.currentTarget.style.boxShadow = 'none';
          }}
          aria-label={sidebarCollapsed ? 'Expand menu' : 'Collapse menu'}
          aria-expanded={!sidebarCollapsed}
          title={sidebarCollapsed ? 'Expand menu' : 'Collapse menu'}
        >
          {Icon ? (
            <Icon name={sidebarCollapsed ? 'menu' : 'close'} size="small" />
          ) : sidebarCollapsed ? '☰' : '✕'}
        </button>

      </div>

      {/* Right section - User dropdown */}
      <div style={rightStyle}>
        <div ref={dropdownRef} style={dropdownStyle}>
          {/* User avatar/name - clickable to open dropdown */}
          <button
            type="button"
            style={userInfoStyle}
            onClick={() => setDropdownOpen(!dropdownOpen)}
            onMouseEnter={(e) => {
              e.currentTarget.style.background = controlHoverBackground;
              e.currentTarget.style.borderColor = withAlpha(primaryColor, 0.22);
              e.currentTarget.style.boxShadow = 'none';
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = dropdownOpen ? controlHoverBackground : controlGlassBackground;
              e.currentTarget.style.borderColor = withAlpha(primaryColor, dropdownOpen ? 0.22 : 0.16);
              e.currentTarget.style.boxShadow = 'none';
            }}
            aria-haspopup="menu"
            aria-expanded={dropdownOpen}
            aria-label="Open account menu"
          >
            {avatar ? (
              <img
                src={avatar}
                alt={username}
                style={{ ...avatarStyle, objectFit: 'cover' as const }}
              />
            ) : (
              <div style={avatarStyle}>{username.charAt(0).toUpperCase()}</div>
            )}
            <span data-testid="current-user-name" style={{ fontSize: '14px', color: tokens.colors.text.primary }}>
              {username}
            </span>
            {Icon ? (
              <Icon name={dropdownOpen ? 'expand_less' : 'expand_more'} size="small" color={tokens.colors.text.secondary} />
            ) : (
              <span style={{ fontSize: '12px', color: tokens.colors.text.secondary, marginLeft: '4px' }}>
                {dropdownOpen ? '▲' : '▼'}
              </span>
            )}
          </button>

          {/* Dropdown menu */}
          {dropdownOpen && (
            <div style={dropdownMenuStyle}>
              {/* Notifications */}
              <button
                type="button"
                style={dropdownItemStyle}
                onClick={() => {
                  onNavigate('/messenger/notifications');
                  setDropdownOpen(false);
                }}
                onMouseEnter={(e) => { e.currentTarget.style.background = hoverBackground; }}
                onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent'; }}
              >
                {Icon ? <Icon name="notifications" size="small" color={tokens.colors.text.secondary} /> : null}
                <span>Notifications</span>
              </button>

              {/* Settings */}
              <button
                type="button"
                style={dropdownItemStyle}
                onClick={() => {
                  onNavigate('/settings');
                  setDropdownOpen(false);
                }}
                onMouseEnter={(e) => { e.currentTarget.style.background = hoverBackground; }}
                onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent'; }}
              >
                {Icon ? <Icon name="settings" size="small" color={tokens.colors.text.secondary} /> : null}
                <span>Settings</span>
              </button>

              <div style={dropdownDividerStyle} />

              {/* Logout */}
              <button
                type="button"
                style={{ ...dropdownItemStyle, color: tokens.colors.status.error }}
                onClick={() => {
                  onLogout();
                  setDropdownOpen(false);
                }}
                onMouseEnter={(e) => { e.currentTarget.style.background = withAlpha(tokens.colors.status.error, 0.08); }}
                onMouseLeave={(e) => { e.currentTarget.style.background = 'transparent'; }}
              >
                {Icon ? <Icon name="logout" size="small" color={tokens.colors.status.error} /> : null}
                <span>Logout</span>
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default AppHeader;
