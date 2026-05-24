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
 * @file Theme Store
 * @description Manages theme state
 * @module @brix-sdk/platform-frame-web/theme/ThemeStore
 * @version 3.2.0
 * 
 * [Design Notes]
 * ThemeStore is the core storage for theme state.
 * 
 * [Migration Notes]
 * This file was migrated from @brix/platform-theme-web.
 * 
 * [Architecture Notes]
 * Abstracts storage operations through StorageAdapter, following the Dependency Inversion Principle.
 * The Shell layer does not directly depend on browser localStorage API.
 * 
 * [Responsibilities]
 * 1. Store current theme state
 * 2. Manage theme presets
 * 3. Handle system theme preference
 * 4. Persist theme settings
 */

import type { 
  ThemeMode, 
  ThemeConfig, 
  ThemePreset,
  ThemeState,
  ThemeChangeEvent,
  Unsubscribe,
} from '@brix-sdk/runtime-sdk-api-web';
import { StorageAdapter, LocalStorageAdapter } from '../storage';

/**
 * Theme change listener
 */
export type ThemeChangeListener = (event: ThemeChangeEvent) => void;

/**
 * Theme store configuration
 */
export interface ThemeStoreConfig {
  /**
   * Default theme mode
   * @default 'system'
   */
  defaultMode?: ThemeMode;
  
  /**
   * Initial preset ID
   */
  initialPresetId?: string;
  
  /**
   * Whether to persist
   * @default true
   */
  persist?: boolean;
  
  /**
   * Storage key name
   * @default 'theme'
   */
  storageKey?: string;
  
  /**
   * Preset list
   */
  presets?: ThemePreset[];
  
  /**
   * Storage adapter
   * @default LocalStorageAdapter('brix')
   */
  storage?: StorageAdapter;
}

/**
 * Get system theme preference
 * 
 * @returns the system preferred theme mode
 */
function getSystemPreference(): 'light' | 'dark' {
  if (typeof window === 'undefined') {
    return 'light';
  }
  
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches 
    ? 'dark' 
    : 'light';
}

/**
 * Theme store
 * 
 * Manages the theme state for the entire application.
 * 
 * [Usage Example]
 * ```typescript
 * const themeStore = new ThemeStore({
 *   defaultMode: 'system',
 *   presets: [defaultPreset],
 * });
 * 
 * // Subscribe to changes
 * themeStore.subscribe((event) => {
 *   console.log('Theme change:', event);
 * });
 * 
 * // Toggle mode
 * themeStore.setMode('dark');
 * ```
 */
export class ThemeStore {
  /**
   * Current theme state
   */
  private state: ThemeState;
  
  /**
   * Theme preset list
   */
  private presets: Map<string, ThemePreset> = new Map();
  
  /**
   * Listener list
   */
  private listeners: Set<ThemeChangeListener> = new Set();
  
  /**
   * Configuration
   */
  private config: ThemeStoreConfig;
  
  /**
   * Storage adapter
   */
  private storage: StorageAdapter;
  
  /**
   * System theme change listener
   */
  private mediaQuery: MediaQueryList | null = null;
  
  /**
   * Constructor
   * 
   * @param config - Storage configuration
   */
  constructor(config: ThemeStoreConfig = {}) {
    this.config = {
      defaultMode: 'system',
      persist: true,
      storageKey: 'theme',
      ...config,
    };
    
    // Initialize storage adapter
    this.storage = config.storage ?? new LocalStorageAdapter('brix');
    
    // Register presets
    if (config.presets) {
      config.presets.forEach(preset => this.presets.set(preset.id, preset));
    }
    
    // Try to restore from storage
    const savedState = this.loadState();
    
    // Initialize state
    const mode = savedState?.mode ?? this.config.defaultMode ?? 'system';
    const presetId = savedState?.presetId ?? this.config.initialPresetId;
    const preset = presetId ? this.presets.get(presetId) : undefined;
    const resolvedMode = mode === 'system' ? getSystemPreference() : mode;
    
    this.state = {
      mode,
      resolvedMode,
      config: preset 
        ? (resolvedMode === 'dark' ? preset.dark : preset.light)
        : this.getDefaultConfig(resolvedMode),
      presetId,
    };
    
    // Listen for system theme changes
    this.setupSystemThemeListener();
    
    // Apply initial theme
    this.applyThemeToDocument();
  }
  
  /**
   * Set up system theme change listener
   */
  private setupSystemThemeListener(): void {
    if (typeof window === 'undefined') return;
    
    this.mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
    
    const handler = (e: MediaQueryListEvent) => {
      if (this.state.mode === 'system') {
        this.updateResolvedMode(e.matches ? 'dark' : 'light', 'system');
      }
    };
    
    if (this.mediaQuery.addEventListener) {
      this.mediaQuery.addEventListener('change', handler);
    } else {
      // Compatible with older browsers
      this.mediaQuery.addListener(handler);
    }
  }
  
