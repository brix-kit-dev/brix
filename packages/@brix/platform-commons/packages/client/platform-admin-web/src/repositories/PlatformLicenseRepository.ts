/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

import type { HttpCapability } from '@brix-sdk/runtime-sdk-api-web';
import { PLATFORM_ADMIN_API } from '../constants';
import type { InstallationQuotaDto } from '../types';

export interface PlatformLicenseRepository {
  getInstallationQuota(): Promise<InstallationQuotaDto>;
}

export function createPlatformLicenseRepository(
  http: HttpCapability,
): PlatformLicenseRepository {
  return {
    async getInstallationQuota() {
      return http.get<InstallationQuotaDto>(PLATFORM_ADMIN_API.LICENSE_QUOTA);
    },
  };
}