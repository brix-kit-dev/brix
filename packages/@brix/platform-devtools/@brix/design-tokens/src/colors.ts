/**
 * @file colors.ts
 * @description Color Tokens - MUI Standard Color Palette
 * @version 3.2.0
 *
 * [v3.2.0 Changes]
 * - Updated to MUI (Material UI) standard color palette
 * - Added layout-specific colors (sidebar, header) for Shell layer support
 * - Added contrast text colors for accessibility
 * - Aligned with UIAdapter ThemeTokens contract in runtime-sdk-api-web
 *
 * [Design Principles]
 * - Follow Material Design color guidelines
 * - Ensure WCAG AA contrast ratios for text on backgrounds
 * - Support both light and dark themes
 * - Provide layout-specific colors for consistent Shell layer styling
 */

// ============================================================================
// Brand Colors (MUI Standard)
// ============================================================================

/**
 * Brand Colors - MUI Standard Primary and Secondary Palette
 *
 * <p>Primary: MUI Blue (#1976d2) - Main brand color for primary actions</p>
 * <p>Secondary: MUI Purple (#9c27b0) - Accent color for secondary elements</p>
 */
export const brandColors = {
  // Primary Color - MUI Blue
  primary: '#1976d2',
  primaryLight: '#42a5f5',
  primaryDark: '#1565c0',
  primaryContrastText: '#ffffff',

  // Secondary Color - MUI Purple
  secondary: '#9c27b0',
  secondaryLight: '#ba68c8',
  secondaryDark: '#7b1fa2',
  secondaryContrastText: '#ffffff',

  // Accent Color - MUI Amber (for highlights, optional use)
  accent: '#ff9800',
  accentLight: '#ffb74d',
  accentDark: '#f57c00',
};

// ============================================================================
// Semantic Colors (MUI Standard)
// ============================================================================

/**
 * Semantic Colors - MUI Standard Status Colors
 *
 * <p>Used for feedback and status indication throughout the application.</p>
 */
export const semanticColors = {
  // Success - MUI Green
  success: '#2e7d32',
  successLight: '#4caf50',
  successDark: '#1b5e20',

  // Warning - MUI Orange
  warning: '#ed6c02',
  warningLight: '#ff9800',
  warningDark: '#e65100',

  // Error - MUI Red
  error: '#d32f2f',
  errorLight: '#ef5350',
  errorDark: '#c62828',

  // Info - MUI Light Blue
  info: '#0288d1',
  infoLight: '#03a9f4',
  infoDark: '#01579b',
};

// ============================================================================
// Neutral Colors
// ============================================================================

/**
 * Neutral Colors - Grayscale Palette
 *
 * <p>Used for backgrounds, borders, text, and general UI elements.</p>
 */
export const neutralColors = {
  white: '#ffffff',
  black: '#000000',
  gray50: '#fafafa',
  gray100: '#f5f5f5',
  gray200: '#eeeeee',
  gray300: '#e0e0e0',
  gray400: '#bdbdbd',
  gray500: '#9e9e9e',
  gray600: '#757575',
  gray700: '#616161',
  gray800: '#424242',
  gray900: '#212121',
};

// ============================================================================
// Layout Colors (Shell Layer Support)
// ============================================================================

/**
 * Layout Colors - Sidebar and Header Specific Colors
 *
 * <p>These colors are used by Shell layer components (AppSidebar, AppHeader)
 * to maintain consistent styling across different UI adapter implementations.</p>
 *
 * <p>v3.2.0 Addition: Aligned with UIAdapter ThemeTokens contract.</p>
 */
export const layoutColors = {
  // Sidebar Colors (Dark sidebar style)
  sidebarBackground: '#1e293b',
  sidebarText: 'rgba(255, 255, 255, 0.87)',
  sidebarActiveBackground: '#1976d2',
  sidebarHoverBackground: 'rgba(255, 255, 255, 0.08)',
  sidebarBorder: 'rgba(255, 255, 255, 0.12)',

  // Header Colors (Light header style)
  headerBackground: '#ffffff',
  headerText: 'rgba(0, 0, 0, 0.87)',
  headerBorder: 'rgba(0, 0, 0, 0.12)',
};

/**
 * Dark Theme Layout Colors
 *
 * <p>Layout colors adjusted for dark theme.</p>
 */
export const darkLayoutColors = {
  // Sidebar Colors (Darker sidebar for dark theme)
  sidebarBackground: '#0f172a',
  sidebarText: 'rgba(255, 255, 255, 0.87)',
  sidebarActiveBackground: '#1e40af',
  sidebarHoverBackground: 'rgba(255, 255, 255, 0.08)',
  sidebarBorder: 'rgba(255, 255, 255, 0.12)',

  // Header Colors (Dark header for dark theme)
  headerBackground: '#1e1e1e',
  headerText: 'rgba(255, 255, 255, 0.87)',
  headerBorder: 'rgba(255, 255, 255, 0.12)',
};

// ============================================================================
// Light Theme (Complete)
// ============================================================================

/**
 * Light Theme - Complete color set for light mode
 *
 * <p>Includes brand colors, semantic colors, neutral colors, and layout colors.</p>
 */
