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
 * @file breakpoints.ts
 * @description Breakpoint Tokens
 * @version 3.1.0
 * 
 * [Architecture Note]
 * Implements v3.0 Architecture Blueprint Task 4.6-1:
 * Unify breakpoint values between @brix-sdk/platform-design-tokens and shell-web LayoutStore
 */

/**
 * Breakpoint Values (pixels) - Unified Breakpoint System
 * 
 * | Breakpoint | Min Width | Device Type |
 * |------------|-----------|-------------|
 * | xs         | 0         | Phone Portrait |
 * | sm         | 576px     | Phone Landscape |
 * | md         | 768px     | Tablet Portrait |
 * | lg         | 992px     | Tablet Landscape/Small Laptop |
 * | xl         | 1200px    | Desktop |
 * | xxl        | 1600px    | Large Desktop |
 */
export const breakpoints = {
  xs: '0px',
  sm: '576px',
  md: '768px',
  lg: '992px',
  xl: '1200px',
  xxl: '1600px',
} as const;

/**
 * Breakpoint Numeric Values (without units, for JavaScript calculations)
 */
export const breakpointValues = {
  xs: 0,
  sm: 576,
  md: 768,
  lg: 992,
  xl: 1200,
  xxl: 1600,
} as const;

export type BreakpointKey = keyof typeof breakpoints;

/**
 * Min-width Media Query
 */
export function mediaUp(breakpoint: BreakpointKey): string {
  return `@media (min-width: ${breakpoints[breakpoint]})`;
}

/**
 * Max-width Media Query
 */
export function mediaDown(breakpoint: BreakpointKey): string {
  const value = breakpointValues[breakpoint] - 1;
  return `@media (max-width: ${value}px)`;
}

/**
 * Range Media Query
 */
export function mediaBetween(min: BreakpointKey, max: BreakpointKey): string {
  const minValue = breakpoints[min];
  const maxValue = breakpointValues[max] - 1;
  return `@media (min-width: ${minValue}) and (max-width: ${maxValue}px)`;
}

/**
 * Exact Match Media Query
 */
export function mediaOnly(breakpoint: BreakpointKey): string {
  const keys = Object.keys(breakpoints) as BreakpointKey[];
  const index = keys.indexOf(breakpoint);
  const nextBreakpoint = keys[index + 1];
  
  if (nextBreakpoint) {
    return mediaBetween(breakpoint, nextBreakpoint);
  }
  return mediaUp(breakpoint);
}

/**
 * Predefined Media Queries
 */
export const mediaQueries = {
  xs: mediaUp('xs'),
  sm: mediaUp('sm'),
  md: mediaUp('md'),
  lg: mediaUp('lg'),
  xl: mediaUp('xl'),
  xxl: mediaUp('xxl'),
  /** Mobile: < 768px */
  mobile: mediaDown('md'),
  /** Tablet: 768px - 991px */
  tablet: mediaBetween('md', 'lg'),
  /** Desktop: >= 992px */
  desktop: mediaUp('lg'),
} as const;

/**
 * Container Max Width
 */
export const containerMaxWidth = {
  xs: '100%',
  sm: '540px',
  md: '720px',
  lg: '960px',
  xl: '1140px',
  xxl: '1320px',
  full: '100%',
} as const;

/**
 * Get Container Styles
 */
export function getContainerStyles(maxWidth: keyof typeof containerMaxWidth = 'xl') {
  return {
    maxWidth: containerMaxWidth[maxWidth],
    marginLeft: 'auto',
    marginRight: 'auto',
    paddingLeft: '1rem',
    paddingRight: '1rem',
  };
}

/**
 * Check if Current Viewport Matches Specified Breakpoint
 */
export function matchesBreakpoint(breakpoint: BreakpointKey): boolean {
  if (typeof window === 'undefined') return false;
  return window.innerWidth >= breakpointValues[breakpoint];
}

/**
 * Get Current Viewport Breakpoint
 */
export function getCurrentBreakpoint(): BreakpointKey {
  if (typeof window === 'undefined') return 'md';
  
  const width = window.innerWidth;
  
  if (width >= breakpointValues.xxl) return 'xxl';
  if (width >= breakpointValues.xl) return 'xl';
  if (width >= breakpointValues.lg) return 'lg';
  if (width >= breakpointValues.md) return 'md';
  if (width >= breakpointValues.sm) return 'sm';
  return 'xs';
}
