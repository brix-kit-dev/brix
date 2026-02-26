/**
 * @file MUI Theme Provider
 * @description Material UI theme provider and token utilities.
 *              Bridges UIAdapter ThemeTokens to MUI Theme system.
 * @module @brix/infra-adapter-ui-mui/theme/MuiThemeProvider
 * @version 3.1.0
 *
 * [Design Principles]
 * - Converts ThemeTokens to MUI createTheme format
 * - Supports light and dark modes
 * - Provides theme context for all MUI components
 * - CssBaseline for consistent cross-browser defaults
 *
 * [Architectural Position - v3.0.4 Blueprint]
 * This is part of the theme system in infra-adapters layer.
 * Shell layer wraps the application with this provider.
 */

import type { FC } from 'react';
import { useMemo, createContext, useContext, useState } from 'react';
import {
  ThemeProvider as MuiThemeProvider_,
  createTheme,
  type Theme,
} from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import type {
  ThemeTokens,
  ThemeProviderProps,
} from '@brix/runtime-sdk-api-web';
import {
  MUI_THEME_TOKENS,
  MUI_DARK_THEME_TOKENS,
} from '@brix/runtime-sdk-api-web';

// ============================================================================
// Theme Context
// ============================================================================

/**
 * Theme Mode Context
 *
 * <p>Provides access to current theme mode and toggle function.</p>
 */
interface ThemeModeContextValue {
  mode: 'light' | 'dark';
  toggleMode: () => void;
}

const ThemeModeContext = createContext<ThemeModeContextValue>({
  mode: 'light',
  toggleMode: () => {},
});

/**
 * Hook to access theme mode controls
 *
 * @returns Current mode and toggle function
 */
export function useThemeMode(): ThemeModeContextValue {
  return useContext(ThemeModeContext);
}

// ============================================================================
// Theme Creation
// ============================================================================

/**
 * Creates MUI Theme from ThemeTokens
 *
 * <p>Converts the UIAdapter ThemeTokens format to MUI's createTheme options.
 * This bridges the abstraction layer between our token system and MUI.</p>
 *
 * @param tokens - ThemeTokens from UIAdapter contract
 * @param mode - Theme mode (light or dark)
 * @returns MUI Theme object
 */
export function createMuiTheme(tokens: ThemeTokens, mode: 'light' | 'dark'): Theme {
  return createTheme({
    palette: {
      mode,
      primary: {
        main: tokens.primary,
        light: tokens.primaryLight,
        dark: tokens.primaryDark,
        contrastText: tokens.primaryContrastText,
      },
      secondary: {
        main: tokens.secondary,
        light: tokens.secondaryLight,
        dark: tokens.secondaryDark,
        contrastText: tokens.secondaryContrastText,
      },
      error: {
        main: tokens.error,
      },
      warning: {
        main: tokens.warning,
      },
      info: {
        main: tokens.info,
      },
      success: {
        main: tokens.success,
      },
      background: {
        default: tokens.background,
        paper: tokens.paper,
      },
      text: {
        primary: tokens.textPrimary,
        secondary: tokens.textSecondary,
        disabled: tokens.textDisabled,
      },
      divider: tokens.divider,
    },
    shape: {
      borderRadius: tokens.borderRadiusMedium,
    },
    typography: {
      fontFamily: '"Inter", "Roboto", "Helvetica", "Arial", sans-serif',
    },
    components: {
      // Customize default MUI components for consistency
      MuiButton: {
        styleOverrides: {
          root: {
            textTransform: 'none', // Disable uppercase transform
            borderRadius: tokens.borderRadiusMedium,
          },
        },
      },
      MuiTextField: {
        defaultProps: {
          variant: 'outlined',
          size: 'medium',
        },
      },
      MuiCard: {
        styleOverrides: {
          root: {
            borderRadius: tokens.borderRadiusLarge,
          },
        },
      },
      MuiDialog: {
        styleOverrides: {
          paper: {
            borderRadius: tokens.borderRadiusLarge,
          },
        },
      },
    },
  });
}

// ============================================================================
// Current Theme Tokens State
// ============================================================================

