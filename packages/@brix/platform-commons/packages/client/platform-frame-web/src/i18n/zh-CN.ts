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
 * @file Chinese (Simplified) translations for enterprise-frame-web
 * @description Shell-level UI text: error pages, dashboard, placeholders, OAuth buttons
 * @module @brix-sdk/platform-frame-web/i18n/zh-CN
 * @version 3.3.0
 */

const zhCN: Record<string, string> = {
  // ── Error Pages ───────────────────────────────────────────────────────
  'frame.error.404.title': '页面未找到',
  'frame.error.404.message': '抱歉，您访问的页面不存在或已被移除。',
  'frame.error.404.goHome': '返回首页',
  'frame.error.404.goBack': '返回上一页',

  'frame.error.403.title': '访问被拒绝',
  'frame.error.403.message': '抱歉，您没有权限访问此页面。请联系管理员获取访问权限。',
  'frame.error.403.goHome': '返回首页',
  'frame.error.403.reLogin': '重新登录',

  // ── Dashboard ─────────────────────────────────────────────────────────
  'frame.dashboard.welcome': '欢迎使用 {appName}',
  'frame.dashboard.quickActions': '快捷操作',
  'frame.dashboard.stats.totalUsers': '总用户数',
  'frame.dashboard.stats.activeSessions': '活跃会话',
  'frame.dashboard.stats.totalOrders': '订单数',
  'frame.dashboard.stats.monthlyRevenue': '本月收入',
  'frame.dashboard.stats.changeFromLastWeek': '较上周',
  'frame.dashboard.actions.booking': '预约管理',
  'frame.dashboard.actions.booking.desc': '管理客户预约',
  'frame.dashboard.actions.products': '产品管理',
  'frame.dashboard.actions.products.desc': '管理产品信息',
  'frame.dashboard.actions.partners': '合作伙伴',
  'frame.dashboard.actions.partners.desc': '管理合作伙伴',
  'frame.dashboard.actions.reports': '数据报表',
  'frame.dashboard.actions.reports.desc': '查看统计报表',

  // ── Placeholder Page ──────────────────────────────────────────────────
  'frame.placeholder.status': '功能开发中',
  'frame.placeholder.description': '此功能正在开发中，敬请期待。',

  // ── OAuth / Google Sign-In ────────────────────────────────────────────
  'frame.oauth.google.signinWith': '使用 Google 账号登录',
  'frame.oauth.google.signupWith': '使用 Google 账号注册',
  'frame.oauth.google.continueWith': '继续使用 Google',
  'frame.oauth.google.signin': '登录',
};

export default zhCN;
