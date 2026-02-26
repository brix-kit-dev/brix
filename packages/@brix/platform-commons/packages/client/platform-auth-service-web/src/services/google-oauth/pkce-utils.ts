/**
 * @file PKCE Utilities
 * @description PKCE (Proof Key for Code Exchange) utility functions for OAuth 2.0
 * @module @brix/platform-auth-web/services/google-oauth/pkce-utils
 * @version 3.2.0
 * 
 * Extracted from GoogleOAuthService.ts as part of v3.2 architecture refactoring
 * to keep each file under 500 lines per code quality guidelines.
 * 
 * [PKCE Flow Description]
 * 1. Client generates random code_verifier (43-128 characters)
 * 2. Calculate code_challenge = BASE64URL(SHA256(code_verifier))
 * 3. Authorization request includes code_challenge
 * 4. Token exchange includes original code_verifier
 * 5. Server verifies SHA256(code_verifier) == code_challenge
 * 
 * 【中文技术要点】
 * PKCE 是 OAuth 2.0 for Native Apps (RFC 8252) 推荐的安全机制。
 * 即使授权码被截获，攻击者也无法交换 Token，因为缺少 code_verifier。
 * 
 * @see https://datatracker.ietf.org/doc/html/rfc7636
 */

import type { PKCEPair } from './google-types';

/**
 * Generate cryptographically secure random string
 * 
 * Uses Web Crypto API's crypto.getRandomValues() for cryptographically secure random numbers.
 * More secure than Math.random().
 * 
 * 【中文技术要点】
 * 使用 Web Crypto API 生成密码学安全的随机数，符合 RFC 7636 PKCE 规范要求。
 * 
 * @param length - String length (recommended: 32-64 characters)
 * @returns Random string
 */
export function generateRandomString(length: number): string {
  // Character set compliant with RFC 7636 (unreserved characters)
  const charset = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~';
  const randomValues = new Uint8Array(length);
  crypto.getRandomValues(randomValues);
  return Array.from(randomValues)
    .map(v => charset[v % charset.length])
    .join('');
}

/**
 * SHA-256 Hash
 * 
 * Uses Web Crypto API's SubtleCrypto.digest() for hash computation.
 * This is the browser's native cryptography API with guaranteed performance and security.
 * 
 * @param plain - Original string
 * @returns SHA-256 hash result (ArrayBuffer)
 */
export async function sha256(plain: string): Promise<ArrayBuffer> {
  const encoder = new TextEncoder();
  const data = encoder.encode(plain);
  return crypto.subtle.digest('SHA-256', data);
}

/**
 * Base64URL Encoding
 * 
 * RFC 4648 defined Base64URL encoding, compared to standard Base64:
 * - '+' replaced with '-'
 * - '/' replaced with '_'
 * - Remove trailing '=' padding
 * 
 * @param buffer - ArrayBuffer to encode
 * @returns Base64URL encoded string
 */
export function base64URLEncode(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let binary = '';
  for (let i = 0; i < bytes.byteLength; i++) {
    binary += String.fromCharCode(bytes[i]);
  }
  return btoa(binary)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

/**
 * Generate PKCE Code Verifier and Code Challenge
 * 
 * 【PKCE 流程说明】
 * 1. 客户端生成随机 code_verifier（43-128 字符）
 * 2. 计算 code_challenge = BASE64URL(SHA256(code_verifier))
 * 3. 授权请求带上 code_challenge
 * 4. Token 交换时带上原始 code_verifier
 * 5. 服务器验证 SHA256(code_verifier) == code_challenge
 * 
 * @see https://datatracker.ietf.org/doc/html/rfc7636
 * @returns PKCE parameter pair
 */
export async function generatePKCEPair(): Promise<PKCEPair> {
  // Code Verifier: 43-128 character random string (recommended 64 chars)
  const codeVerifier = generateRandomString(64);
  
  // Code Challenge: Base64URL encode of SHA-256(code_verifier)
  const hash = await sha256(codeVerifier);
  const codeChallenge = base64URLEncode(hash);
  
  return {
    codeVerifier,
    codeChallenge,
    codeChallengeMethod: 'S256',
  };
}
