/*
 * Copyright 2026 Brix Platform Authors
 * Licensed under the Apache License, Version 2.0 (the "License");
 */

export const PLATFORM_TENANT_ROUTES = Object.freeze({
  FIRST_OWNER_ACCEPT: '/platform/first-owner/accept',
  /** Staff console behind the BFF (identity-auth §4 `tenant-console`). */
  CONSOLE_HOME: '/tenant',
} as const);

export const PLATFORM_TENANT_API = Object.freeze({
  FIRST_OWNER_ACCEPT: '/tenant/first-owner-invitations/accept',
} as const);

export const PLATFORM_AUTH_API = Object.freeze({
  ACTOR_LOGIN: '/auth/login/actor',
} as const);
