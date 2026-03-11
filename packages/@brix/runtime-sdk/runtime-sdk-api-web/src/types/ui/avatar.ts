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
 * @file Avatar Component Type Definitions
 * @description Defines types for the Avatar component in the UI adapter system
 * @module @brix/runtime-sdk-api-web/types/ui/avatar
 * @version 3.2.0
 */

import type { ReactNode, CSSProperties } from 'react';
import type { ComponentSize } from './common';

/**
 * Avatar Shape Variants
 */
export type AvatarShape = 'circle' | 'square' | 'rounded';

/**
 * Avatar Component Props
 *
 * User avatar or icon display component with various presentation options.
 *
 * @example
 * ```tsx
 * <Avatar
 *   src={user.avatarUrl}
 *   alt={user.name}
 *   size="large"
 *   fallback={user.initials}
 * />
 * ```
 */
export interface AvatarProps {
  /**
   * Image Source URL
   *
   * URL of the avatar image.
   */
  src?: string;

  /**
   * Alt Text
   *
   * Alternative text for accessibility.
   */
  alt?: string;

  /**
   * Fallback Content
   *
   * Content to display when image fails to load (e.g., initials).
   */
  fallback?: ReactNode;

  /**
   * Avatar Size
   *
   * Predefined size or custom pixel value.
   * @default 'medium'
   */
  size?: ComponentSize | number;

  /**
   * Avatar Shape
   *
   * @default 'circle'
   */
  shape?: AvatarShape;

  /**
   * Background Color
   *
   * Custom background color (CSS value).
   */
  backgroundColor?: string;

  /**
   * Icon Name
   *
   * Icon to display when no image or fallback is provided.
   */
  icon?: string;

  /**
   * Custom Inline Styles
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   */
  className?: string;

  /**
   * Avatar Content
   *
   * Direct content to display (e.g., text initials).
   */
  children?: ReactNode;
}
