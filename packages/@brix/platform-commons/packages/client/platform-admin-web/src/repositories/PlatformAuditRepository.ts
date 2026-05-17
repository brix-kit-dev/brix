/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

/**
 * @file PlatformAuditRepository — read-only access to the platform audit log.
 *
 * Backend (`PlatformAuditController`) is read-only by design — audit entries
 * are written exclusively by the platform-admin service layer (SSOT §10).
 * This repository therefore exposes only `query`.
 */

import type { HttpCapability } from '@brix-sdk/runtime-sdk-api-web';
import { PLATFORM_ADMIN_API } from '../constants';
import type {
  AuditLogQuery,
  Page,
  PlatformAuditLogDto,
} from '../types';

export interface PlatformAuditRepository {
  query(q: AuditLogQuery): Promise<Page<PlatformAuditLogDto>>;
}

export function createPlatformAuditRepository(
  http: HttpCapability,
): PlatformAuditRepository {
  return {
    async query(q) {
      return http.get<Page<PlatformAuditLogDto>>(
        PLATFORM_ADMIN_API.AUDIT_LOGS,
        q as Record<string, unknown>,
      );
    },
  };
}
