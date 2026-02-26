/**
 * @file React Navigation Adapter
 * @description Brix UI Mobile navigation routing implementation - based on React Navigation 6.x
 * @module @brix/infra-adapter-navigation-mobile
 * @version 3.0.0
 * 
 * Design Notes:
 * This adapter is the Mobile navigation layer of the v3.0 Runtime Shell architecture.
 * It wraps React Navigation and provides a unified navigation capability interface.
 * 
 * v3.0 Architecture Position:
 * ```
 * ┌─────────────────────────────────────────────────────────────┐
 * │                    Mobile Plugin Layer                      │
 * │    ┌─────────────┐  ┌─────────────┐  ┌─────────────┐       │
 * │    │  Booking    │  │  Products   │  │  Partners   │       │
 * │    │  Plugin     │  │  Plugin     │  │  Plugin     │       │
 * │    └──────┬──────┘  └──────┬──────┘  └──────┬──────┘       │
 * │           │                │                │              │
 * │           ▼                ▼                ▼              │
 * │    ┌─────────────────────────────────────────────────┐     │
 * │    │        NavigationCapability Contract Interface   │     │
 * │    │  - navigateTo(screenId, params)                 │     │
 * │    │  - goBack()                                      │     │
 * │    │  - getCurrentRoute()                             │     │
 * │    └─────────────────────────────────────────────────┘     │
 * │                           │                                │
 * │                           ▼                                │
 * │    ┌─────────────────────────────────────────────────┐     │
 * │    │      RNNavigationAdapter (this adapter)          │     │
 * │    │  - Screen registration and management            │     │
 * │    │  - Navigation state tracking                     │     │
 * │    │  - Deep Link handling                            │     │
 * │    └─────────────────────────────────────────────────┘     │
 * │                           │                                │
 * │                           ▼                                │
 * │    ┌─────────────────────────────────────────────────┐     │
 * │    │         React Navigation Native Stack            │     │
 * │    └─────────────────────────────────────────────────┘     │
 * └─────────────────────────────────────────────────────────────┘
 * ```
 * 
 * Mobile Navigation Strategies:
 * - Use ScreenId instead of direct screen names
 * - Host maintains the complete navigation stack
 * - Plugins can only navigate to registered screens
 * - Cross-plugin navigation goes through Host
 * 
 * v3.0 Boundary Constraints:
 * ❌ Plugins must NOT directly operate Navigation Container
 * ❌ Plugins must NOT access other plugins' navigation state
 * ❌ Plugins must NOT register global Deep Link handlers
 * ✅ Plugins declare navigation intent through NavigationCapability
 * ✅ Navigation permissions are controlled by Host
 * 
 * Usage Example (Host layer only):
 * ```typescript
 * import { RNNavigationAdapter } from '@brix/infra-adapter-navigation-mobile';
 * 
 * const adapter = new RNNavigationAdapter({
 *   screenRegistry: registry,
 *   onNavigate: (event) => console.log('Navigation:', event),
 * });
 * 
 * // Use in NavigationContainer
 * <NavigationContainer ref={adapter.navigationRef}>
 *   {adapter.renderScreens()}
 * </NavigationContainer>
 * ```
 */

import type { ComponentType } from 'react';
import { createRef } from 'react';
import type { NavigationContainerRef, ParamListBase } from '@react-navigation/native';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';

// ========== Type Definitions ==========

/**
 * Screen unique identifier type
 * Format: `{pluginId}:{screenName}`
 * Example: `booking:detail`, `products:list`
 */
export type ScreenId = `${string}:${string}`;

/**
 * Screen metadata
 */
export interface ScreenMetadata {
  /** Screen unique identifier */
  screenId: ScreenId;
  /** Owner plugin ID */
  pluginId: string;
  /** Screen name */
  name: string;
  /** Screen component */
  component: ComponentType<NativeStackScreenProps<ParamListBase>>;
  /** Screen title */
  title?: string;
  /** Screen options */
  options?: ScreenOptions;
  /** Deep Link path */
  deepLinkPath?: string;
}

/**
 * Screen configuration options
 */
