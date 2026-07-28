/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

export { useRepositories, type PlatformAdminRepositoryBundle } from './useRepositories';
export { usePlatformLogin, type UsePlatformLoginResult } from './usePlatformLogin';
export { usePlatformLoginTotp, type UsePlatformLoginTotpResult } from './usePlatformLoginTotp';
export { usePlatformSetup, type UsePlatformSetupResult } from './usePlatformSetup';
export { usePlatformBootstrap, type UsePlatformBootstrapResult, type PlatformBootstrapCreateRequest } from './usePlatformBootstrap';
export { useSuperAdminList, type UseSuperAdminListResult } from './useSuperAdminList';
export { useCreateSuperAdmin, type UseCreateSuperAdminResult } from './useCreateSuperAdmin';
export { useRevokeSuperAdmin, type UseRevokeSuperAdminResult } from './useRevokeSuperAdmin';
export { useResetPassword, type UseResetPasswordResult } from './useResetPassword';
export { useChangeOwnPassword, type UseChangeOwnPasswordResult } from './useChangeOwnPassword';
export { useAuditLog, type UseAuditLogResult } from './useAuditLog';
export { useInstallationQuota, type UseInstallationQuotaResult } from './useInstallationQuota';
export { usePlatformTenantList, type UsePlatformTenantListResult } from './usePlatformTenantList';
export { useUpdateTenantStatus, type UseUpdateTenantStatusResult } from './useUpdateTenantStatus';
export { useCreatePlatformTenant, type UseCreatePlatformTenantResult } from './useCreatePlatformTenant';
export { useFirstOwnerInvitation, type UseFirstOwnerInvitationResult } from './useFirstOwnerInvitation';
export { useNoReferrerPolicy } from './useNoReferrerPolicy';
