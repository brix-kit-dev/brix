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
 * App Layout - Main Application Layout Component
 *
 * This component provides the structural layout for the mobile application,
 * including safe area handling, status bar configuration, and content area.
 *
 * @module @brix-sdk/platform-frame-mobile/layouts
 * @since 3.3.0
 */


import type { ReactNode } from 'react';
import { View, StyleSheet, StatusBar, Platform } from 'react-native';

/**
 * App Layout Props
 */
export interface AppLayoutProps {
  /** Child components */
  children: ReactNode;
  /** Background color */
  backgroundColor?: string;
  /** Status bar style */
  statusBarStyle?: 'light-content' | 'dark-content' | 'default';
  /** Whether to show status bar */
  showStatusBar?: boolean;
  /** Safe area edges to apply */
  safeAreaEdges?: ('top' | 'bottom' | 'left' | 'right')[];
}

/**
 * AppLayout Component
 *
 * Root layout component that handles safe areas, status bar,
 * and overall application structure.
 *
 * @example
 * ```tsx
 * <AppLayout
 *   backgroundColor="#ffffff"
 *   statusBarStyle="dark-content"
 *   safeAreaEdges={['top', 'bottom']}
 * >
 *   <Content />
 * </AppLayout>
 * ```
 */
export function AppLayout({
  children,
  backgroundColor = '#ffffff',
  statusBarStyle = 'dark-content',
  showStatusBar = true,
  safeAreaEdges = ['top', 'bottom']
}: AppLayoutProps): JSX.Element {
  const safeAreaStyle = {
    paddingTop: safeAreaEdges.includes('top') ? (Platform.OS === 'ios' ? 44 : StatusBar.currentHeight ?? 24) : 0,
    paddingBottom: safeAreaEdges.includes('bottom') ? (Platform.OS === 'ios' ? 34 : 0) : 0,
    paddingLeft: safeAreaEdges.includes('left') ? 16 : 0,
    paddingRight: safeAreaEdges.includes('right') ? 16 : 0
  };

  return (
    <View style={[styles.container, { backgroundColor }]}>
      {showStatusBar && (
        <StatusBar
          barStyle={statusBarStyle}
          backgroundColor={backgroundColor}
          translucent={Platform.OS === 'android'}
        />
      )}
      <View style={[styles.content, safeAreaStyle]}>
        {children}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1
  },
  content: {
    flex: 1
  }
});
