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
 * @file Default Theme Preset
 * @description Provides default light/dark theme preset
 * @module @brix-sdk/platform-frame-web/theme/presets/defaultPreset
 * @version 3.1.0
 * 
 * [Merge Notes]
 * This file was merged from @brix/platform-theme-web.
 * 
 * [Design Notes]
 * Implements v3.0 architecture blueprint task 4.6-2.
 * Semantic colors are imported from @brix-sdk/platform-design-tokens to ensure cross-component consistency.
 */

import type { ThemePreset } from '@brix-sdk/runtime-sdk-api-web';
import { semanticColors, neutralColors, fontFamily } from '@brix-sdk/platform-design-tokens';

/**
 * Default theme preset
 * 
 * Based on Ant Design 5.0 design language; semantic colors from @brix-sdk/platform-design-tokens.
 */
export const defaultPreset: ThemePreset = {
  id: 'default',
  name: 'Default Theme',
  description: 'Default theme based on Ant Design 5.0 design language',
  
  light: {
    colors: {
      primary: '#1677ff',
      secondary: '#722ed1',
      success: semanticColors.success,
      warning: semanticColors.warning,
      error: semanticColors.error,
      info: semanticColors.info,
      backgroundDefault: neutralColors.gray100,
      backgroundPaper: neutralColors.white,
      textPrimary: 'rgba(0, 0, 0, 0.88)',
      textSecondary: 'rgba(0, 0, 0, 0.45)',
      border: neutralColors.gray300,
      divider: 'rgba(5, 5, 5, 0.06)',
    },
    borderRadius: 6,
    shadows: {
      sm: '0 1px 2px 0 rgba(0, 0, 0, 0.03), 0 1px 6px -1px rgba(0, 0, 0, 0.02), 0 2px 4px 0 rgba(0, 0, 0, 0.02)',
      md: '0 3px 6px -4px rgba(0, 0, 0, 0.12), 0 6px 16px 0 rgba(0, 0, 0, 0.08), 0 9px 28px 8px rgba(0, 0, 0, 0.05)',
      lg: '0 6px 16px -8px rgba(0, 0, 0, 0.08), 0 9px 28px 0 rgba(0, 0, 0, 0.05), 0 12px 48px 16px rgba(0, 0, 0, 0.03)',
    },
    fontFamily: fontFamily.sans.join(', '),
    fontSize: 14,
  },
  
  dark: {
    colors: {
      primary: '#1668dc',
      secondary: '#9254de',
      success: semanticColors.successDark,
      warning: semanticColors.warningDark,
      error: semanticColors.errorDark,
      info: semanticColors.infoDark,
      backgroundDefault: neutralColors.black,
      backgroundPaper: neutralColors.gray900,
      textPrimary: 'rgba(255, 255, 255, 0.85)',
      textSecondary: 'rgba(255, 255, 255, 0.45)',
      border: neutralColors.gray600,
      divider: 'rgba(253, 253, 253, 0.12)',
    },
    borderRadius: 6,
    shadows: {
      sm: '0 1px 2px 0 rgba(0, 0, 0, 0.45), 0 1px 6px -1px rgba(0, 0, 0, 0.35), 0 2px 4px 0 rgba(0, 0, 0, 0.35)',
      md: '0 3px 6px -4px rgba(0, 0, 0, 0.48), 0 6px 16px 0 rgba(0, 0, 0, 0.32), 0 9px 28px 8px rgba(0, 0, 0, 0.2)',
      lg: '0 6px 16px -8px rgba(0, 0, 0, 0.32), 0 9px 28px 0 rgba(0, 0, 0, 0.2), 0 12px 48px 16px rgba(0, 0, 0, 0.12)',
    },
    fontFamily: fontFamily.sans.join(', '),
    fontSize: 14,
  },
};
