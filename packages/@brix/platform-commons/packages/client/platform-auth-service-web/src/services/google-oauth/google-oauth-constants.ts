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
 * @file Google OAuth Constants
 * @description Constants and configuration utilities for Google OAuth
 * @module @brix/platform-auth-web/services/google-oauth/google-oauth-constants
 * @version 3.2.0
 * 
 * Extracted from GoogleOAuthService.ts as part of v3.2 architecture refactoring
 * to keep each file under 500 lines per code quality guidelines.
 * 
 * 【中文技术要点】
 * 集中管理 Google OAuth 相关常量，便于维护和配置。
 */

/**
 * Google OAuth Endpoints
 * 
 * Uses Google OAuth 2.0 official endpoints with OpenID Connect support.
 * These endpoints are stable and maintained by Google.
 * 
 * @see https://developers.google.com/identity/protocols/oauth2
 */
export const GOOGLE_ENDPOINTS = {
  /** Authorization endpoint: user authorization entry */
  authorization: 'https://accounts.google.com/o/oauth2/v2/auth',
  /** Token endpoint: exchange authorization code for token */
  token: 'https://oauth2.googleapis.com/token',
  /** UserInfo endpoint: retrieve user profile */
  userinfo: 'https://www.googleapis.com/oauth2/v3/userinfo',
  /** Revoke endpoint: invalidate token */
  revoke: 'https://oauth2.googleapis.com/revoke',
} as const;

/**
 * Default authorization scopes
 * 
 * - openid: Required for OpenID Connect, returns ID Token
 * - email: Get user email
 * - profile: Get basic profile (name, avatar)
 * 
 * @see https://developers.google.com/identity/protocols/oauth2/scopes
 */
export const DEFAULT_SCOPES = [
  'openid',           // OpenID Connect
  'email',            // User email
  'profile',          // Basic profile
] as const;

/**
 * Default storage key prefix
 * 
 * [v3.1 Brand Name Isolation] Uses generic prefix, avoids hardcoding brand names.
 * This allows the same code to be used across different product brands.
 */
export const DEFAULT_STORAGE_KEY_PREFIX = 'brix_oauth';

/**
 * Storage keys type
 */
export type StorageKeys = {
  /** State parameter (anti-CSRF) */
  readonly state: string;
  /** Nonce parameter (anti-replay) */
  readonly nonce: string;
  /** PKCE Code Verifier */
  readonly codeVerifier: string;
  /** Pre-login path (for post-callback redirect) */
  readonly redirectPath: string;
};

/**
 * Create storage keys
 * 
 * [v3.1 Brand Name Isolation] Supports configurable prefix.
 * Different products can use different prefixes to avoid key collisions.
 * 
 * @param prefix - Storage key prefix
 * @returns Storage key object
 */
export function createStorageKeys(prefix: string = DEFAULT_STORAGE_KEY_PREFIX): StorageKeys {
  return {
    /** State parameter (anti-CSRF) */
    state: `${prefix}_state`,
    /** Nonce parameter (anti-replay) */
    nonce: `${prefix}_nonce`,
    /** PKCE Code Verifier */
    codeVerifier: `${prefix}_code_verifier`,
    /** Pre-login path (for post-callback redirect) */
    redirectPath: `${prefix}_redirect_path`,
  } as const;
}
