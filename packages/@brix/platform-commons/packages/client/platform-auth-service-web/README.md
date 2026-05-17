# @brix-sdk/platform-auth-service-web

> Authentication service implementation for Brix Platform

[![npm version](https://img.shields.io/npm/v/@brix-sdk/platform-auth-service-web.svg)](https://www.npmjs.com/package/@brix-sdk/platform-auth-service-web)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

## Overview

`@brix-sdk/platform-auth-service-web` provides authentication services including OAuth, token management, and session handling. This package was extracted from `@brix-sdk/platform-auth-web` in v3.0.

### v3.1 Architecture

| Package | Responsibility | Description |
|---------|----------------|-------------|
| `@brix-sdk/platform-auth-web` | Capability implementation | AuthCapabilityImpl |
| `@brix-sdk/platform-auth-ui-web` | UI components | Login form, permission guards |
| `@brix-sdk/platform-auth-service-web` | Service factory | OAuth, token management |

## Installation

```bash
npm install @brix-sdk/platform-auth-service-web
```

## Usage

### Platform Auth Service

```typescript
import { createPlatformAuthService } from '@brix-sdk/platform-auth-service-web';

// Create auth service
const authService = createPlatformAuthService({
  baseUrl: '/api/auth',
  tokenStorage: 'localStorage',
});

// Login
const result = await authService.login({
  username: 'user@example.com',
  password: 'password',
});

// Get current user
const user = authService.getCurrentUser();

// Logout
await authService.logout();
```

## License

Apache-2.0

### Google OAuth 服务

```typescript
import { GoogleOAuthService, initGoogleAuth } from '@brix-sdk/platform-auth-service-web';

// 初始�?Google OAuth
initGoogleAuth({
  clientId: 'your-google-client-id',
  redirectUri: window.location.origin + '/oauth/callback',
  backendExchangeEndpoint: '/api/auth/google/exchange',
});

// 获取服务实例
const googleAuth = getGoogleAuthService();

// 开�?OAuth 流程
googleAuth.startOAuthFlow();

// 处理回调
const result = await googleAuth.handleCallback(code);
```

## 📚 API 参�?

### 平台认证服务

| 方法 | 说明 |
|------|------|
| `createPlatformAuthService(options)` | 创建认证服务实例 |
| `login(credentials)` | 用户登录 |
| `logout()` | 用户登出 |
| `register(data)` | 用户注册 |
| `getCurrentUser()` | 获取当前用户 |
| `refreshToken()` | 刷新 Token |

### Google OAuth 服务

| 方法 | 说明 |
|------|------|
| `initGoogleAuth(config)` | 初始�?Google OAuth |
| `getGoogleAuthService()` | 获取服务单例 |
| `resetGoogleAuth()` | 重置服务状�?|
| `startOAuthFlow()` | 开�?OAuth 流程 |
| `handleCallback(code)` | 处理 OAuth 回调 |

### 类型定义

```typescript
interface PlatformAuthServiceOptions {
  baseUrl: string;
  tokenStorage?: 'localStorage' | 'sessionStorage' | 'memory';
}

interface GoogleOAuthConfig {
  clientId: string;
  redirectUri: string;
  backendExchangeEndpoint?: string;
  scopes?: string[];
}
```

## 🔒 安全注意事项

1. **Token 存储**：生产环境建议使�?HttpOnly Cookie 而非 localStorage
2. **PKCE**：Google OAuth 实现已内�?PKCE 支持
3. **CSRF**：确保后端实现了 CSRF 保护

## 📄 License

Apache-2.0
