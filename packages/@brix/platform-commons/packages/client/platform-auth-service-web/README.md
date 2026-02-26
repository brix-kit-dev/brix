# @brix/platform-auth-service-web

> Web 端认证服务工厂 - 提供 OAuth、Token 管理等认证服务

[![npm version](https://img.shields.io/npm/v/@brix/platform-auth-service-web.svg)](https://www.npmjs.com/package/@brix/platform-auth-service-web)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

## 📋 概述

`@brix/platform-auth-service-web` 是 Brix 平台的认证服务包，从 `@brix/platform-auth-web` v3.0 拆分而来。

### v3.1 架构重构

| 包名 | 职责 | 说明 |
|------|------|------|
| `@brix/platform-auth-web` | 能力实现 | AuthCapabilityImpl |
| `@brix/platform-auth-ui-web` | UI 组件 | 登录表单、权限守卫等 |
| `@brix/platform-auth-service-web` | 服务工厂 | OAuth、Token 管理 |

## 📦 安装

```bash
pnpm add @brix/platform-auth-service-web
```

## 🚀 使用

### 平台认证服务

```typescript
import { createPlatformAuthService } from '@brix/platform-auth-service-web';

// 创建认证服务
const authService = createPlatformAuthService({
  baseUrl: '/api/auth',
  tokenStorage: 'localStorage',
});

// 登录
const result = await authService.login({
  username: 'user@example.com',
  password: 'password',
});

// 获取当前用户
const user = authService.getCurrentUser();

// 登出
await authService.logout();
```

### Google OAuth 服务

```typescript
import { GoogleOAuthService, initGoogleAuth } from '@brix/platform-auth-service-web';

// 初始化 Google OAuth
initGoogleAuth({
  clientId: 'your-google-client-id',
  redirectUri: window.location.origin + '/oauth/callback',
  backendExchangeEndpoint: '/api/auth/google/exchange',
});

// 获取服务实例
const googleAuth = getGoogleAuthService();

// 开始 OAuth 流程
googleAuth.startOAuthFlow();

// 处理回调
const result = await googleAuth.handleCallback(code);
```

## 📚 API 参考

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
| `initGoogleAuth(config)` | 初始化 Google OAuth |
| `getGoogleAuthService()` | 获取服务单例 |
| `resetGoogleAuth()` | 重置服务状态 |
| `startOAuthFlow()` | 开始 OAuth 流程 |
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

1. **Token 存储**：生产环境建议使用 HttpOnly Cookie 而非 localStorage
2. **PKCE**：Google OAuth 实现已内置 PKCE 支持
3. **CSRF**：确保后端实现了 CSRF 保护

## 📄 License

Apache-2.0
