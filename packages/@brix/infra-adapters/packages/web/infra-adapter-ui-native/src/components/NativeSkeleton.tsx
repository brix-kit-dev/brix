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
 * @file Native Skeleton Component
 * @description Pure-CSS implementation of {@link SkeletonProps} from the
 *              UIAdapter contract. Used by `usePageState().render()` and any
 *              other place that needs a CLS-stable loading placeholder.
 * @module @brix-sdk/infra-adapter-ui-native/components/NativeSkeleton
 * @version 3.3.0
 *
 * <h3>Design Principles</h3>
 * <ul>
 *   <li>Zero third-party UI dependencies — pure CSS keyframes.</li>
 *   <li>Visually identical bounding box to {@link MuiSkeleton} so adapter
 *       swaps do not cause layout shift.</li>
 *   <li>Animation can be disabled (`animation="none"`) for users who set
 *       `prefers-reduced-motion`. The default mode also respects that
 *       media query via the global stylesheet emitted on first mount.</li>
 *   <li>Exposes ARIA `role="status"` + `aria-busy="true"` so screen readers
 *       announce the loading state.</li>
 * </ul>
 */

import { type FC, type CSSProperties } from 'react';
import type {
  SkeletonAnimation,
  SkeletonProps,
  SkeletonVariant,
} from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// Style tokens — kept inline to honour the "no third-party CSS" constraint
// of the native adapter.
// ============================================================================

const COLOUR_BASE = 'rgba(0, 0, 0, 0.11)';
const COLOUR_HIGHLIGHT = 'rgba(0, 0, 0, 0.05)';
const RADIUS_DEFAULT = 4;

const KEYFRAMES_ID = 'native-skeleton-keyframes';

/**
 * Inject the keyframe stylesheet exactly once. The `<style>` is hoisted to
 * `document.head` so it survives unmounts of individual skeletons.
 *
 * Implementation note: we use a singleton element keyed by `KEYFRAMES_ID`
 * to avoid rule duplication across the page. SSR-safe: guarded by `document`.
 */
function ensureKeyframes(): void {
  if (typeof document === 'undefined') return;
  if (document.getElementById(KEYFRAMES_ID)) return;
  const style = document.createElement('style');
  style.id = KEYFRAMES_ID;
  style.textContent = `
@keyframes brix-native-skeleton-pulse {
  0%   { opacity: 1; }
  50%  { opacity: 0.4; }
  100% { opacity: 1; }
}
@keyframes brix-native-skeleton-wave {
  0%   { transform: translateX(-100%); }
  60%  { transform: translateX(100%); }
  100% { transform: translateX(100%); }
}
@media (prefers-reduced-motion: reduce) {
  .brix-native-skeleton { animation: none !important; }
  .brix-native-skeleton-wave-overlay { animation: none !important; opacity: 0; }
}
`;
  document.head.appendChild(style);
}

// ============================================================================
// Per-variant default sizes
// ============================================================================

interface VariantDefaults {
  width: number | string;
  height: number | string;
  borderRadius: number | string;
}

const VARIANT_DEFAULTS_MAP: Record<SkeletonVariant, VariantDefaults> = {
  text:        { width: '100%', height: 14, borderRadius: RADIUS_DEFAULT },
  title:       { width: '40%',  height: 28, borderRadius: RADIUS_DEFAULT },
  paragraph:   { width: '100%', height: 14, borderRadius: RADIUS_DEFAULT },
  rectangular: { width: '100%', height: 80, borderRadius: 0 },
  circular:    { width: 40,     height: 40, borderRadius: '50%' },
};

/**
 * Resolve per-variant defaults. Always returns a value (`text` is the
 * fallback) — required to satisfy `noUncheckedIndexedAccess` strictness.
 */
function getVariantDefaults(variant: SkeletonVariant): VariantDefaults {
  return VARIANT_DEFAULTS_MAP[variant] ?? VARIANT_DEFAULTS_MAP.text;
}

