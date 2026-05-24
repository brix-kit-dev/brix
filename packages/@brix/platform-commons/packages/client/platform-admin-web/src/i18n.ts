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
    mfaChallengeMissing: [
      'login.mfaChallengeMissing',
      'MFA challenge was not returned. Please sign in again.',
    ],
    requiredUsername: ['login.requiredUsername', 'Username is required'],
    requiredPassword: ['login.requiredPassword', 'Password is required'],
  },
  loginTotp: {
    title: ['loginTotp.title', 'Verify TOTP'],
    subtitle: ['loginTotp.subtitle', 'Use the authenticator bound during setup to finish sign in.'],
    badge: ['loginTotp.badge', 'MFA required'],
    code: ['loginTotp.code', 'Authenticator code'],
    codePlaceholder: ['loginTotp.codePlaceholder', '000000'],
    codeHelp: ['loginTotp.codeHelp', 'Enter the current 6-digit code from your authenticator app.'],
    submit: ['loginTotp.submit', 'Verify and continue'],
    missingChallenge: ['loginTotp.missingChallenge', 'Your sign-in challenge expired. Please sign in again.'],
    invalidCode: ['loginTotp.invalidCode', 'Enter a six-digit code'],
    helpTitle: ['loginTotp.helpTitle', 'Where is this code?'],
    helpBound: ['loginTotp.helpBound', 'Open the authenticator app that was bound when this account was set up.'],
    helpRefresh: ['loginTotp.helpRefresh', 'Use the latest 6-digit code; it changes every 30 seconds.'],
    noQrHelp: ['loginTotp.noQrHelp', 'A QR code is only shown when binding a new authenticator. Sign in uses the authenticator already bound to this account.'],
    backToLogin: ['loginTotp.backToLogin', 'Back to sign in'],
  },
  setup: {
    title: ['setup.title', 'Complete Platform Admin Setup'],
    subtitle: ['setup.subtitle', 'Create your password and bind an authenticator before signing in.'],
    invalidLink: ['setup.invalidLink', 'This setup link is invalid or expired.'],
    invalidLinkHelp: [
      'setup.invalidLinkHelp',
      'Open the latest setup email and use the complete link. Setup tokens are single-use and expire automatically.',
    ],
    account: ['setup.account', 'Account'],
    password: ['setup.password', 'New password'],
    confirmPassword: ['setup.confirmPassword', 'Confirm password'],
    continue: ['setup.continue', 'Continue'],
    totpTitle: ['setup.totpTitle', 'Bind authenticator'],
    totpCode: ['setup.totpCode', 'Authenticator code'],
    totpCodeHelp: ['setup.totpCodeHelp', 'Enter the current code after scanning the QR code.'],
    complete: ['setup.complete', 'Complete setup'],
    success: ['setup.success', 'Setup complete. You can now sign in.'],
    successBadge: ['setup.successBadge', 'Setup complete'],
    successTitle: ['setup.successTitle', 'Credential and MFA are active'],
    successBody: ['setup.successBody', 'Your password hash has been saved and this authenticator is now bound to the account.'],
    nextTitle: ['setup.nextTitle', 'Next sign in'],
    nextBody: ['setup.nextBody', 'Sign in with your password, then enter the current code from the same authenticator app.'],
    nextPasswordTitle: ['setup.nextPasswordTitle', 'Password step'],
    nextPasswordBody: ['setup.nextPasswordBody', 'Use the password created in this setup flow for platform sign-in.'],
    nextMfaTitle: ['setup.nextMfaTitle', 'Authenticator step'],
    nextMfaBody: ['setup.nextMfaBody', 'Open the same authenticator app and enter the current 6-digit code.'],
    completeFailed: ['setup.completeFailed', 'Setup did not complete. Please retry.'],
    manualPayload: ['setup.manualPayload', 'Manual setup payload'],
    manualSecret: ['setup.manualSecret', 'Manual setup key'],
    manualSecretHelp: [
      'setup.manualSecretHelp',
      'Use this key only when the authenticator app cannot scan the QR code. Choose manual entry in the app and enter the key there.',
    ],
    copyManualSecret: ['setup.copyManualSecret', 'Copy key'],
    qrUnavailable: [
      'setup.qrUnavailable',
      'QR rendering is unavailable in this browser session. Use the manual setup key below.',
    ],
    totpHelp: [
      'setup.totpHelp',
      'Scan the QR code with Google Authenticator or another TOTP app, or add the manual key. Then enter the current six-digit code.',
    ],
    progressLabel: ['setup.progressLabel', 'Setup progress'],
    stepPassword: ['setup.stepPassword', 'Password'],
    stepAuthenticator: ['setup.stepAuthenticator', 'Authenticator'],
    stepComplete: ['setup.stepComplete', 'Complete'],
    passwordMismatch: ['setup.passwordMismatch', 'Passwords do not match'],
  },
  bootstrap: {
    title: ['bootstrap.title', 'Platform Bootstrap'],
    subtitle: ['bootstrap.subtitle', 'Create the first platform super administrator and send the setup link by email.'],
    closed: ['bootstrap.closed', 'Bootstrap mode is closed.'],
    setupCode: ['bootstrap.setupCode', 'Bootstrap setup code'],
    username: ['bootstrap.username', 'Username'],
    email: ['bootstrap.email', 'Email'],
    displayName: ['bootstrap.displayName', 'Display name'],
    submit: ['bootstrap.submit', 'Create first admin'],
    sentTitle: ['bootstrap.sentTitle', 'Setup Link Sent'],
    sentSubtitle: ['bootstrap.sentSubtitle', 'The first platform administrator can now finish credential setup.'],
    sentBadge: ['bootstrap.sentBadge', 'Setup link sent'],
    sentBody: ['bootstrap.sentBody', 'The first platform administrator was created and the setup link was sent by email.'],
    sentNextTitle: ['bootstrap.sentNextTitle', 'Next steps'],
    sentNextMail: ['bootstrap.sentNextMail', 'Open the setup email from Mailpit or the configured mailbox.'],
    sentNextSetup: ['bootstrap.sentNextSetup', 'Set a compliant password from the setup link.'],
    sentNextMfa: ['bootstrap.sentNextMfa', 'Bind an authenticator and enter the current 6-digit code.'],
    sentDeliveryBody: [
      'bootstrap.sentDeliveryBody',
      'Delivery happens outside the platform response, preserving setup-token secrecy.',
    ],
    sentPasswordBody: [
      'bootstrap.sentPasswordBody',
      'The password is created only from the mailed setup link.',
    ],
    sentMfaBody: [
      'bootstrap.sentMfaBody',
      'Authenticator binding is required before the account becomes active.',
    ],
    sentMessage: ['bootstrap.sentMessage', '首位平台超管已创建，初始化设置链接已发送，请前往邮箱完成密码设置。'],
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
    summaryRevoked: ['admin.summaryRevoked', 'Revoked on page'],
    summaryPageSize: ['admin.summaryPageSize', '{{count}} shown per page'],
    create: ['admin.create', 'Create Super Admin'],
    colUsername: ['admin.colUsername', 'Username'],
    colEmail: ['admin.colEmail', 'Email'],
    colRole: ['admin.colRole', 'Role'],
    colStatus: ['admin.colStatus', 'Status'],
    colCreatedAt: ['admin.colCreatedAt', 'Created'],
    colLastLogin: ['admin.colLastLogin', 'Last login'],
    actionRevoke: ['admin.actionRevoke', 'Revoke'],
    actionResetPassword: ['admin.actionResetPassword', 'Reset password'],
    statusActive: ['admin.statusActive', 'Active'],
    statusRevoked: ['admin.statusRevoked', 'Revoked'],

    createDialogTitle: ['admin.createDialogTitle', 'Create Super Admin'],
    fieldUsername: ['admin.fieldUsername', 'Username'],
    fieldEmail: ['admin.fieldEmail', 'Email'],
    fieldDisplayName: ['admin.fieldDisplayName', 'Display name'],
    fieldRole: ['admin.fieldRole', 'Role'],
    fieldNotes: ['admin.fieldNotes', 'Notes'],

    setupLinkTitle: ['admin.setupLinkTitle', 'Setup Link Delivery'],
    setupLinkInfo: [
      'admin.setupLinkInfo',
      'The administrator will complete credential setup and MFA binding from the setup link.',
    ],
    setupLinkSent: [
      'admin.setupLinkSent',
      'The setup link has been sent to the administrator email address.',
    ],
    setupLinkPending: [
      'admin.setupLinkPending',
      'Setup link delivery was not accepted. No credentials were returned.',
    ],

    revokeDialogTitle: ['admin.revokeDialogTitle', 'Revoke Super Admin'],
    revokeReason: ['admin.revokeReason', 'Reason (will be audited)'],
    revokeConfirm: [
      'admin.revokeConfirm',
      'Revoke {{name}}? They will be signed out immediately and cannot sign back in.',
    ],

    resetConfirm: [
      'admin.resetConfirm',
      'Send a setup reset link to {{name}}?',
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
    totpCode: ['changePassword.totpCode', 'Authenticator code'],
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
    // Create tenant
    createBtn: ['tenant.createBtn', 'Create Tenant'],
    createDialogTitle: ['tenant.createDialogTitle', 'Create New Tenant'],
    createIntroIconLabel: ['tenant.createIntroIconLabel', 'Tenant workspace'],
    createIntroTitle: ['tenant.createIntroTitle', 'Enterprise workspace provisioning'],
    createIntroBody: [
      'tenant.createIntroBody',
      'Create an isolated tenant record for a customer organization before enabling lifecycle status changes and member onboarding.',
    ],
    createPendingActivationNote: [
      'tenant.createPendingActivationNote',
      'New tenants are created as Pending Activation. Activate the tenant only after the organization and ownership details are verified.',
    ],
    fieldCode: ['tenant.fieldCode', 'Tenant Code'],
    fieldCodeHelper: ['tenant.fieldCodeHelper', '2–64 chars · lowercase letters, digits and hyphens · must start with a letter (e.g. acme-corp)'],
    fieldName: ['tenant.fieldName', 'Display Name'],
    fieldNameHelper: ['tenant.fieldNameHelper', 'Customer-facing organization name shown in platform administration views'],
    createSuccess: ['tenant.createSuccess', 'Tenant created successfully'],
    createFailed: ['tenant.createFailed', 'Failed to create tenant'],
    codeRequired: ['tenant.codeRequired', 'Tenant code is required'],
    codeInvalid: ['tenant.codeInvalid', 'Code must be 2–64 lowercase letters, digits or hyphens, starting with a letter'],
    nameRequired: ['tenant.nameRequired', 'Display name is required'],
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
