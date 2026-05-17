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
 * @file Skeleton Component Type Definitions
 * @description Defines types for the Skeleton placeholder component used to
 *              represent loading states with content-shape preserving placeholders.
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/skeleton
 * @version 3.3.0
 *
 * [Frontend Stability Reform Plan v1.0 — C-7]
 * Phase 4 §6.1 mandates that every list / detail page consume
 * `usePageState().render()`, which in turn renders this Skeleton during the
 * `loading` state instead of a bare `Spin`. Skeleton preserves layout
 * dimensions (no content reflow) and gives a perceptibly faster UX.
 *
 * [Architectural Constraints — v3.0.9 Blueprint / Constraint 9]
 * - Plugins MUST obtain Skeleton through `useUI().Skeleton` (R-3).
 * - Implemented in 2C adapters (`infra-adapter-ui-{mui,native}`).
 * - This contract is the only Layer 2A surface; no other module may extend it.
 *
 * [Design Philosophy — Why Skeleton over Spin]
 * - Spin: imperative spinner, signals "something is happening".
 * - Skeleton: structural placeholder, signals "what is loading".
 * Skeleton is preferred for first-load list / card / detail pages because it
 * eliminates Cumulative Layout Shift (CLS) — a Core Web Vitals metric.
 */

import type { CSSProperties } from 'react';

// ============================================================================
// Skeleton Variants — discriminated union with no magic strings
// ============================================================================

/**
 * Skeleton Variant
 *
 * Geometric shape of the placeholder. Each variant maps to a typical
 * content shape so designers and developers share one vocabulary:
 * - `text`     →  Single line of text (default).
 * - `title`    →  Slightly larger / bolder line for headings.
 * - `paragraph`→  Multiple stacked text lines (controlled by `rows`).
 * - `circular` →  Round placeholder for avatars.
 * - `rectangular` → Generic block — image, card body, table row.
 */
export type SkeletonVariant =
  | 'text'
  | 'title'
  | 'paragraph'
  | 'circular'
  | 'rectangular';

/**
 * Skeleton Animation
 *
 * Visual animation hint. `pulse` is the cross-platform default; `wave`
 * is offered as an alternative for adapters that natively support it.
 * `none` disables animation (useful in print mode or for tests).
 */
export type SkeletonAnimation = 'pulse' | 'wave' | 'none';

// ============================================================================
// Skeleton Component Props
// ============================================================================

/**
 * Skeleton Component Props
 *
 * Cross-cutting placeholder primitive consumed by `usePageState().render()`
 * and any plugin that needs a structural loading state.
 *
 * @example
 * ```tsx
 * const { Skeleton, Stack } = useUI();
 *
 * // Single line of text
 * <Skeleton variant="text" width="60%" />
 *
 * // Avatar + two lines (typical list item)
 * <Stack direction="row" spacing={12} align="center">
 *   <Skeleton variant="circular" width={40} height={40} />
 *   <Stack direction="column" spacing={6} style={{ flex: 1 }}>
 *     <Skeleton variant="text" width="40%" />
 *     <Skeleton variant="text" width="80%" />
 *   </Stack>
 * </Stack>
 *
 * // Multi-row paragraph block
 * <Skeleton variant="paragraph" rows={3} />
 * ```
 */
export interface SkeletonProps {
  /**
   * Variant
   *
   * Geometric shape of the placeholder.
   *
   * @default 'text'
   */
  variant?: SkeletonVariant;

  /**
   * Animation
   *
   * Animation style applied to the placeholder.
   *
   * @default 'pulse'
   */
  animation?: SkeletonAnimation;

  /**
   * Number of rows
   *
   * Only meaningful when `variant === 'paragraph'`. Each row renders as a
   * line whose width tapers slightly so the block feels organic.
   *
   * @default 3
   */
  rows?: number;

  /**
   * Width
   *
   * Explicit width of the placeholder. Accepts CSS length values
   * (e.g. `120`, `'60%'`, `'10rem'`). When omitted the component fills
   * its container (block) or uses an intrinsic width (inline).
   */
  width?: number | string;

  /**
   * Height
   *
   * Explicit height of the placeholder. For `text` / `title` variants
   * the line-height-derived default is used when omitted.
   */
  height?: number | string;

  /**
   * Loading State
   *
   * When `true` (default) the placeholder is shown. When `false` and
   * `children` is provided the children are rendered instead — this allows
   * `<Skeleton loading={isLoading}><RealContent /></Skeleton>` patterns
   * without a parent ternary.
   *
   * @default true
   */
  loading?: boolean;

  /**
   * Custom Inline Styles
   *
   * Forwarded to the placeholder's root element.
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   *
   * Forwarded to the placeholder's root element.
   */
  className?: string;

  /**
   * Test ID
   *
   * `data-testid` attribute for testing frameworks (Vitest / Playwright).
   */
  'data-testid'?: string;

  /**
   * Real Content
   *
   * Optional. When supplied together with `loading={false}`, this content
   * is rendered in place of the placeholder.
   */
  children?: import('react').ReactNode;
}
