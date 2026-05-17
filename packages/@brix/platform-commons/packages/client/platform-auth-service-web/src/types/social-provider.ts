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
 * @file Social Provider Type Definitions
 * @description OAuth social login provider types — platform-level contracts
 * @module @brix-sdk/platform-auth-service-web/types/social-provider
 * @version 3.2.0
 *
 * Migrated from enterprise-frame-web/src/oauth/types.ts (Phase 2.7)
 */

// ============================================================================
// OAuth Credential Types
// ============================================================================

/**
 * OAuth Credential Configuration.
 * Used for social login and OAuth 2.0 configuration.
 */
export interface OAuthCredentials {
  /** OAuth Client ID */
  clientId: string;

  /** Redirect URI (optional, defaults to {origin}/auth/callback/{providerId}) */
  redirectUri?: string;

  /** OAuth authorization scope */
  scope?: string;
}

/**
 * Social Login Provider definition.
 */
export interface SocialProvider {
  /** Provider ID (google, apple, microsoft, wechat, alipay) */
  id: string;

  /** Provider display name */
  name: string;

  /** Icon (emoji or SVG markup) */
  icon: string;

  /** Background color */
  backgroundColor: string;

  /** Text color */
  textColor: string;

  /** OAuth credential configuration (enables this provider when present) */
  oauth?: OAuthCredentials;

  /** Whether enabled (default: true) */
  enabled?: boolean;
}

/**
 * Regional Social Login Provider Configuration.
 */
export interface RegionalSocialProviders {
  /** China mainland region providers (WeChat, Alipay) */
  china: SocialProvider[];

  /** International market providers (Google, Apple, Microsoft) */
  international: SocialProvider[];
}
