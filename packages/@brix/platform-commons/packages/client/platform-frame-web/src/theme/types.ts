/**
 * @file Theme Type Definitions
 * @description Platform theme related types (theme colors, styles, etc.)
 * @module @brix-sdk/platform-frame-web/theme
 * @version 3.0.0
 * 
 * [Note] OAuth/social login branding was migrated to oauth/ directory
 */

// ============================================================================
// Theme Color Configuration
// ============================================================================

/**
 * Theme Color Configuration
 * 
 * Supports custom colors including primary, secondary, and accent colors
 */
export interface ThemeConfig {
  /**
   * Primary Color
   * Used for main buttons, links, and active elements
   * @default '#007AAD'
   */
  primaryColor: string;
  
  /**
   * Secondary Color
   * Used for backgrounds, borders, and secondary elements
   * @default '#D9E2E9'
   */
  secondaryColor: string;
  
  /**
   * Tertiary Color
   * Used for page backgrounds, card backgrounds
   * @default '#FFFBFC'
   */
  tertiaryColor: string;
  
  /**
   * Success Color
   * @default '#10B981'
   */
  successColor?: string;
  
  /**
   * Warning Color
   * @default '#F59E0B'
   */
  warningColor?: string;
  
  /**
   * Error Color
   * @default '#EF4444'
   */
  errorColor?: string;
  
  /**
   * Info Color
   * @default '#3B82F6'
   */
  infoColor?: string;
}

/**
 * Full Theme Configuration (extends base colors)
 */
export interface FullThemeConfig extends ThemeConfig {
  /**
   * Text Colors
   */
  textPrimary?: string;
  textSecondary?: string;
  textDisabled?: string;
  
  /**
   * Background Colors
   */
  backgroundDefault?: string;
  backgroundPaper?: string;
  
  /**
   * Border Colors
   */
  borderColor?: string;
  dividerColor?: string;
}

// ============================================================================
// Branding Configuration
// ============================================================================

/**
 * Branding Configuration
 * 
 * Used for login page, registration page, and other pages requiring brand display
 */
export interface BrandingConfig {
  /**
   * Application Name
   */
  appName: string;
  
  /**
   * Logo URL (optional)
   */
  logoUrl?: string;
  
  /**
   * Logo Text (displayed when logoUrl is absent)
   */
  logoText?: string;
  
  /**
   * Primary Color (use global theme)
   */
  primaryColor: string;
  
  /**
   * Secondary Color (accent color)
   */
  secondaryColor?: string;
  
  /**
   * Tertiary Color (card background)
   */
  tertiaryColor?: string;
  
  /**
   * Welcome Message
   */
  welcomeMessage?: string;
  
  /**
   * Subtitle
   */
  subtitle?: string;
  
  /**
   * Copyright Information
   */
  copyright?: string;
  
  /**
   * ICP License Number (China region)
   */
  icpNumber?: string;
}