// ============================================================================
// Helpers
// ============================================================================

function buildAnimationStyle(animation: SkeletonAnimation): CSSProperties {
  if (animation === 'pulse') {
    return { animation: 'brix-native-skeleton-pulse 1.5s ease-in-out 0.5s infinite' };
  }
  if (animation === 'wave') {
    // Wave is rendered via a child overlay; the base block stays static.
    return { position: 'relative', overflow: 'hidden' };
  }
  return {};
}

function renderWaveOverlay(): JSX.Element {
  return (
    <span
      aria-hidden="true"
      className="brix-native-skeleton-wave-overlay"
      style={{
        position: 'absolute',
        inset: 0,
        background:
          'linear-gradient(90deg, transparent, rgba(255,255,255,0.5), transparent)',
        animation: 'brix-native-skeleton-wave 1.6s linear 0.5s infinite',
      }}
    />
  );
}

// ============================================================================
// Component
// ============================================================================

/**
 * Native Skeleton — pure-CSS placeholder block.
 *
 * @example
 * ```tsx
 * const { Skeleton } = useUI();
 *
 * // Single line text placeholder
 * <Skeleton variant="text" width={200} />
 *
 * // Three-line paragraph (last line 60% width)
 * <Skeleton variant="paragraph" rows={3} />
 *
 * // Avatar placeholder
 * <Skeleton variant="circular" width={48} height={48} />
 *
 * // Conditional wrap — render children when not loading
 * <Skeleton loading={isLoading} variant="rectangular" height={120}>
 *   <Image src={src} />
 * </Skeleton>
 * ```
 */
export const NativeSkeleton: FC<SkeletonProps> = ({
  variant = 'text',
  animation = 'pulse',
  rows = 3,
  width,
  height,
  loading = true,
  style,
  className,
  'data-testid': dataTestId,
  children,
}) => {
  ensureKeyframes();

  if (!loading && children !== undefined) {
    return <>{children}</>;
  }

  const defaults = getVariantDefaults(variant);
  const resolvedWidth = width ?? defaults.width;
  const resolvedHeight = height ?? defaults.height;

  const baseClassName = ['brix-native-skeleton', className].filter(Boolean).join(' ');

  const baseStyle: CSSProperties = {
    display: 'block',
    backgroundColor: COLOUR_BASE,
    backgroundImage:
      animation === 'pulse'
        ? 'none'
        : `linear-gradient(90deg, ${COLOUR_BASE}, ${COLOUR_HIGHLIGHT}, ${COLOUR_BASE})`,
    width: resolvedWidth,
    height: resolvedHeight,
    borderRadius: defaults.borderRadius,
    ...buildAnimationStyle(animation),
    ...style,
  };

  // Paragraph composes N text rows.
  if (variant === 'paragraph') {
    const safeRows = Math.max(1, Math.floor(rows));
    return (
      <div
        className={className}
        style={{ display: 'flex', flexDirection: 'column', gap: 8, ...style }}
        data-testid={dataTestId}
        role="status"
        aria-busy="true"
        aria-live="polite"
      >
        {Array.from({ length: safeRows }, (_, i) => {
          const rowWidth = i === safeRows - 1 ? '60%' : (width ?? '100%');
          const rowStyle: CSSProperties = {
            ...baseStyle,
            width: rowWidth,
            height: height ?? getVariantDefaults('text').height,
          };
          return (
            <span key={i} className={baseClassName} style={rowStyle}>
              {animation === 'wave' && renderWaveOverlay()}
            </span>
          );
        })}
      </div>
    );
  }

  return (
    <span
      className={baseClassName}
      style={baseStyle}
      data-testid={dataTestId}
      role="status"
      aria-busy="true"
      aria-live="polite"
    >
      {animation === 'wave' && renderWaveOverlay()}
    </span>
  );
};
