/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file i18n keys for `@brix-sdk/platform-admin-web`.
 *
 * All page strings are looked up via `useI18n('platform-admin').t(key, fallback)`.
 * Hosts that wish to localise this SDK must register a translation bundle
 * for the `platform-admin` namespace using `useI18n.addResourceBundle()`.
 *
 * The English fallbacks below double as the "source of truth" for translators.
 */

export const I18N_NAMESPACE = 'platform-admin';

export const I18N_KEYS = Object.freeze({
  // Common
  common: {
    loading: ['common.loading', 'Loading…'],
    empty: ['common.empty', 'No data'],
    error: ['common.error', 'Something went wrong'],
    retry: ['common.retry', 'Retry'],
    confirm: ['common.confirm', 'Confirm'],
    cancel: ['common.cancel', 'Cancel'],
    save: ['common.save', 'Save'],
    close: ['common.close', 'Close'],
    refresh: ['common.refresh', 'Refresh'],
    actions: ['common.actions', 'Actions'],
    copy: ['common.copy', 'Copy'],
    copied: ['common.copied', 'Copied to clipboard'],
    apply: ['common.apply', 'Apply'],
    total: ['common.total', 'Total'],
    currentPage: ['common.currentPage', 'Current page'],
  },
  // Login
  login: {
    productName: ['login.productName', 'Brix Platform'],
    consoleName: ['login.consoleName', 'Super Admin Console'],
    accessLabel: ['login.accessLabel', 'Commercial operations access'],
    guardRuntime: ['login.guardRuntime', 'Runtime Shell'],
    guardTenant: ['login.guardTenant', 'Tenant isolation'],
    guardAudit: ['login.guardAudit', 'Audit ready'],
    brandLabel: ['login.brandLabel', 'BRIX FRAMEWORK'],
    tagline: ['login.tagline', 'Composable\nEnterprise Platform'],
    features: ['login.features', 'Modular · Extensible · Scalable'],
    description: [
      'login.description',
      'Build enterprise capabilities with flexible, reusable modules.',
    ],
    poweredBy: ['login.poweredBy', 'A SHINWA platform product'],
    title: ['login.title', 'Platform Sign in'],
    subtitle: ['login.subtitle', 'Super administrator access'],
    username: ['login.username', 'Username'],
    usernamePlaceholder: ['login.usernamePlaceholder', 'Enter username'],
    password: ['login.password', 'Password'],
    passwordPlaceholder: ['login.passwordPlaceholder', 'Enter password'],
    showPassword: ['login.showPassword', 'Show password'],
    hidePassword: ['login.hidePassword', 'Hide password'],
    submit: ['login.submit', 'Sign in'],
    invalidCreds: ['login.invalidCreds', 'Invalid username or password'],
    forcePasswordChange: [
      'login.forcePasswordChange',
      'You must change your password before continuing.',
    ],
    requiredUsername: ['login.requiredUsername', 'Username is required'],
    requiredPassword: ['login.requiredPassword', 'Password is required'],
  },
  // Dashboard
  dashboard: {
    title: ['dashboard.title', 'Platform Dashboard'],
    welcome: ['dashboard.welcome', 'Welcome, {{name}}'],
    roleFallback: ['dashboard.roleFallback', 'Platform Admin'],
    workspaceCount: ['dashboard.workspaceCount', '{{count}} workspaces'],
    sectionTitle: ['dashboard.sectionTitle', 'Operations'],
    openWorkspace: ['dashboard.openWorkspace', 'Open'],
    cardAdmins: ['dashboard.cardAdmins', 'Manage Super Admins'],
    cardAdminsArea: ['dashboard.cardAdminsArea', 'Access'],
    cardAdminsDescription: [
      'dashboard.cardAdminsDescription',
      'Operator accounts, platform roles and privileged access.',
    ],
    cardTenants: ['dashboard.cardTenants', 'Manage Tenants'],
    cardTenantsArea: ['dashboard.cardTenantsArea', 'Tenants'],
    cardTenantsDescription: [
      'dashboard.cardTenantsDescription',
      'Tenant lifecycle, status control and commercial readiness.',
    ],
    cardAudit: ['dashboard.cardAudit', 'Audit Log'],
    cardAuditArea: ['dashboard.cardAuditArea', 'Audit'],
    cardAuditDescription: [
      'dashboard.cardAuditDescription',
      'Security-sensitive actions and platform operation history.',
    ],
    cardChangePassword: ['dashboard.cardChangePassword', 'Change My Password'],
    cardChangePasswordArea: ['dashboard.cardChangePasswordArea', 'Account'],
    cardChangePasswordDescription: [
      'dashboard.cardChangePasswordDescription',
      'Personal credential rotation for the current administrator.',
    ],
  },
  // Admin list
  admin: {
    listTitle: ['admin.listTitle', 'Platform Super Admins'],
    listSubtitle: [
      'admin.listSubtitle',
      'Control privileged operator access, one-time password flows and audited lifecycle actions.',
    ],
    summaryTotal: ['admin.summaryTotal', 'Total admins'],
    summaryActive: ['admin.summaryActive', 'Active on page'],
    summaryDisabled: ['admin.summaryDisabled', 'Disabled on page'],
    summaryPageSize: ['admin.summaryPageSize', '{{count}} shown per page'],
    create: ['admin.create', 'Create Super Admin'],
    colUsername: ['admin.colUsername', 'Username'],
    colEmail: ['admin.colEmail', 'Email'],
    colRole: ['admin.colRole', 'Role'],
    colStatus: ['admin.colStatus', 'Status'],
    colCreatedAt: ['admin.colCreatedAt', 'Created'],
    colLastLogin: ['admin.colLastLogin', 'Last login'],
    actionDisable: ['admin.actionDisable', 'Disable'],
    actionResetPassword: ['admin.actionResetPassword', 'Reset password'],
    statusActive: ['admin.statusActive', 'Active'],
    statusDisabled: ['admin.statusDisabled', 'Disabled'],

    createDialogTitle: ['admin.createDialogTitle', 'Create Super Admin'],
    fieldUsername: ['admin.fieldUsername', 'Username'],
    fieldEmail: ['admin.fieldEmail', 'Email'],
    fieldDisplayName: ['admin.fieldDisplayName', 'Display name'],
    fieldRole: ['admin.fieldRole', 'Role'],
    fieldNotes: ['admin.fieldNotes', 'Notes'],

    tempPasswordTitle: ['admin.tempPasswordTitle', 'One-time Temporary Password'],
    tempPasswordWarning: [
      'admin.tempPasswordWarning',
      'Copy this password NOW. It will not be shown again. The user will be required to change it on first login.',
    ],
    tempPasswordExpires: [
      'admin.tempPasswordExpires',
      'Expires at {{when}} (UTC).',
    ],

    disableDialogTitle: ['admin.disableDialogTitle', 'Disable Super Admin'],
    disableReason: ['admin.disableReason', 'Reason (will be audited)'],
    disableConfirm: [
      'admin.disableConfirm',
      'Disable {{name}}? They will be signed out immediately and cannot sign back in.',
    ],

    resetConfirm: [
      'admin.resetConfirm',
      'Generate a new one-time password for {{name}}? Their current password will be invalidated.',
    ],

    requiredField: ['admin.requiredField', 'This field is required'],
    invalidEmail: ['admin.invalidEmail', 'Invalid email format'],
    invalidUsername: [
      'admin.invalidUsername',
      'Username must be 3–64 chars: letters, digits, _ . -',
    ],
  },
  // Change own password
  changePassword: {
    title: ['changePassword.title', 'Change My Password'],
    subtitle: [
      'changePassword.subtitle',
      'Rotate your own platform credential without exposing the password to the host shell.',
    ],
    cardTitle: ['changePassword.cardTitle', 'Credential update'],
    requirementTitle: ['changePassword.requirementTitle', 'Password policy'],
    requirementLength: ['changePassword.requirementLength', 'At least 12 characters'],
    requirementUpper: ['changePassword.requirementUpper', 'Uppercase and lowercase letters'],
    requirementDigit: ['changePassword.requirementDigit', 'At least one digit'],
    requirementSymbol: ['changePassword.requirementSymbol', 'At least one symbol'],
    oldPassword: ['changePassword.oldPassword', 'Current password'],
    newPassword: ['changePassword.newPassword', 'New password'],
    confirmNew: ['changePassword.confirmNew', 'Confirm new password'],
    submit: ['changePassword.submit', 'Change password'],
    success: ['changePassword.success', 'Password changed. Please sign in again.'],
    weakPassword: [
      'changePassword.weakPassword',
      'Password must be ≥12 chars and contain upper, lower, digit and symbol.',
    ],
    mismatch: ['changePassword.mismatch', 'New passwords do not match'],
  },
  // Audit
  audit: {
    title: ['audit.title', 'Platform Audit Log'],
    subtitle: [
      'audit.subtitle',
      'Review security-sensitive platform actions with actor, target, tenant and result context.',
    ],
    summaryTotal: ['audit.summaryTotal', 'Total events'],
    summarySuccess: ['audit.summarySuccess', 'Success on page'],
    summaryFailure: ['audit.summaryFailure', 'Failures on page'],
    summaryFiltered: ['audit.summaryFiltered', 'Filtered view'],
    colTime: ['audit.colTime', 'Time'],
    colActor: ['audit.colActor', 'Actor'],
    colAction: ['audit.colAction', 'Action'],
    colTarget: ['audit.colTarget', 'Target'],
    colTenant: ['audit.colTenant', 'Tenant'],
    colResult: ['audit.colResult', 'Result'],
    colReason: ['audit.colReason', 'Reason'],
    colIp: ['audit.colIp', 'IP'],
    filterAction: ['audit.filterAction', 'Filter by action'],
    filterResult: ['audit.filterResult', 'Filter by result'],
    resultSuccess: ['audit.resultSuccess', 'SUCCESS'],
    resultFailure: ['audit.resultFailure', 'FAILURE'],
  },
  // Tenant
  tenant: {
    listTitle: ['tenant.listTitle', 'Tenants'],
    listSubtitle: [
      'tenant.listSubtitle',
      'Inspect tenant lifecycle status, member footprint and controlled activation changes.',
    ],
    searchLabel: ['tenant.searchLabel', 'Search tenants'],
    applyFilters: ['tenant.applyFilters', 'Apply filters'],
    summaryTotal: ['tenant.summaryTotal', 'Total tenants'],
    summaryActive: ['tenant.summaryActive', 'Active on page'],
    summarySuspended: ['tenant.summarySuspended', 'Suspended on page'],
    summaryMembers: ['tenant.summaryMembers', 'Members on page'],
    colCode: ['tenant.colCode', 'Code'],
    colName: ['tenant.colName', 'Name'],
    colStatus: ['tenant.colStatus', 'Status'],
    colMembers: ['tenant.colMembers', 'Members'],
    colCreatedAt: ['tenant.colCreatedAt', 'Created'],
    actionUpdateStatus: ['tenant.actionUpdateStatus', 'Update status'],
    statusDialogTitle: ['tenant.statusDialogTitle', 'Update Tenant Status'],
    statusReason: ['tenant.statusReason', 'Reason (will be audited)'],
    searchPlaceholder: ['tenant.searchPlaceholder', 'Search by code or name…'],
    filterStatus: ['tenant.filterStatus', 'Filter by status'],
  },
} as const);

/**
 * Convenience wrapper — looks up a key/fallback tuple via the i18n hook.
 *
 * @example
 *   const tt = makeT(useI18n(I18N_NAMESPACE).t);
 *   tt(I18N_KEYS.login.title); // 'Platform Sign-in' (or translated)
 */
export type I18nTuple = readonly [string, string];

export function makeT(
  t: (key: string, def?: string | Record<string, unknown>) => string,
) {
  return (entry: I18nTuple, vars?: Record<string, unknown>): string => {
    const [key, fallback] = entry;
    if (!vars) {
      return t(key, fallback);
    }
    const translated = t(key, { defaultValue: fallback, interpolation: vars });
    return translated.replace(/\{\{\s*([\w.-]+)\s*\}\}/g, (match, name: string) => {
      const value = vars[name];
      return value === undefined || value === null ? match : String(value);
    });
  };
}
