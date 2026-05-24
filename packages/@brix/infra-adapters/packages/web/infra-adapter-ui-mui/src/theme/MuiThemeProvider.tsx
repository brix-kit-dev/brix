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
 * @file MUI Theme Provider
 * @description Material UI theme provider and token utilities.
 *              Bridges UIAdapter ThemeTokens to MUI Theme system.
 * @module @brix-sdk/infra-adapter-ui-mui/theme/MuiThemeProvider
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
} from '@brix-sdk/runtime-sdk-api-web';
import {
  BRIX_LIGHT_THEME_TOKENS,
  BRIX_DARK_THEME_TOKENS,
} from '@brix-sdk/platform-design-tokens';

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
  const outlinedLabelXOffset = 14;
  const outlinedLabelShrinkYOffset = -9;
  const inputLabelLineHeightRatio = 1.4375;
  const mediumInputLabelYOffset =
    (tokens.controlHeightMedium - tokens.fontSizeMedium * inputLabelLineHeightRatio) / 2;
  const smallInputLabelYOffset =
    (tokens.controlHeightSmall - tokens.fontSizeSmall * inputLabelLineHeightRatio) / 2;

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
      fontFamily: tokens.fontFamily,
      fontSize: tokens.fontSizeMedium,
      button: {
        fontSize: tokens.fontSizeMedium,
      },
      body1: {
        fontSize: tokens.fontSizeMedium,
      },
      body2: {
        fontSize: tokens.fontSizeSmall,
      },
    },
    components: {
      // Customize default MUI components for consistency
      MuiButton: {
        styleOverrides: {
          root: {
            textTransform: 'none', // Disable uppercase transform
            borderRadius: tokens.borderRadiusMedium,
          },
          sizeSmall: {
            minHeight: tokens.controlHeightSmall,
          },
          sizeMedium: {
            minHeight: tokens.controlHeightMedium,
          },
          sizeLarge: {
            minHeight: tokens.controlHeightLarge,
          },
        },
      },
      MuiTextField: {
        defaultProps: {
          variant: 'outlined',
          size: 'medium',
        },
      },
      MuiInputLabel: {
        styleOverrides: {
          root: {
            fontSize: tokens.fontSizeMedium,
          },
          sizeSmall: {
            fontSize: tokens.fontSizeSmall,
          },
          outlined: {
            lineHeight: inputLabelLineHeightRatio,
            transform: `translate(${outlinedLabelXOffset}px, ${mediumInputLabelYOffset}px) scale(1)`,
            '&.MuiInputLabel-sizeSmall': {
              transform: `translate(${outlinedLabelXOffset}px, ${smallInputLabelYOffset}px) scale(1)`,
            },
            '&.MuiInputLabel-shrink': {
              transform: `translate(${outlinedLabelXOffset}px, ${outlinedLabelShrinkYOffset}px) scale(0.75)`,
            },
            '&.MuiInputLabel-shrink.MuiInputLabel-sizeSmall': {
              transform: `translate(${outlinedLabelXOffset}px, ${outlinedLabelShrinkYOffset}px) scale(0.75)`,
            },
          },
        },
      },
      MuiOutlinedInput: {
        styleOverrides: {
          root: {
            borderRadius: tokens.borderRadiusMedium,
            alignItems: 'center',
            // Default (medium) density
            minHeight: tokens.controlHeightMedium,
            fontSize: tokens.fontSizeMedium,
            '&.MuiInputBase-sizeSmall': {
              minHeight: tokens.controlHeightSmall,
              fontSize: tokens.fontSizeSmall,
            },
            '&.MuiInputBase-multiline': {
              alignItems: 'flex-start',
              paddingTop: 10,
              paddingBottom: 10,
            },
          },
          input: {
            paddingTop: 0,
            paddingBottom: 0,
            height: tokens.controlHeightMedium,
            lineHeight: `${tokens.controlHeightMedium}px`,
            boxSizing: 'border-box',
            '&.MuiInputBase-inputSizeSmall': {
              height: tokens.controlHeightSmall,
              lineHeight: `${tokens.controlHeightSmall}px`,
            },
            '&.MuiInputBase-inputMultiline': {
              height: 'auto',
              lineHeight: 1.5,
              paddingTop: 0,
              paddingBottom: 0,
            },
          },
        },
      },
      MuiInputBase: {
        styleOverrides: {
          root: {
            fontSize: tokens.fontSizeMedium,
            alignItems: 'center',
          },
          input: {
            '&::placeholder': {
              lineHeight: 'inherit',
            },
          },
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
let currentThemeTokens: ThemeTokens = BRIX_LIGHT_THEME_TOKENS;
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
    const baseTokens = mode === 'light' ? BRIX_LIGHT_THEME_TOKENS : BRIX_DARK_THEME_TOKENS;
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
