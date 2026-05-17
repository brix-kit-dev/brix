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
 * @file Card Component Type Definitions
 * @description Defines types for the Card component in the UI adapter system
 * @module @brix-sdk/runtime-sdk-api-web/types/ui/card
 * @version 3.2.0
 */

import type { ReactNode, MouseEvent, CSSProperties } from 'react';

/**
 * Card Component Props
 *
 * Container component for grouping related content with optional
 * elevation and interactive states.
 *
 * @example
 * ```tsx
 * <Card
 *   title="User Profile"
 *   hoverable
 *   onClick={handleCardClick}
 * >
 *   <p>Card content here</p>
 * </Card>
 * ```
 */
export interface CardProps {
  /**
   * Card Title
   *
   * Optional title displayed at the top of the card.
   */
  title?: ReactNode;

  /**
   * Card Subtitle
   *
   * Optional subtitle displayed below the title.
   */
  subtitle?: ReactNode;

  /**
   * Elevation Level
   *
   * Shadow depth level. 0 means no shadow.
   * @default 1
   */
  elevation?: number;

  /**
   * Hoverable State
   *
   * When true, the card shows hover effects on mouse over.
   * @default false
   */
  hoverable?: boolean;

  /**
   * Bordered Style
   *
   * When true, displays a border instead of/in addition to shadow.
   * @default false
   */
  bordered?: boolean;

  /**
   * Click Event Handler
   *
   * Callback fired when the card is clicked.
   */
  onClick?: (event: MouseEvent<HTMLDivElement>) => void;

  /**
   * Header Actions
   *
   * Action elements displayed in the card header area.
   */
  headerActions?: ReactNode;

  /**
   * Footer Content
   *
   * Content displayed in the card footer area.
   */
  footer?: ReactNode;

  /**
   * Custom Inline Styles
   */
  style?: CSSProperties;

  /**
   * Custom CSS Class Name
   */
  className?: string;

  /**
   * Card Content
   */
  children?: ReactNode;
}
