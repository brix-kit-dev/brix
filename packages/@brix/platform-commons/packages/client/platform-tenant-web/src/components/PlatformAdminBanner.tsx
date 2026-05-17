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
 * @file PlatformAdminBanner — Phase 2 / C-4 ViewMode UI
 * @description Renders a persistent banner whenever a platform admin is
 * currently viewing the system as a tenant (i.e. {@code originalSub} is
 * present in the JWT). Provides a one-click action to exit the viewing
 * session and return to the platform-admin perspective.
 *
 * @module @brix-sdk/platform-tenant-web/components/PlatformAdminBanner
 * @version 3.3.0
 *
 * [Architecture Layer]
 * Layer 3 (Presentation). Resolves {@link useViewMode} from the runtime
 * context — does NOT import any UI framework directly (R-3 compliance).
 *
 * @since 3.3.0
 */

import React, { useCallback, useState } from 'react';
import { useViewMode } from '@brix-sdk/runtime-sdk-react';
import { VIEW_MODE_PLATFORM_ADMIN } from '@brix-sdk/runtime-sdk-api-web';

/**
 * Visual style overrides accepted by {@link PlatformAdminBanner}. Provided
 * here as a small, framework-free knob so Hosts can re-skin the banner
 * (e.g. to match a tenant's brand colours) without forking the component.
 */
export interface PlatformAdminBannerStyle {
  /** Background colour. Defaults to a high-contrast warning red. */
  readonly background?: string;
  /** Foreground/text colour. */
  readonly color?: string;
  /** z-index — must sit above app shell chrome. */
  readonly zIndex?: number;
  /** Banner height (any valid CSS length). */
  readonly height?: string;
}

/**
 * Public props for {@link PlatformAdminBanner}.
 */
export interface PlatformAdminBannerProps {
  /** Optional visual overrides. */
  readonly style?: PlatformAdminBannerStyle;
  /**
   * Optional override for the displayed message. Receives the originalSub
   * and viewing tenant ID so callers may localise the text.
   */
  readonly renderMessage?: (
    originalSub: string,
    viewingTenantId: string | null,
  ) => React.ReactNode;
  /**
   * Label for the exit button. Defaults to a Chinese-localised string per
   * the plan; Hosts using i18n should override.
   */
  readonly exitLabel?: string;
}

const DEFAULT_BACKGROUND = '#c62828';
const DEFAULT_COLOR = '#ffffff';
const DEFAULT_Z_INDEX = 2000;
const DEFAULT_HEIGHT = '36px';
const DEFAULT_EXIT_LABEL = '退出超管视角';

/**
 * Renders a sticky red banner across the top of the viewport while the
 * current session is in viewing mode. Returns `null` for ordinary platform
 * admin / tenant sessions so it is safe to mount unconditionally in the
 * Host shell.
 */
export const PlatformAdminBanner: React.FC<PlatformAdminBannerProps> = ({
  style,
  renderMessage,
  exitLabel = DEFAULT_EXIT_LABEL,
}) => {
  const { isViewingAsTenant, originalSub, viewingTenantId, switchTo } = useViewMode();
  const [exiting, setExiting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleExit = useCallback(async () => {
    if (exiting) return;
    setExiting(true);
    setError(null);
    try {
      await switchTo({ mode: VIEW_MODE_PLATFORM_ADMIN });
      // The implementation is expected to perform a full reload; if it does
      // not (e.g. in tests), we leave the banner in the disabled-loading
      // state to avoid surprising behaviour.
    } catch (err) {
      setExiting(false);
      const message = err instanceof Error ? err.message : String(err);
      setError(message);
    }
  }, [exiting, switchTo]);

  if (!isViewingAsTenant || !originalSub) {
    return null;
  }

  const containerStyle: React.CSSProperties = {
    position: 'sticky',
    top: 0,
    left: 0,
    right: 0,
    height: style?.height ?? DEFAULT_HEIGHT,
    background: style?.background ?? DEFAULT_BACKGROUND,
    color: style?.color ?? DEFAULT_COLOR,
    zIndex: style?.zIndex ?? DEFAULT_Z_INDEX,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: '12px',
    fontSize: '13px',
    fontWeight: 500,
    padding: '0 16px',
    boxShadow: '0 1px 4px rgba(0,0,0,0.2)',
  };

  const buttonStyle: React.CSSProperties = {
    background: 'rgba(255,255,255,0.18)',
    color: 'inherit',
    border: '1px solid rgba(255,255,255,0.6)',
    borderRadius: '4px',
    padding: '4px 12px',
    cursor: exiting ? 'wait' : 'pointer',
    fontSize: '12px',
    opacity: exiting ? 0.7 : 1,
  };

  return (
    <div role="alert" data-testid="view-mode-banner" style={containerStyle}>
      <span>
        {renderMessage
          ? renderMessage(originalSub, viewingTenantId)
          : `平台超管 #${originalSub} 正在以租户 ${viewingTenantId ?? '?'} 的视角操作`}
      </span>
      <button
        type="button"
        onClick={handleExit}
        disabled={exiting}
        style={buttonStyle}
        data-testid="view-mode-exit"
      >
        {exiting ? '...' : exitLabel}
      </button>
      {error !== null && (
        <span data-testid="platform-admin-banner-error" style={{ marginLeft: 8, fontSize: 12 }}>
          {error}
        </span>
      )}
    </div>
  );
};
