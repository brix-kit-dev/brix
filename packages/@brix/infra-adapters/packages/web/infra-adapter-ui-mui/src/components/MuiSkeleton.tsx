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
 * @file MUI Skeleton Component Implementation
 * @description Material UI implementation of the {@link SkeletonProps} contract
 *              from `@brix-sdk/runtime-sdk-api-web`. Used by `usePageState().render()`
 *              and any other place that needs an empty layout placeholder while
 *              data is loading.
 * @module @brix-sdk/infra-adapter-ui-mui/components/MuiSkeleton
 * @version 3.3.0
 *
 * <h3>Why Skeleton, not Spin?</h3>
 * Skeletons preserve layout dimensions while loading, eliminating Cumulative
 * Layout Shift (CLS) — a Core Web Vitals metric the Brix v1.0 Stability
 * Reform Plan explicitly targets in §6.1 (C-7). Spin is for *operations*
 * (pending mutation), Skeleton is for *content* (pending fetch).
 *
 * <h3>Architectural Constraints (R-3 / R-4)</h3>
 * <ul>
 *   <li>Plugins MUST consume this only through `useUI().Skeleton` — never import.</li>
 *   <li>This file is the only place allowed to import `@mui/material/Skeleton`.</li>
 * </ul>
 */

import { type FC } from 'react';
import MuiSkeletonBase from '@mui/material/Skeleton';
import Box from '@mui/material/Box';
import type {
  SkeletonAnimation,
  SkeletonProps,
  SkeletonVariant,
} from '@brix-sdk/runtime-sdk-api-web';

// ============================================================================
// Variant + animation mappings (UIAdapter contract → MUI prop space)
// ============================================================================

/**
 * Map Brix `SkeletonVariant` → MUI `SkeletonProps.variant`.
 *
 * `paragraph` is composed of multiple `text` rows below — the lookup only
 * applies to the per-row underlying primitive.
 */
const VARIANT_MAP: Record<Exclude<SkeletonVariant, 'paragraph'>, 'text' | 'circular' | 'rectangular'> = {
  text: 'text',
  title: 'text',
  circular: 'circular',
  rectangular: 'rectangular',
};

/**
 * Map Brix `SkeletonAnimation` → MUI `SkeletonProps.animation`.
 *
 * MUI accepts `false` to disable the animation entirely.
 */
const ANIMATION_MAP: Record<SkeletonAnimation, 'pulse' | 'wave' | false> = {
  pulse: 'pulse',
  wave: 'wave',
  none: false,
};

/**
 * MUI Skeleton Component
 *
 * Implements UIAdapter `Skeleton`. When `loading` is `false` and `children`
 * are provided the children are rendered as-is, allowing callers to write:
 *
 * ```tsx
 * <Skeleton loading={isLoading} variant="paragraph" rows={3}>
 *   <UserCard user={user} />
 * </Skeleton>
 * ```
 *
 * For the most common case — `usePageState().render()` — only props are
 * supplied (no children) and a structural placeholder is rendered.
 *
 * @param props {@link SkeletonProps}
 * @returns A skeleton placeholder, or `children` when not loading.
 */
export const MuiSkeleton: FC<SkeletonProps> = ({
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
  if (!loading && children !== undefined) {
    return <>{children}</>;
  }

  const muiAnimation = ANIMATION_MAP[animation];

  if (variant === 'paragraph') {
    // Paragraph = N text rows, the last one shorter to mimic prose.
    const safeRows = Math.max(1, Math.floor(rows));
    return (
      <Box
        className={className}
        style={style}
        data-testid={dataTestId}
        role="status"
        aria-busy="true"
        aria-live="polite"
      >
        {Array.from({ length: safeRows }, (_, i) => (
          <MuiSkeletonBase
            key={i}
            variant="text"
            animation={muiAnimation}
            width={i === safeRows - 1 ? '60%' : width ?? '100%'}
            height={height}
          />
        ))}
      </Box>
    );
  }

  // Title is functionally a single bold text row — the larger height is
  // mapped here rather than in the variant map so consumers can override.
  const resolvedHeight = height ?? (variant === 'title' ? 32 : undefined);

  return (
    <MuiSkeletonBase
      variant={VARIANT_MAP[variant]}
      animation={muiAnimation}
      width={width}
      height={resolvedHeight}
      className={className}
      style={style}
      data-testid={dataTestId}
      // MUI sets role="progressbar" by default; we override to "status" for
      // assistive-tech parity with NativeSkeleton & blueprint a11y rules.
      role="status"
      aria-busy="true"
      aria-live="polite"
    />
  );
};