export interface ScreenOptions {
  /** Whether to show Header */
  headerShown?: boolean;
  /** Header title */
  headerTitle?: string;
  /** Whether fullscreen mode */
  fullScreen?: boolean;
  /** Whether gesture is enabled */
  gestureEnabled?: boolean;
  /** Animation type */
  animation?: 'default' | 'fade' | 'slide_from_right' | 'slide_from_bottom' | 'none';
  /** Whether can go back */
  canGoBack?: boolean;
}

/**
 * Screen registry interface
 */
export interface ScreenRegistry {
  /** Get screen metadata */
  getScreen(screenId: ScreenId): ScreenMetadata | undefined;
  /** Get all registered screens */
  getAllScreens(): ScreenMetadata[];
  /** Check if screen is registered */
  hasScreen(screenId: ScreenId): boolean;
  /** Get all screens for a plugin */
  getPluginScreens(pluginId: string): ScreenMetadata[];
}

/**
 * Navigation params type
 */
export interface NavigationParams {
  /** Target screen ID */
  screenId: ScreenId;
  /** Parameters to pass */
  params?: Record<string, unknown>;
  /** Navigation mode */
  mode?: 'push' | 'replace' | 'reset';
}

/**
 * Navigation event
 */
export interface NavigationEvent {
  /** Event type */
  type: 'navigate' | 'goBack' | 'reset' | 'deepLink';
  /** Source plugin ID */
  sourcePluginId?: string;
  /** Target screen ID */
  targetScreenId?: ScreenId;
  /** Navigation params */
  params?: Record<string, unknown>;
  /** Event timestamp */
  timestamp: number;
}

/**
 * Navigation state
 */
export interface NavigationState {
  /** Current screen ID */
  currentScreenId: ScreenId | null;
  /** Current plugin ID */
  currentPluginId: string | null;
  /** Navigation stack depth */
  stackDepth: number;
  /** Whether can go back */
  canGoBack: boolean;
}

/**
 * RNNavigationAdapter configuration options
 */
export interface RNNavigationAdapterOptions {
  /** Screen registry */
  screenRegistry: ScreenRegistry;
  /** Initial screen ID */
  initialScreenId?: ScreenId;
  /** Navigation event callback */
  onNavigate?: (event: NavigationEvent) => void;
  /** Navigation state change callback */
  onStateChange?: (state: NavigationState) => void;
  /** Deep Link prefixes */
  deepLinkPrefixes?: string[];
  /** Navigation permission check */
  canNavigate?: (sourcePluginId: string, targetScreenId: ScreenId) => boolean;
}

// ========== Core Implementation ==========

/**
 * React Navigation Adapter
 * 
 * Responsibilities:
 * - Manage screen registration and navigation
 * - Provide navigation capability abstraction
 * - Implement navigation state tracking
 * - Handle Deep Links
 * 
 * Internal Implementation:
 * - Use NavigationContainerRef to control navigation
 * - Maintain mapping from Screen ID to React Navigation Screen
 * - Support cross-plugin navigation permission control
 * 
 * @example
 * ```typescript
 * const adapter = new RNNavigationAdapter({
 *   screenRegistry: registry,
 *   initialScreenId: 'home:main',
 *   onNavigate: (event) => {
 *     analytics.track('navigation', event);
 *   },
 * });
 * 
 * // Navigate to specified screen
 * adapter.navigateTo('booking:detail', { bookingId: '123' });
 * ```
 */
export class RNNavigationAdapter {
  /** Navigation Container reference */
  public readonly navigationRef = createRef<NavigationContainerRef<ParamListBase>>();
  
  /** Screen registry */
  private readonly screenRegistry: ScreenRegistry;
  
  /** Configuration options */
  private readonly options: RNNavigationAdapterOptions;
  
  /** Current navigation state */
  private currentState: NavigationState = {
    currentScreenId: null,
    currentPluginId: null,
    stackDepth: 0,
    canGoBack: false,
  };
  
  /** ScreenId to React Navigation screen name mapping */
  private readonly screenNameMap: Map<ScreenId, string> = new Map();
  
  /** React Navigation screen name to ScreenId reverse mapping */
  private readonly reverseScreenNameMap: Map<string, ScreenId> = new Map();

