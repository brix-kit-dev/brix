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
 * @file createDashboardPage Factory Function
 * @description Creates pre-assembled Dashboard page
 * @module @brix-sdk/platform-frame-web/pages/DashboardPage
 * @version 3.0.0
 * 
 * [Architecture Notes]
 * Dashboard page is provided by the capability layer; Host only needs configuration to use it.
 * Supports:
 * - Statistics card display
 * - Quick action entry points
 * - Custom branding configuration
 * - Dynamic data loading
 */

import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useUIOptional, useI18n } from '@brix-sdk/runtime-sdk-react';
import type {
  DashboardPageConfig,
  SimpleDashboardConfig,
  StatCard,
  QuickAction,
} from '../types';
import { generateDashboardStyles, DEFAULT_PRIMARY_COLOR } from '../styles';

/**
 * Default statistics cards (titles use i18n keys resolved at render time)
 */
const DEFAULT_STATS: StatCard[] = [
  {
    key: 'total-users',
    title: 'frame.dashboard.stats.totalUsers',
    value: '1,234',
    change: { value: 12.5, type: 'increase' },
    icon: '👥',
    color: '#4f46e5',
  },
  {
    key: 'active-sessions',
    title: 'frame.dashboard.stats.activeSessions',
    value: '856',
    change: { value: 8.2, type: 'increase' },
    icon: '📊',
    color: '#059669',
  },
  {
    key: 'total-orders',
    title: 'frame.dashboard.stats.totalOrders',
    value: '3,456',
    change: { value: 3.1, type: 'decrease' },
    icon: '📦',
    color: '#d97706',
  },
  {
    key: 'revenue',
    title: 'frame.dashboard.stats.monthlyRevenue',
    value: '¥89,240',
    change: { value: 15.3, type: 'increase' },
    icon: '💰',
    color: '#dc2626',
  },
];

/**
 * Default quick actions (titles and descriptions are i18n keys resolved at render time)
 */
const DEFAULT_QUICK_ACTIONS: QuickAction[] = [
  {
    key: 'booking',
    title: 'frame.dashboard.actions.booking',
    description: 'frame.dashboard.actions.booking.desc',
    path: '/booking',
    icon: '📅',
  },
  {
    key: 'products',
    title: 'frame.dashboard.actions.products',
    description: 'frame.dashboard.actions.products.desc',
    path: '/products',
    icon: '🛍️',
  },
  {
    key: 'partners',
    title: 'frame.dashboard.actions.partners',
    description: 'frame.dashboard.actions.partners.desc',
    path: '/partners',
    icon: '🤝',
  },
  {
    key: 'reports',
    title: 'frame.dashboard.actions.reports',
    description: 'frame.dashboard.actions.reports.desc',
    path: '/reports',
    icon: '📈',
  },
];

/**
 * Get greeting based on time of day
 */
function getGreeting(): string {
  const hour = new Date().getHours();
  if (hour < 6) return 'Good night';
  if (hour < 12) return 'Good morning';
  if (hour < 14) return 'Good afternoon';
  if (hour < 18) return 'Good afternoon';
  return 'Good evening';
}

/**
 * Create pre-assembled Dashboard page
 */
