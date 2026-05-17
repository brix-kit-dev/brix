# @brix-sdk/platform-auth-ui-web

> Authentication UI components for Brix Platform

[![npm version](https://img.shields.io/npm/v/@brix-sdk/platform-auth-ui-web.svg)](https://www.npmjs.com/package/@brix-sdk/platform-auth-ui-web)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

## Overview

`@brix-sdk/platform-auth-ui-web` provides authentication UI components including login forms, registration forms, and permission guards. This package was extracted from `@brix-sdk/platform-auth-web` in v3.0.

### v3.1 Architecture

| Package | Responsibility | Description |
|---------|----------------|-------------|
| `@brix-sdk/platform-auth-web` | Capability implementation | AuthCapabilityImpl |
| `@brix-sdk/platform-auth-ui-web` | UI components | Login form, permission guards |
| `@brix-sdk/platform-auth-service-web` | Service factory | OAuth, token management |

## Installation

```bash
npm install @brix-sdk/platform-auth-ui-web
```

## Usage

### Components

```tsx
import { AuthGuard, PermissionGate, LoginForm } from '@brix-sdk/platform-auth-ui-web';

// Auth guard - protects pages requiring authentication
<AuthGuard fallback={<LoginRedirect />}>
  <ProtectedContent />
</AuthGuard>

// Permission gate - protects content requiring specific permissions
<PermissionGate permission="booking:read">
  <BookingList />
</PermissionGate>

// Login form
<LoginForm 
  onSubmit={handleLogin}
  branding={{ logo: '/logo.png', title: 'Login' }}
/>
```

### Hooks

```tsx
import { useAuth, usePermission, useRole } from '@brix-sdk/platform-auth-ui-web';

function MyComponent() {
  // 获取当前认证状�?
  const { user, isAuthenticated, loading } = useAuth();
  
  // 检查权�?
  const canEdit = usePermission('booking:edit');
  
  // 检查角�?
  const isAdmin = useRole('admin');
  
  return (
    <div>
      {isAuthenticated && <span>欢迎, {user.name}</span>}
      {canEdit && <EditButton />}
    </div>
  );
}
```

### 预装配页�?

```tsx
import { createLoginPage, createSimpleRegisterPage } from '@brix-sdk/platform-auth-ui-web';

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

## 📚 API 参�?

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
| `useAuth` | 获取当前认证状�?|
| `usePermission` | 检查单个权�?|
| `useAnyPermission` | 检查是否拥有任一权限 |
| `useAllPermissions` | 检查是否拥有所有权�?|
| `useRole` | 检查角�?|

### 页面工厂

| 工厂函数 | 说明 |
|----------|------|
| `createLoginPage` | 创建完整配置的登录页�?|
| `createSimpleLoginPage` | 创建简化版登录页面 |
| `createSimpleRegisterPage` | 创建简化版注册页面 |

## 📄 License

Apache-2.0