export const lightTheme = {
  // Brand Colors
  ...brandColors,

  // Semantic Colors
  ...semanticColors,

  // Surface Colors
  background: '#f5f5f5',
  paper: neutralColors.white,
  surface: neutralColors.gray50,

  // Text Colors (MUI standard rgba values for proper contrast)
  text: 'rgba(0, 0, 0, 0.87)',
  textPrimary: 'rgba(0, 0, 0, 0.87)',
  textSecondary: 'rgba(0, 0, 0, 0.6)',
  textDisabled: 'rgba(0, 0, 0, 0.38)',
  textMuted: 'rgba(0, 0, 0, 0.38)',

  // Border and Divider
  border: 'rgba(0, 0, 0, 0.12)',
  divider: 'rgba(0, 0, 0, 0.12)',

  // Layout Colors
  ...layoutColors,
};

// ============================================================================
// Dark Theme (Complete)
// ============================================================================

/**
 * Dark Theme - Complete color set for dark mode
 *
 * <p>Includes brand colors (adjusted), semantic colors (adjusted),
 * neutral colors, and layout colors.</p>
 */
export const darkTheme = {
  // Brand Colors (adjusted for dark mode - lighter variants for visibility)
  primary: '#90caf9',
  primaryLight: '#e3f2fd',
  primaryDark: '#42a5f5',
  primaryContrastText: 'rgba(0, 0, 0, 0.87)',

  secondary: '#ce93d8',
  secondaryLight: '#f3e5f5',
  secondaryDark: '#ab47bc',
  secondaryContrastText: 'rgba(0, 0, 0, 0.87)',

  accent: '#ffb74d',
  accentLight: '#ffe0b2',
  accentDark: '#ff9800',

  // Semantic Colors (adjusted for dark mode)
  success: '#66bb6a',
  successLight: '#81c784',
  successDark: '#388e3c',

  warning: '#ffa726',
  warningLight: '#ffb74d',
  warningDark: '#f57c00',

  error: '#f44336',
  errorLight: '#e57373',
  errorDark: '#d32f2f',

  info: '#29b6f6',
  infoLight: '#4fc3f7',
  infoDark: '#0288d1',

  // Surface Colors
  background: '#121212',
  paper: '#1e1e1e',
  surface: '#1e1e1e',

  // Text Colors (MUI standard rgba values for dark mode)
  text: 'rgba(255, 255, 255, 0.87)',
  textPrimary: 'rgba(255, 255, 255, 0.87)',
  textSecondary: 'rgba(255, 255, 255, 0.6)',
  textDisabled: 'rgba(255, 255, 255, 0.38)',
  textMuted: 'rgba(255, 255, 255, 0.38)',

  // Border and Divider
  border: 'rgba(255, 255, 255, 0.12)',
  divider: 'rgba(255, 255, 255, 0.12)',

  // Layout Colors (dark theme)
  ...darkLayoutColors,
};

// ============================================================================
// Color Aggregates and Utilities
// ============================================================================

/**
 * Color Aggregate - All color tokens organized by category
 */
export const colors = {
  brand: brandColors,
  semantic: semanticColors,
  neutral: neutralColors,
  layout: layoutColors,
  darkLayout: darkLayoutColors,
  light: lightTheme,
  dark: darkTheme,
};

/**
 * Theme Type
 */
export type Theme = 'light' | 'dark';

/**
 * Get Theme Colors by Theme Mode
 *
 * <p>Returns the complete color set for the specified theme mode.</p>
 *
 * @param theme - Theme mode ('light' | 'dark')
 * @returns Complete theme color object
 *
 * @example
 * ```typescript
 * const colors = getThemeColors('dark');
 * console.log(colors.primary); // '#90caf9'
 * ```
 */
export function getThemeColors(theme: Theme) {
  return theme === 'dark' ? darkTheme : lightTheme;
}

/**
 * Get Layout Colors by Theme Mode
 *
 * <p>Returns layout-specific colors for Shell layer components.</p>
 *
 * @param theme - Theme mode ('light' | 'dark')
 * @returns Layout color object
 *
 * @example
 * ```typescript
 * const layout = getLayoutColors('light');
 * console.log(layout.sidebarBackground); // '#1e293b'
 * ```
 */
export function getLayoutColors(theme: Theme) {
  return theme === 'dark' ? darkLayoutColors : layoutColors;
}

/**
 * Add Opacity to Color
 *
 * <p>Appends alpha channel to a hex color string.</p>
 *
 * @param color - Hex color string (e.g., '#1976d2')
 * @param opacity - Opacity value between 0 and 1
 * @returns Color with alpha channel appended
 *
 * @example
 * ```typescript
 * const semiTransparent = withOpacity('#1976d2', 0.5);
 * // Returns '#1976d280'
 * ```
 */
export function withOpacity(color: string, opacity: number): string {
  const clampedOpacity = Math.max(0, Math.min(1, opacity));
  const alphaHex = Math.round(clampedOpacity * 255)
    .toString(16)
    .padStart(2, '0');
  return `${color}${alphaHex}`;
}