export function createDashboardPage(config: DashboardPageConfig): React.FC {
  const {
    navigationService,
    dataProvider,
    branding,
    welcomeMessage,
    username,
    stats: staticStats,
    quickActions: staticQuickActions,
    header,
    footer,
  } = config;
  
  const primaryColor = branding?.primaryColor || DEFAULT_PRIMARY_COLOR;
  
  const DashboardPage: React.FC = () => {
    const [stats, setStats] = useState<StatCard[]>(staticStats || DEFAULT_STATS);
    const [quickActions, setQuickActions] = useState<QuickAction[]>(staticQuickActions || DEFAULT_QUICK_ACTIONS);
    const [greeting, setGreeting] = useState<string>('');
    
    // I18n: resolve translation keys at render time
    const { t } = useI18n('frame');
    
    // Get UIAdapter components if available (optional - graceful degradation)
    const ui = useUIOptional();
    const tokens = ui?.getThemeTokens();
    
    // Load data
    useEffect(() => {
      if (dataProvider) {
        dataProvider.getStats().then(setStats);
        dataProvider.getQuickActions().then(setQuickActions);
        if (dataProvider.getWelcomeMessage) {
          dataProvider.getWelcomeMessage().then(setGreeting);
        }
      }
    }, []);
    
    // Generate greeting
    const displayGreeting = useMemo(() => {
      if (greeting) return greeting;
      if (welcomeMessage) return welcomeMessage;
      const greet = getGreeting();
      return username ? `${greet}，${username}` : greet;
    }, [greeting]);
    
    // Handle quick action click
    const handleQuickAction = useCallback((path: string) => {
      navigationService.navigate(path);
    }, []);
    
    // Generate styles
    const styles = useMemo(() => generateDashboardStyles(primaryColor), [primaryColor]);
    
    return (
      <>
        <style>{styles}</style>
        <div className="shell-dashboard">
          {header}
          
          {/* Page header */}
          <header className="shell-dashboard-header">
            <h1 className="shell-dashboard-welcome">{displayGreeting}</h1>
            <p className="shell-dashboard-subtitle">
              {t('dashboard.welcome', { appName: branding?.appName || 'Brix Platform' })}
            </p>
          </header>
          
          {/* Statistics cards */}
          <section className="shell-dashboard-stats">
            {stats.map((stat, index) => (
              ui?.Card ? (
                <ui.Card
                  key={stat.key}
                  style={{
                    padding: '20px',
                    borderRadius: '12px',
                    background: index === 0 
                      ? `linear-gradient(135deg, ${tokens?.primary || stat.color} 0%, ${tokens?.primaryDark || stat.color} 100%)`
                      : '#fff',
                    color: index === 0 ? '#fff' : 'inherit',
                    boxShadow: '0 2px 8px rgba(0,0,0,0.08)',
                  }}
                >
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
                    <div>
                      <div style={{ fontSize: '13px', opacity: index === 0 ? 0.9 : 0.6, marginBottom: '8px' }}>
                        {t(stat.title)}
                      </div>
                      <div style={{ fontSize: '24px', fontWeight: 600 }}>
                        {stat.value}
                      </div>
                      {stat.change && (
                        <div style={{ 
                          fontSize: '12px', 
                          color: index === 0 ? 'rgba(255,255,255,0.8)' : (stat.change.type === 'increase' ? tokens?.success || '#2e7d32' : tokens?.error || '#d32f2f'),
                          marginTop: '4px',
                        }}>
                          {stat.change.type === 'increase' ? '↑' : '↓'} {stat.change.value}% from last week
                        </div>
                      )}
                    </div>
                    {ui?.Icon && (
                      <ui.Icon 
                        name={stat.icon?.toString() || 'analytics'} 
                        size="large" 
                        color={index === 0 ? 'rgba(255,255,255,0.6)' : tokens?.textSecondary} 
                      />
                    )}
                  </div>
                </ui.Card>
              ) : (
                <div key={stat.key} className="shell-stat-card">
                  <div className="shell-stat-card-header">
                    <div
                      className="shell-stat-card-icon"
                      style={{ backgroundColor: `${stat.color}20` }}
                    >
                      {stat.icon}
                    </div>
                    {stat.change && (
                      <span className={`shell-stat-card-change ${stat.change.type}`}>
                        {stat.change.type === 'increase' ? '↑' : '↓'} {stat.change.value}%
                      </span>
                    )}
                  </div>
                  <div className="shell-stat-card-value">{stat.value}</div>
                  <div className="shell-stat-card-title">{t(stat.title)}</div>
                </div>
              )
            ))}
          </section>
          
          {/* Quick actions */}
          <section className="shell-dashboard-section">
            <h2 className="shell-dashboard-section-title">{t('dashboard.quickActions')}</h2>
            <div className="shell-quick-actions">
              {quickActions.map((action) => (
                ui?.Button ? (
                  <ui.Button
                    key={action.key}
                    variant="secondary"
                    onClick={() => handleQuickAction(action.path)}
                    style={{
                      flexDirection: 'column',
                      padding: '16px 24px',
                      height: 'auto',
                      minWidth: '140px',
                    }}
                  >
                    {ui?.Icon && (
                      <ui.Icon name={action.icon?.toString() || 'apps'} size="large" />
                    )}
                    <span style={{ marginTop: '8px', fontWeight: 500 }}>{t(action.title)}</span>
                    {action.description && (
                      <span style={{ marginTop: '4px', fontSize: '12px', opacity: 0.7 }}>
                        {t(action.description)}
                      </span>
                    )}
                  </ui.Button>
                ) : (
                  <button
                    key={action.key}
                    className="shell-quick-action"
                    onClick={() => handleQuickAction(action.path)}
                  >
                    <div className="shell-quick-action-icon">{action.icon}</div>
                    <div className="shell-quick-action-title">{t(action.title)}</div>
                    {action.description && (
                      <div className="shell-quick-action-desc">{t(action.description)}</div>
                    )}
                  </button>
                )
              ))}
            </div>
          </section>
          
          {footer}
        </div>
      </>
    );
  };
  
  DashboardPage.displayName = 'PrebuiltDashboardPage';
  
  return DashboardPage;
}

/**
 * Create simplified Dashboard page
 */
export function createSimpleDashboardPage(config: SimpleDashboardConfig): React.FC {
  const {
    onNavigate,
    branding,
    welcomeMessage,
    username,
    stats,
    quickActions,
  } = config;
  
  return createDashboardPage({
    navigationService: {
      navigate: onNavigate,
      goBack: () => window.history.back(),
    },
    branding,
    welcomeMessage,
    username,
    stats,
    quickActions,
  });
}