/**
 * Global state for current theme tokens
 *
 * <p>Used by getMuiThemeTokens() to return the active tokens.
 * Updated when MuiThemeProvider renders with different props.</p>
 */
let currentThemeTokens: ThemeTokens = MUI_THEME_TOKENS;
let currentThemeMode: 'light' | 'dark' = 'light';

/**
 * Get Current Theme Tokens
 *
 * <p>Returns the currently active theme tokens.
 * This is used by components that need direct token access.</p>
 *
 * @returns Current ThemeTokens
 */
export function getMuiThemeTokens(): ThemeTokens {
  return currentThemeTokens;
}

/**
 * Get Current Theme Mode
 *
 * <p>Returns the current theme mode ('light' or 'dark').
 * Useful for conditional styling based on active mode.</p>
 *
 * @returns Current theme mode
 */
export function getMuiThemeMode(): 'light' | 'dark' {
  return currentThemeMode;
}

// ============================================================================
// Theme Provider Component
// ============================================================================

/**
 * Extended Theme Provider Props
 *
 * <p>Adds custom token override capability.</p>
 */
interface MuiThemeProviderPropsExtended extends ThemeProviderProps {
  /**
   * Custom theme tokens override
   *
   * <p>If provided, these tokens are used instead of defaults.</p>
   */
  tokens?: Partial<ThemeTokens>;
}

/**
 * MUI Theme Provider Component
 *
 * <p>Material UI implementation of ThemeProviderProps from UIAdapter contract.
 * Wraps the application with MUI theme context and CSS baseline.</p>
 *
 * <h3>Features:</h3>
 * <ul>
 *   <li>Light and dark mode support</li>
 *   <li>CSS baseline for consistent defaults</li>
 *   <li>Theme token customization</li>
 *   <li>Theme mode toggle via context</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <p>This provider is typically used at the Host layer application root.
 * Shell layer components receive theme via MUI's useTheme hook.</p>
 *
 * @example
 * ```tsx
 * // Basic usage
 * <MuiThemeProvider theme="light">
 *   <App />
 * </MuiThemeProvider>
 *
 * // Dark mode
 * <MuiThemeProvider theme="dark">
 *   <App />
 * </MuiThemeProvider>
 *
 * // With custom tokens
 * <MuiThemeProvider
 *   theme="light"
 *   tokens={{ primary: '#7c3aed' }}
 * >
 *   <App />
 * </MuiThemeProvider>
 * ```
 *
 * @param props - Theme provider props including children and mode
 * @returns MUI ThemeProvider wrapped application
 */
export const MuiThemeProvider: FC<MuiThemeProviderPropsExtended> = ({
  children,
  theme: initialMode = 'light',
  tokens: customTokens,
}) => {
  // Internal state for theme mode
  const [mode, setMode] = useState<'light' | 'dark'>(initialMode);

  // Toggle function for theme mode
  const toggleMode = useMemo(
    () => () => {
      setMode((prev) => (prev === 'light' ? 'dark' : 'light'));
    },
    []
  );

  // Build final tokens based on mode and custom overrides
  const tokens = useMemo(() => {
    const baseTokens = mode === 'light' ? MUI_THEME_TOKENS : MUI_DARK_THEME_TOKENS;
    const merged = customTokens ? { ...baseTokens, ...customTokens } : baseTokens;
    
    // Update global state for getMuiThemeTokens()
    currentThemeTokens = merged;
    currentThemeMode = mode;
    
    return merged;
  }, [mode, customTokens]);

  // Create MUI theme from tokens
  const muiTheme = useMemo(() => createMuiTheme(tokens, mode), [tokens, mode]);

  // Context value for theme mode access
  const contextValue = useMemo(
    () => ({ mode, toggleMode }),
    [mode, toggleMode]
  );

  return (
    <ThemeModeContext.Provider value={contextValue}>
      <MuiThemeProvider_ theme={muiTheme}>
        <CssBaseline />
        {children}
      </MuiThemeProvider_>
    </ThemeModeContext.Provider>
  );
};

export default MuiThemeProvider;
