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
 * Tab Navigator - Bottom Tab Navigation Layout
 *
 * This component provides a bottom tab navigation structure
 * for organizing screens into tabs.
 *
 * @module @brix-sdk/platform-frame-mobile/layouts
 * @since 3.3.0
 */

import { useState, useCallback, useMemo } from 'react';
import type { ReactNode } from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';

/**
 * Tab Configuration
 */
export interface TabConfig {
  /** Tab identifier */
  key: string;
  /** Tab label */
  label: string;
  /** Tab icon name */
  icon?: string;
  /** Tab badge */
  badge?: string | number;
  /** Tab content renderer */
  render: () => ReactNode;
}

/**
 * Tab Navigator Props
 */
export interface TabNavigatorProps {
  /** Tab configurations */
  tabs: TabConfig[];
  /** Initial active tab key */
  initialTab?: string;
  /** Tab bar style */
  tabBarStyle?: 'light' | 'dark';
  /** Callback when tab changes */
  onTabChange?: (tabKey: string) => void;
}

/**
 * TabNavigator Component
 *
 * Renders a bottom tab navigation structure with customizable tabs.
 *
 * @example
 * ```tsx
 * <TabNavigator
 *   tabs={[
 *     { key: 'home', label: 'Home', render: () => <HomeScreen /> },
 *     { key: 'profile', label: 'Profile', render: () => <ProfileScreen /> }
 *   ]}
 *   initialTab="home"
 *   onTabChange={(key) => console.log('Tab changed:', key)}
 * />
 * ```
 */
export function TabNavigator({
  tabs,
  initialTab,
  tabBarStyle = 'light',
  onTabChange
}: TabNavigatorProps): JSX.Element {
  const [activeTab, setActiveTab] = useState(initialTab ?? tabs[0]?.key ?? '');

  const handleTabPress = useCallback((key: string) => {
    setActiveTab(key);
    onTabChange?.(key);
  }, [onTabChange]);

  const activeTabConfig = useMemo(
    () => tabs.find(tab => tab.key === activeTab),
    [tabs, activeTab]
  );

  const tabBarStyles = tabBarStyle === 'dark'
    ? styles.tabBarDark
    : styles.tabBarLight;

  return (
    <View style={styles.container}>
      {/* Content Area */}
      <View style={styles.content}>
        {activeTabConfig?.render()}
      </View>

      {/* Tab Bar */}
      <View style={[styles.tabBar, tabBarStyles]}>
        {tabs.map(tab => (
          <TouchableOpacity
            key={tab.key}
            style={styles.tabItem}
            onPress={() => handleTabPress(tab.key)}
            accessibilityRole="tab"
            accessibilityState={{ selected: activeTab === tab.key }}
          >
            <Text
              style={[
                styles.tabLabel,
                activeTab === tab.key && styles.tabLabelActive
              ]}
            >
              {tab.label}
            </Text>
            {tab.badge !== undefined && (
              <View style={styles.badge}>
                <Text style={styles.badgeText}>{tab.badge}</Text>
              </View>
            )}
          </TouchableOpacity>
        ))}
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
  },
  tabBar: {
    flexDirection: 'row',
    height: 56,
    borderTopWidth: 1
  },
  tabBarLight: {
    backgroundColor: '#ffffff',
    borderTopColor: '#e0e0e0'
  },
  tabBarDark: {
    backgroundColor: '#1e1e1e',
    borderTopColor: '#333333'
  },
  tabItem: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    position: 'relative'
  },
  tabLabel: {
    fontSize: 12,
    color: '#666666'
  },
  tabLabelActive: {
    color: '#1976d2',
    fontWeight: '600'
  },
  badge: {
    position: 'absolute',
    top: 4,
    right: 20,
    backgroundColor: '#f44336',
    borderRadius: 8,
    minWidth: 16,
    height: 16,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 4
  },
  badgeText: {
    color: '#ffffff',
    fontSize: 10,
    fontWeight: 'bold'
  }
});
