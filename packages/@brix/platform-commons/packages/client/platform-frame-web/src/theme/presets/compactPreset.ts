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
 * @file Compact Theme Preset
 * @description Provides compact layout theme preset
 * @module @brix-sdk/platform-frame-web/theme/presets/compactPreset
 * @version 3.0.0
 * 
 * [Merge Notes]
 * This file was merged from @brix/platform-theme-web.
 */

import type { ThemePreset } from '@brix-sdk/runtime-sdk-api-web';

/**
 * Compact theme preset
 * 
 * Suitable for data-intensive applications.
 */
export const compactPreset: ThemePreset = {
  id: 'compact',
  name: 'Compact Theme',
  description: 'Compact theme suitable for data-intensive applications',
  
  light: {
    colors: {
      primary: '#1677ff',
      secondary: '#722ed1',
      success: '#52c41a',
      warning: '#faad14',
      error: '#ff4d4f',
      info: '#1677ff',
      backgroundDefault: '#f0f2f5',
      backgroundPaper: '#ffffff',
      textPrimary: 'rgba(0, 0, 0, 0.88)',
      textSecondary: 'rgba(0, 0, 0, 0.45)',
      border: '#d9d9d9',
      divider: 'rgba(5, 5, 5, 0.06)',
    },
    borderRadius: 4,
    shadows: {
      sm: '0 1px 2px 0 rgba(0, 0, 0, 0.03)',
      md: '0 2px 8px 0 rgba(0, 0, 0, 0.08)',
      lg: '0 4px 16px 0 rgba(0, 0, 0, 0.1)',
    },
    fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'Noto Sans', sans-serif",
    fontSize: 12,
  },
  
  dark: {
    colors: {
      primary: '#1668dc',
      secondary: '#9254de',
      success: '#49aa19',
      warning: '#d89614',
      error: '#dc4446',
      info: '#1668dc',
      backgroundDefault: '#000000',
      backgroundPaper: '#141414',
      textPrimary: 'rgba(255, 255, 255, 0.85)',
      textSecondary: 'rgba(255, 255, 255, 0.45)',
      border: '#424242',
      divider: 'rgba(253, 253, 253, 0.12)',
    },
    borderRadius: 4,
    shadows: {
      sm: '0 1px 2px 0 rgba(0, 0, 0, 0.3)',
      md: '0 2px 8px 0 rgba(0, 0, 0, 0.4)',
      lg: '0 4px 16px 0 rgba(0, 0, 0, 0.5)',
    },
    fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'Noto Sans', sans-serif",
    fontSize: 12,
  },
};