  /**
   * Create RNNavigationAdapter instance
   * 
   * @param options - Adapter configuration
   */
  constructor(options: RNNavigationAdapterOptions) {
    this.options = options;
    this.screenRegistry = options.screenRegistry;
    this.initializeScreenMaps();
  }

  /**
   * Navigate to specified screen
   * 
   * Navigation Flow:
   * 1. Verify target screen exists
   * 2. Check navigation permission
   * 3. Execute navigation operation
   * 4. Trigger navigation event
   * 
   * @param screenId - Target screen ID
   * @param params - Parameters to pass
   * @param mode - Navigation mode
   * @returns Whether navigation succeeded
   * 
   * @example
   * ```typescript
   * adapter.navigateTo('booking:detail', { bookingId: '123' });
   * ```
   */
  navigateTo(
    screenId: ScreenId,
    params?: Record<string, unknown>,
    mode: 'push' | 'replace' | 'reset' = 'push'
  ): boolean {
    // 1. Verify screen exists
    const screenMetadata = this.screenRegistry.getScreen(screenId);
    if (!screenMetadata) {
      console.warn(`[RNNavigationAdapter] Screen not found: ${screenId}`);
      return false;
    }

    // 2. Check navigation permission
    if (this.options.canNavigate) {
      const sourcePluginId = this.currentState.currentPluginId || 'host';
      if (!this.options.canNavigate(sourcePluginId, screenId)) {
        console.warn(
          `[RNNavigationAdapter] Navigation denied: ${sourcePluginId} -> ${screenId}`
        );
        return false;
      }
    }

    // 3. Get React Navigation screen name
    const screenName = this.screenNameMap.get(screenId);
    if (!screenName) {
      console.error(`[RNNavigationAdapter] Screen name not found: ${screenId}`);
      return false;
    }

    // 4. Execute navigation
    const navigation = this.navigationRef.current;
    if (!navigation) {
      console.error('[RNNavigationAdapter] Navigation ref not ready');
      return false;
    }

    try {
      switch (mode) {
        case 'push':
          // Use dispatch + CommonActions.navigate to avoid strict type checking
          navigation.dispatch({
            type: 'NAVIGATE',
            payload: { name: screenName, params },
          });
          break;
        case 'replace':
          // In React Navigation 6, replace needs to use CommonActions
          navigation.dispatch({
            type: 'REPLACE',
            payload: { name: screenName, params },
          });
          break;
        case 'reset':
          // Use type assertion to avoid React Navigation strict type checking
          navigation.reset({
            index: 0,
            routes: [{ name: screenName, params }] as unknown as never[],
          });
          break;
      }

      // 5. Trigger navigation event
      this.emitNavigationEvent({
        type: mode === 'reset' ? 'reset' : 'navigate',
        sourcePluginId: this.currentState.currentPluginId || undefined,
        targetScreenId: screenId,
        params,
        timestamp: Date.now(),
      });

      return true;
    } catch (error) {
      console.error('[RNNavigationAdapter] Navigation failed:', error);
      return false;
    }
  }

