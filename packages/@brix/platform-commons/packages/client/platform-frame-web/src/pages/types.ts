/**
 * @file Shell Page Type Definitions
 * @description Core type definitions for Web host package
 * @module @brix-sdk/platform-frame-web/pages/types
 * @version 3.0.0
 */

import type { ReactNode } from 'react';

// ============================================================================
// Common Configuration Types
// ============================================================================

/**
 * Branding Configuration
 */
export interface ShellBranding {
  /**
   * Application name
   */
  appName?: string;
  
  /**
   * Logo URL or React node
   */
  logo?: string | ReactNode;
  
  /**
   * Theme color
   */
  primaryColor?: string;
  
  /**
   * Copyright information
   */
  copyright?: string;
}

/**
 * Navigation Service Interface
 */
export interface NavigationService {
  /**
   * Navigate to specified path
   */
  navigate(path: string, options?: { replace?: boolean }): void;
  
  /**
   * Go back to previous page
   */
  goBack(): void;
}

/**
 * Route Configuration
 */
export interface ShellRoutes {
  /**
   * Home page path
   * @default '/dashboard'
   */
  homePath?: string;
  
  /**
   * Login page path
   * @default '/login'
   */
  loginPath?: string;
  
  /**
   * 404 page path
   * @default '/404'
   */
  notFoundPath?: string;
  
  /**
   * Unauthorized page path
   * @default '/unauthorized'
   */
  unauthorizedPath?: string;
}

// ============================================================================
// Dashboard Page Types
// ============================================================================

/**
 * Stat Card Data
 */
export interface StatCard {
  /**
   * Unique identifier
   */
  key: string;
  
  /**
   * Title
   */
  title: string;
  
  /**
   * Value
   */
  value: string | number;
  
  /**
   * Change value
   */
  change?: {
    value: number;
    type: 'increase' | 'decrease';
  };
  
  /**
   * Icon (emoji or React node)
   */
  icon?: string | ReactNode;
  
  /**
   * Color
   */
  color?: string;
}

/**
 * Quick Action
 */
export interface QuickAction {
  /**
   * Unique identifier
   */
  key: string;
  
  /**
   * Title
   */
  title: string;
  
  /**
   * Description
   */
  description?: string;
  
  /**
   * Route path
   */
  path: string;
  
  /**
   * Icon
   */
  icon?: string | ReactNode;
}

/**
 * Dashboard Data Provider
 */
export interface DashboardDataProvider {
  /**
   * Get stat card data
   */
  getStats(): Promise<StatCard[]>;
  
  /**
   * Get quick actions
   */
  getQuickActions(): Promise<QuickAction[]>;
  
  /**
   * Get welcome message
   */
  getWelcomeMessage?(): Promise<string>;
}

/**
 * Dashboard Page Configuration
 */
export interface DashboardPageConfig {
  /**
   * Navigation service
   */
  navigationService: NavigationService;
  
  /**
   * Data provider (optional, uses default data if not provided)
   */
  dataProvider?: DashboardDataProvider;
  
  /**
   * Branding configuration
   */
  branding?: ShellBranding;
  
  /**
   * Welcome message
   */
  welcomeMessage?: string;
  
  /**
   * Username (for displaying greeting)
   */
  username?: string;
  
  /**
   * Static stat cards (if not using dataProvider)
   */
  stats?: StatCard[];
  
  /**
   * Static quick actions (if not using dataProvider)
   */
  quickActions?: QuickAction[];
  
  /**
   * Custom header content
   */
  header?: ReactNode;
  
  /**
   * Custom footer content
   */
  footer?: ReactNode;
}

/**
 * Simplified Dashboard Configuration
 */
export interface SimpleDashboardConfig {
  /**
   * Navigation callback
   */
  onNavigate: (path: string) => void;
  
  /**
   * Branding configuration
   */
  branding?: ShellBranding;
  
  /**
   * Welcome message
   */
  welcomeMessage?: string;
  
  /**
   * Username
   */
  username?: string;
  
  /**
   * Stat cards
   */
  stats?: StatCard[];
  
  /**
   * Quick actions
   */
  quickActions?: QuickAction[];
}

// ============================================================================
// Error Page Types
// ============================================================================

/**
 * Error Page Configuration
 */
export interface ErrorPageConfig {
  /**
   * Navigation service
   */
  navigationService: NavigationService;
  
  /**
   * Route configuration
   */
  routes?: ShellRoutes;
  
  /**
   * Branding configuration
   */
  branding?: ShellBranding;
}

/**
 * Simplified Error Page Configuration
 */
export interface SimpleErrorPageConfig {
  /**
   * Go home callback
   */
  onGoHome: () => void;
  
  /**
   * Go back callback
   */
  onGoBack?: () => void;
  
  /**
   * Re-login callback (only for 403 page)
   */
  onReLogin?: () => void;
  
  /**
   * Branding configuration
   */
  branding?: ShellBranding;
}