  /**
   * Update resolved theme mode
   * 
   * @param resolvedMode - Resolved mode
   * @param reason - Change reason
   */
  private updateResolvedMode(
    resolvedMode: 'light' | 'dark', 
    reason: 'user' | 'system' | 'preset'
  ): void {
    const oldState = { ...this.state };
    
    const preset = this.state.presetId 
      ? this.presets.get(this.state.presetId) 
      : undefined;
    
    this.state = {
      ...this.state,
      resolvedMode,
      config: preset 
        ? (resolvedMode === 'dark' ? preset.dark : preset.light)
        : this.getDefaultConfig(resolvedMode),
    };
    
    this.applyThemeToDocument();
    this.saveState();
    this.notifyListeners(oldState, reason);
  }
  
  /**
   * Get default theme configuration
   * 
   * @param mode - Theme mode
   * @returns Default configuration
   */
  private getDefaultConfig(mode: 'light' | 'dark'): ThemeConfig {
    if (mode === 'dark') {
      return {
        colors: {
          primary: '#1890ff',
          secondary: '#722ed1',
          success: '#52c41a',
          warning: '#faad14',
          error: '#ff4d4f',
          info: '#1890ff',
          backgroundDefault: '#141414',
          backgroundPaper: '#1f1f1f',
          textPrimary: 'rgba(255, 255, 255, 0.85)',
          textSecondary: 'rgba(255, 255, 255, 0.45)',
          border: '#434343',
          divider: '#303030',
        },
        borderRadius: 4,
        shadows: {
          sm: '0 1px 2px rgba(0, 0, 0, 0.45)',
          md: '0 3px 6px rgba(0, 0, 0, 0.45)',
          lg: '0 5px 15px rgba(0, 0, 0, 0.45)',
        },
      };
    }
    
    return {
      colors: {
        primary: '#1890ff',
        secondary: '#722ed1',
        success: '#52c41a',
        warning: '#faad14',
        error: '#ff4d4f',
        info: '#1890ff',
        backgroundDefault: '#f0f2f5',
        backgroundPaper: '#ffffff',
        textPrimary: 'rgba(0, 0, 0, 0.85)',
        textSecondary: 'rgba(0, 0, 0, 0.45)',
        border: '#d9d9d9',
        divider: '#f0f0f0',
      },
      borderRadius: 4,
      shadows: {
        sm: '0 1px 2px rgba(0, 0, 0, 0.03)',
        md: '0 3px 6px rgba(0, 0, 0, 0.05)',
        lg: '0 5px 15px rgba(0, 0, 0, 0.1)',
      },
    };
  }
  
  /**
   * Apply theme to document
   */
  private applyThemeToDocument(): void {
    if (typeof document === 'undefined') return;
    
    const { config, resolvedMode } = this.state;
    const root = document.documentElement;
    
    // Set data-theme attribute
    root.setAttribute('data-theme', resolvedMode);
    
    // Set color variables
    const { colors } = config;
    if (colors.primary) root.style.setProperty('--brix-color-primary', colors.primary);
    if (colors.secondary) root.style.setProperty('--brix-color-secondary', colors.secondary);
    if (colors.success) root.style.setProperty('--brix-color-success', colors.success);
    if (colors.warning) root.style.setProperty('--brix-color-warning', colors.warning);
    if (colors.error) root.style.setProperty('--brix-color-error', colors.error);
    if (colors.info) root.style.setProperty('--brix-color-info', colors.info);
    if (colors.backgroundDefault) root.style.setProperty('--brix-color-background', colors.backgroundDefault);
    if (colors.backgroundPaper) root.style.setProperty('--brix-color-surface', colors.backgroundPaper);
    if (colors.textPrimary) root.style.setProperty('--brix-color-text', colors.textPrimary);
    if (colors.textSecondary) root.style.setProperty('--brix-color-text-secondary', colors.textSecondary);
    if (colors.border) root.style.setProperty('--brix-color-border', colors.border);
    if (colors.divider) root.style.setProperty('--brix-color-divider', colors.divider);
    
    // Set border radius variables
    if (config.borderRadius !== undefined) {
      root.style.setProperty('--brix-radius-small', `${config.borderRadius / 2}px`);
      root.style.setProperty('--brix-radius-medium', `${config.borderRadius}px`);
      root.style.setProperty('--brix-radius-large', `${config.borderRadius * 2}px`);
    }
    
    // Set shadow variables
    if (config.shadows) {
      const { sm, md, lg } = config.shadows;
      if (sm) root.style.setProperty('--brix-shadow-small', sm);
      if (md) root.style.setProperty('--brix-shadow-medium', md);
      if (lg) root.style.setProperty('--brix-shadow-large', lg);
    }
  }
  