  /**
   * Go back to previous screen
   * 
   * @returns Whether go back succeeded
   */
  goBack(): boolean {
    const navigation = this.navigationRef.current;
    if (!navigation || !this.currentState.canGoBack) {
      return false;
    }

    try {
      navigation.goBack();
      this.emitNavigationEvent({
        type: 'goBack',
        sourcePluginId: this.currentState.currentPluginId || undefined,
        timestamp: Date.now(),
      });
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Get current navigation state
   * 
   * @returns Current navigation state
   */
  getNavigationState(): NavigationState {
    return { ...this.currentState };
  }

  /**
   * Get current screen ID
   * 
   * @returns Current screen ID or null
   */
  getCurrentScreenId(): ScreenId | null {
    return this.currentState.currentScreenId;
  }

  /**
   * Handle Deep Link
   * 
   * @param url - Deep Link URL
   * @returns Whether handling succeeded
   */
  handleDeepLink(url: string): boolean {
    // Parse Deep Link URL
    const screenId = this.resolveDeepLinkToScreenId(url);
    if (!screenId) {
      return false;
    }

    // Extract params
    const params = this.extractDeepLinkParams(url);

    // Trigger navigation
    const success = this.navigateTo(screenId, params, 'push');

    if (success) {
      this.emitNavigationEvent({
        type: 'deepLink',
        targetScreenId: screenId,
        params,
        timestamp: Date.now(),
      });
    }

    return success;
  }

  /**
   * Navigation state change handler
   * 
   * For use with NavigationContainer's onStateChange
   */
  handleNavigationStateChange = (): void => {
    const navigation = this.navigationRef.current;
    if (!navigation) return;

    const currentRoute = navigation.getCurrentRoute();
    if (!currentRoute) return;

    const screenId = this.reverseScreenNameMap.get(currentRoute.name);
    const screenMetadata = screenId ? this.screenRegistry.getScreen(screenId) : undefined;

    const newState: NavigationState = {
      currentScreenId: screenId || null,
      currentPluginId: screenMetadata?.pluginId || null,
      stackDepth: navigation.getState()?.routes?.length || 0,
      canGoBack: navigation.canGoBack(),
    };

    this.currentState = newState;
    this.options.onStateChange?.(newState);
  };

  /**
   * Get Deep Link configuration
   * 
   * For use with NavigationContainer's linking configuration
   */
  getLinkingConfig(): object {
    const screens: Record<string, string> = {};
    
    this.screenRegistry.getAllScreens().forEach((screen) => {
      if (screen.deepLinkPath) {
        const screenName = this.screenNameMap.get(screen.screenId);
        if (screenName) {
          screens[screenName] = screen.deepLinkPath;
        }
      }
    });

    return {
      prefixes: this.options.deepLinkPrefixes || [],
      config: {
        screens,
      },
    };
  }

  // ========== Private Methods ==========

  /**
   * Initialize screen mappings
   */
  private initializeScreenMaps(): void {
    this.screenRegistry.getAllScreens().forEach((screen) => {
      // Use pluginID_screenName as the React Navigation screen name
      const screenName = `${screen.pluginId}_${screen.name}`;
      this.screenNameMap.set(screen.screenId, screenName);
      this.reverseScreenNameMap.set(screenName, screen.screenId);
    });
  }

  /**
   * Emit navigation event
   */
  private emitNavigationEvent(event: NavigationEvent): void {
    this.options.onNavigate?.(event);
  }

  /**
   * Resolve Deep Link to ScreenId
   */
  private resolveDeepLinkToScreenId(url: string): ScreenId | null {
    try {
      const parsedUrl = new URL(url);
      const path = parsedUrl.pathname;

      // Find matching screen
      const screens = this.screenRegistry.getAllScreens();
      for (const screen of screens) {
        if (screen.deepLinkPath && this.matchDeepLinkPath(path, screen.deepLinkPath)) {
          return screen.screenId;
        }
      }

      return null;
    } catch {
      return null;
    }
  }

  /**
   * Match Deep Link path
   */
  private matchDeepLinkPath(actualPath: string, pattern: string): boolean {
    // Simple path matching, supports :param format parameters
    const patternParts = pattern.split('/').filter(Boolean);
    const actualParts = actualPath.split('/').filter(Boolean);

    if (patternParts.length !== actualParts.length) {
      return false;
    }

    return patternParts.every((part, index) => {
      return part.startsWith(':') || part === actualParts[index];
    });
  }

  /**
   * Extract Deep Link params
   */
  private extractDeepLinkParams(url: string): Record<string, unknown> {
    try {
      const parsedUrl = new URL(url);
      const params: Record<string, unknown> = {};

      parsedUrl.searchParams.forEach((value, key) => {
        params[key] = value;
      });

      return params;
    } catch {
      return {};
    }
  }
}

// ========== Convenient Type Exports ==========

/**
 * Helper function to create ScreenId
 * 
 * @param pluginId - Plugin ID
 * @param screenName - Screen name
 * @returns ScreenId
 */
export function createScreenId(pluginId: string, screenName: string): ScreenId {
  return `${pluginId}:${screenName}`;
}

/**
 * Parse ScreenId
 * 
 * @param screenId - Screen ID
 * @returns Parsed result
 */
export function parseScreenId(screenId: ScreenId): { pluginId: string; screenName: string } {
  const [pluginId = '', ...rest] = screenId.split(':');
  return {
    pluginId,
    screenName: rest.join(':'),
  };
}
