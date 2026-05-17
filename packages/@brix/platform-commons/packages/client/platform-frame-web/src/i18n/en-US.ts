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
 * @file English (US) translations for enterprise-frame-web
 * @description Shell-level UI text: error pages, dashboard, placeholders, OAuth buttons
 * @module @brix-sdk/platform-frame-web/i18n/en-US
 * @version 3.3.0
 */

const enUS: Record<string, string> = {
  // ── Error Pages ───────────────────────────────────────────────────────
  'frame.error.404.title': 'Page Not Found',
  'frame.error.404.message': 'Sorry, the page you visited does not exist or has been removed.',
  'frame.error.404.goHome': 'Go Home',
  'frame.error.404.goBack': 'Go Back',

  'frame.error.403.title': 'Access Denied',
  'frame.error.403.message': 'Sorry, you do not have permission to access this page. Please contact the administrator.',
  'frame.error.403.goHome': 'Go Home',
  'frame.error.403.reLogin': 'Re-Login',

  // ── Dashboard ─────────────────────────────────────────────────────────
  'frame.dashboard.welcome': 'Welcome to {appName}',
  'frame.dashboard.quickActions': 'Quick Actions',
  'frame.dashboard.stats.totalUsers': 'Total Users',
  'frame.dashboard.stats.activeSessions': 'Active Sessions',
  'frame.dashboard.stats.totalOrders': 'Total Orders',
  'frame.dashboard.stats.monthlyRevenue': 'Monthly Revenue',
  'frame.dashboard.stats.changeFromLastWeek': 'from last week',
  'frame.dashboard.actions.booking': 'Booking',
  'frame.dashboard.actions.booking.desc': 'Manage bookings',
  'frame.dashboard.actions.products': 'Products',
  'frame.dashboard.actions.products.desc': 'Manage products',
  'frame.dashboard.actions.partners': 'Partners',
  'frame.dashboard.actions.partners.desc': 'Manage partners',
  'frame.dashboard.actions.reports': 'Reports',
  'frame.dashboard.actions.reports.desc': 'View analytics',

  // ── Placeholder Page ──────────────────────────────────────────────────
  'frame.placeholder.status': 'Coming Soon',
  'frame.placeholder.description': 'This feature is currently under development.',

  // ── OAuth / Google Sign-In ────────────────────────────────────────────
  'frame.oauth.google.signinWith': 'Sign in with Google',
  'frame.oauth.google.signupWith': 'Sign up with Google',
  'frame.oauth.google.continueWith': 'Continue with Google',
  'frame.oauth.google.signin': 'Sign in',
};

export default enUS;