  /**
   * Notify listeners
   * 
   * @param oldState - Old state
   * @param reason - Change reason
   */
  private notifyListeners(
    oldState: ThemeState, 
    reason: 'user' | 'system' | 'preset'
  ): void {
    const event: ThemeChangeEvent = {
      mode: this.state.mode,
      resolvedMode: this.state.resolvedMode,
      previousMode: oldState.mode,
      config: this.state.config,
      source: reason === 'preset' ? 'api' : reason,
      timestamp: Date.now(),
    };
    
    this.listeners.forEach(listener => {
      try {
        listener(event);
      } catch (error) {
        console.error('[ThemeStore] Listener error:', error);
      }
    });
  }
  
  /**
   * Save state to storage
   */
  private saveState(): void {
    if (!this.config.persist) return;
    
    try {
      this.storage.set(this.config.storageKey!, {
        mode: this.state.mode,
        presetId: this.state.presetId,
      });
    } catch (error) {
      console.warn('[ThemeStore] Failed to save state:', error);
    }
  }
  
  /**
   * Load state from storage
   * 
   * @returns Saved state
   */
  private loadState(): { mode?: ThemeMode; presetId?: string } | null {
    if (!this.config.persist) return null;
    
    try {
      return this.storage.get<{ mode?: ThemeMode; presetId?: string }>(this.config.storageKey!);
    } catch (error) {
      console.warn('[ThemeStore] Failed to load state:', error);
      return null;
    }
  }
  
  /**
   * Get current state
   * 
   * @returns Current theme state
   */
  getState(): ThemeState {
    return { ...this.state };
  }
  
  /**
   * Set theme mode
   * 
   * @param mode - Theme mode
   */
  setMode(mode: ThemeMode): void {
    if (this.state.mode === mode) return;
    
    const oldState = { ...this.state };
    const resolvedMode = mode === 'system' ? getSystemPreference() : mode;
    
    const preset = this.state.presetId 
      ? this.presets.get(this.state.presetId) 
      : undefined;
    
    this.state = {
      ...this.state,
      mode,
      resolvedMode,
      config: preset 
        ? (resolvedMode === 'dark' ? preset.dark : preset.light)
        : this.getDefaultConfig(resolvedMode),
    };
    
    this.applyThemeToDocument();
    this.saveState();
    this.notifyListeners(oldState, 'user');
  }
  
  /**
   * Toggle light/dark mode
   */
  toggleMode(): void {
    const newMode = this.state.resolvedMode === 'light' ? 'dark' : 'light';
    this.setMode(newMode);
  }
  
  /**
   * Apply preset
   * 
   * @param presetId - Preset ID
   */
  applyPreset(presetId: string): boolean {
    const preset = this.presets.get(presetId);
    if (!preset) {
      console.warn(`[ThemeStore] Preset does not exist`);
      return false;
    }
    
    const oldState = { ...this.state };
    
    this.state = {
      ...this.state,
      presetId,
      config: this.state.resolvedMode === 'dark' ? preset.dark : preset.light,
    };
    
    this.applyThemeToDocument();
    this.saveState();
    this.notifyListeners(oldState, 'preset');

    return true;
  }
  
  /**
   * Register presets
   * 
   * @param preset - Theme preset
   */
  registerPreset(preset: ThemePreset): void {
    this.presets.set(preset.id, preset);
  }
  
  /**
   * Get all presets
   * 
   * @returns Preset list
   */
  getPresets(): ThemePreset[] {
    return Array.from(this.presets.values());
  }
  
  /**
   * Set CSS variable
   * 
   * @param name - Variable name (without --)
   * @param value - Variable value
   */
  setCssVariable(name: string, value: string): void {
    if (typeof document === 'undefined') return;
    document.documentElement.style.setProperty(`--${name}`, value);
  }
  
  /**
   * Batch set CSS variables
   * 
   * @param variables - Variable key-value pairs
   */
  setCssVariables(variables: Record<string, string>): void {
    Object.entries(variables).forEach(([name, value]) => {
      this.setCssVariable(name, value);
    });
  }
  
  /**
   * Get CSS variable value
   * 
   * @param name - Variable name (without --)
   * @returns Variable value
   */
  getCssVariable(name: string): string {
    if (typeof document === 'undefined') return '';
    return getComputedStyle(document.documentElement).getPropertyValue(`--${name}`).trim();
  }
  
  /**
   * Subscribe to state changes
   * 
   * @param listener - Change listener
   * @returns Unsubscribe function
   */
  subscribe(listener: ThemeChangeListener): Unsubscribe {
    this.listeners.add(listener);
    
    return () => {
      this.listeners.delete(listener);
    };
  }
  
  /**
   * Destroy store
   */
  destroy(): void {
    this.listeners.clear();
  }
}







