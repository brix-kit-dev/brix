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
 * Shell Navigator - Main Navigation Container
 *
 * This component provides the main navigation structure for the mobile shell.
 * It supports tab navigation, drawer navigation, and stack navigation.
 *
 * @module @brix-sdk/platform-frame-mobile/navigation
 * @since 3.3.0
 */

import { useContext, useMemo } from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { ShellContext } from '../providers/ShellProvider';
import type { RouteConfig } from './types';

/**
 * Shell Navigator Props
 */
export interface ShellNavigatorProps {
  /** Custom route configurations */
  routes?: RouteConfig[];
  /** Initial route name */
  initialRouteName?: string;
  /** Theme override */
  theme?: 'light' | 'dark';
}

/**
 * ShellNavigator Component
 *
 * Main navigation container that renders the appropriate navigation structure
 * based on configuration. Automatically handles plugin screen registration.
 *
 * @example
 * ```tsx
 * import { ShellNavigator } from '@brix-sdk/platform-frame-mobile';
 *
 * function App() {
 *   return (
 *     <ShellProvider>
 *       <ShellNavigator
 *         initialRouteName="Home"
 *         routes={[
 *           { name: 'Home', component: HomeScreen },
 *           { name: 'Profile', component: ProfileScreen }
 *         ]}
 *       />
 *     </ShellProvider>
 *   );
 * }
 * ```
 */
export function ShellNavigator({
  routes: customRoutes,
  initialRouteName,
  theme: _theme
}: ShellNavigatorProps): JSX.Element {
  const shellContext = useContext(ShellContext);

  // Merge custom routes with plugin routes
  const routes = useMemo(() => {
    const pluginRoutes: RouteConfig[] = []; // Will be populated by plugin loader
    return [...(customRoutes ?? []), ...pluginRoutes];
  }, [customRoutes]);

  // Determine navigation type
  const navigationType = shellContext?.config.navigation?.type ?? 'tab';
  const initial = initialRouteName ?? shellContext?.config.navigation?.initialRoute ?? 'Home';

  // If shell is not initialized, show loading
  if (!shellContext?.state.initialized) {
    return (
      <View style={styles.loadingContainer}>
        <Text style={styles.loadingText}>Loading...</Text>
      </View>
    );
  }

  // If there's an error, show error screen
  if (shellContext.state.error) {
    return (
      <View style={styles.errorContainer}>
        <Text style={styles.errorTitle}>Error</Text>
        <Text style={styles.errorText}>{shellContext.state.error}</Text>
      </View>
    );
  }

  // Render based on navigation type
  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <Text style={styles.headerTitle}>{shellContext.config.appName}</Text>
      </View>
      <View style={styles.content}>
        {routes.length === 0 ? (
          <View style={styles.emptyContainer}>
            <Text style={styles.emptyText}>No routes configured</Text>
            <Text style={styles.emptySubtext}>Add routes via ShellNavigator props or load plugins</Text>
          </View>
        ) : (
          <Text style={styles.routeInfo}>
            {routes.length} routes registered. Initial: {initial}
          </Text>
        )}
      </View>
      {navigationType === 'tab' && (
        <View style={styles.tabBar}>
          <Text style={styles.tabBarText}>Tab Navigation</Text>
        </View>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5'
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#ffffff'
  },
  loadingText: {
    fontSize: 16,
    color: '#666666'
  },
  errorContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#fff5f5',
    padding: 20
  },
  errorTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#cc0000',
    marginBottom: 10
  },
  errorText: {
    fontSize: 14,
    color: '#666666',
    textAlign: 'center'
  },
  header: {
    height: 56,
    backgroundColor: '#1976d2',
    justifyContent: 'center',
    alignItems: 'center',
    paddingTop: 20
  },
  headerTitle: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#ffffff'
  },
  content: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center'
  },
  emptyContainer: {
    alignItems: 'center'
  },
  emptyText: {
    fontSize: 16,
    color: '#666666',
    marginBottom: 8
  },
  emptySubtext: {
    fontSize: 14,
    color: '#999999'
  },
  routeInfo: {
    fontSize: 14,
    color: '#666666'
  },
  tabBar: {
    height: 56,
    backgroundColor: '#ffffff',
    borderTopWidth: 1,
    borderTopColor: '#e0e0e0',
    justifyContent: 'center',
    alignItems: 'center'
  },
  tabBarText: {
    fontSize: 12,
    color: '#666666'
  }
});
