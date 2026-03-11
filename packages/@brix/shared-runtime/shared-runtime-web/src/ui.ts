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
 *
 * @fileoverview UI Library Runtime Re-exports
 *
 * This module serves as the single source of truth for MUI (Material-UI)
 * and Emotion styling in the Brix Platform. All UI component and styling
 * imports MUST come from this module to ensure consistent theming.
 *
 * @module @brix/shared-runtime-web/ui
 *
 * ## Architecture Context (Layer 2B - Shared Runtime)
 *
 * According to v3.0.7 Architecture Blueprint:
 * - UI theming is provided by the Host
 * - Plugins inherit the Host's theme context
 * - Emotion's CSS-in-JS must use the same instance for style injection
 *
 * ## Why MUI + Emotion?
 *
 * MUI is chosen for:
 * 1. Enterprise-grade component library
 * 2. Comprehensive accessibility support
 * 3. Extensive customization via theming
 * 4. Active maintenance and community
 *
 * Emotion is used internally by MUI and provides:
 * 1. Zero-runtime CSS extraction option
 * 2. Dynamic styling with full CSS support
 * 3. Server-side rendering compatibility
 *
 * ## Usage
 *
 * ```typescript
 * import { Button, Typography, styled } from '@brix/shared-runtime-web/ui';
 *
 * const StyledButton = styled(Button)`
 *   margin: 8px;
 * `;
 *
 * function MyComponent() {
 *   return (
 *     <StyledButton variant="contained">
 *       Click Me
 *     </StyledButton>
 *   );
 * }
 * ```
 *
 * ## Theming Guidelines
 *
 * 1. Plugins should NOT create their own ThemeProvider
 * 2. Use theme tokens via sx prop or styled() for consistency
 * 3. Custom colors should be added to the Host theme, not hardcoded
 *
 * @see {@link ../mf-shared-config.ts} for Module Federation configuration
 * @see {@link ../versions.ts} for centralized version constants
 */

// =============================================================================
// MUI Core Exports
// =============================================================================

/**
 * Re-export all MUI core components and utilities.
 *
 * This is a comprehensive export including:
 *
 * **Layout Components:**
 * - Box, Container, Grid, Stack
 *
 * **Input Components:**
 * - Button, IconButton, TextField, Select, Checkbox, Radio, Switch, Slider
 *
 * **Data Display:**
 * - Typography, Table, List, Chip, Avatar, Badge, Tooltip
 *
 * **Feedback:**
 * - Alert, Snackbar, Dialog, Backdrop, CircularProgress, LinearProgress
 *
 * **Navigation:**
 * - Tabs, Menu, Drawer, AppBar, Breadcrumbs, Pagination
 *
 * **Surfaces:**
 * - Card, Paper, Accordion
 *
 * **Utils:**
 * - useTheme, useMediaQuery, ThemeProvider, CssBaseline
 *
 * **Hooks:**
 * - useAutocomplete, useScrollTrigger, usePagination
 *
 * @remarks
 * This is a large re-export. Tree-shaking will eliminate unused components
 * in the production build. Import only what you need for best performance.
 */
export * from '@mui/material';

// =============================================================================
// Emotion Exports - Explicit to avoid conflicts with MUI
// =============================================================================

/**
 * Re-export Emotion React utilities.
 *
 * Note: We use explicit exports to avoid conflicts with MUI's re-exports
 * of some Emotion types (CSSObject, Interpolation, Theme, etc.)
 *
 * @example
 * ```typescript
 * import { css, keyframes } from '@brix/shared-runtime-web/ui';
 *
 * const fadeIn = keyframes`
 *   from { opacity: 0; }
 *   to { opacity: 1; }
 * `;
 *
 * const fadeInStyle = css`
 *   animation: ${fadeIn} 0.3s ease-in;
 * `;
 * ```
 */
export {
  css,
  Global,
  keyframes,
  ClassNames,
  jsx as emotionJsx,
  ThemeContext as EmotionThemeContext,
} from '@emotion/react';

/**
 * Re-export Emotion styled as named export.
 *
 * Note: We export as `emotionStyled` to avoid potential conflicts with
 * MUI's styled utility. Use `styled` (default export) for typical usage.
 *
 * @example
 * ```typescript
 * import { styled } from '@brix/shared-runtime-web/ui';
 *
 * const StyledDiv = styled('div')`
 *   padding: 16px;
 *   background: ${({ theme }) => theme.palette.background.paper};
 * `;
 *
 * // Or with MUI component
 * import { Button, styled } from '@brix/shared-runtime-web/ui';
 *
 * const PrimaryButton = styled(Button)(({ theme }) => ({
 *   backgroundColor: theme.palette.primary.main,
 *   '&:hover': {
 *     backgroundColor: theme.palette.primary.dark,
 *   },
 * }));
 * ```
 */
import emotionStyled from '@emotion/styled';

/**
 * Export emotion styled as named export for explicit usage.
 */
export { emotionStyled };

/**
 * Default export for styled.
 * Allows the common pattern: import styled from '@brix/shared-runtime-web/ui'
 *
 * This is the Emotion styled function, compatible with MUI components.
 *
 * @example
 * ```typescript
 * import styled from '@brix/shared-runtime-web/ui';
 * import { Paper } from '@brix/shared-runtime-web/ui';
 *
 * const Card = styled(Paper)`
 *   padding: 24px;
 *   margin: 16px 0;
 * `;
 * ```
 */
export { default as styled } from '@emotion/styled';
