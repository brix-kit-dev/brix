# @brix/platform-auth-ui-web

> Web 端认证 UI 组件库 - 提供登录表单、注册表单、权限守卫等 UI 组件

[![npm version](https://img.shields.io/npm/v/@brix/platform-auth-ui-web.svg)](https://www.npmjs.com/package/@brix/platform-auth-ui-web)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

## 📋 概述

`@brix/platform-auth-ui-web` 是 Brix 平台的认证 UI 组件包，从 `@brix/platform-auth-web` v3.0 拆分而来。

### v3.1 架构重构

| 包名 | 职责 | 说明 |
|------|------|------|
| `@brix/platform-auth-web` | 能力实现 | AuthCapabilityImpl |
| `@brix/platform-auth-ui-web` | UI 组件 | 登录表单、权限守卫等 |
| `@brix/platform-auth-service-web` | 服务工厂 | OAuth、Token 管理 |

## 📦 安装

```bash
pnpm add @brix/platform-auth-ui-web
```

## 🚀 使用

### 组件

```tsx
import { AuthGuard, PermissionGate, LoginForm } from '@brix/platform-auth-ui-web';

// 认证守卫 - 保护需要登录的页面
<AuthGuard fallback={<LoginRedirect />}>
  <ProtectedContent />
</AuthGuard>

// 权限守卫 - 保护需要特定权限的内容
<PermissionGate permission="booking:read">
  <BookingList />
</PermissionGate>

// 登录表单
<LoginForm 
  onSubmit={handleLogin}
  branding={{ logo: '/logo.png', title: '登录' }}
/>
```

### Hooks

```tsx
import { useAuth, usePermission, useRole } from '@brix/platform-auth-ui-web';

function MyComponent() {
  // 获取当前认证状态
  const { user, isAuthenticated, loading } = useAuth();
  
  // 检查权限
  const canEdit = usePermission('booking:edit');
  
  // 检查角色
  const isAdmin = useRole('admin');
  
  return (
    <div>
      {isAuthenticated && <span>欢迎, {user.name}</span>}
      {canEdit && <EditButton />}
    </div>
  );
}
```

### 预装配页面

```tsx
import { createLoginPage, createSimpleRegisterPage } from '@brix/platform-auth-ui-web';

// 创建登录页面
const LoginPage = createLoginPage({
  authService,
  navigationService,
  routes: {
    home: '/',
    register: '/register',
  },
});

// 创建注册页面
const RegisterPage = createSimpleRegisterPage({
  authService,
  routes: { login: '/login' },
});
```

## 📚 API 参考

### 组件

| 组件 | 说明 |
|------|------|
| `AuthGuard` | 认证守卫，保护需要登录的内容 |
| `PermissionGate` | 权限守卫，保护需要特定权限的内容 |
| `LoginForm` | 登录表单组件 |
| `RegisterForm` | 注册表单组件 |

### Hooks

| Hook | 说明 |
|------|------|
| `useAuth` | 获取当前认证状态 |
| `usePermission` | 检查单个权限 |
| `useAnyPermission` | 检查是否拥有任一权限 |
| `useAllPermissions` | 检查是否拥有所有权限 |
| `useRole` | 检查角色 |

### 页面工厂

| 工厂函数 | 说明 |
|----------|------|
| `createLoginPage` | 创建完整配置的登录页面 |
| `createSimpleLoginPage` | 创建简化版登录页面 |
| `createSimpleRegisterPage` | 创建简化版注册页面 |

## 📄 License

Apache-2.0
