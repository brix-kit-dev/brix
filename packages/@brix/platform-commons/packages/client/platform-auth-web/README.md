# @brix-sdk/platform-auth-web

> Authentication capability implementation for Brix Platform

## Overview

This package implements the AuthCapability interface, providing authentication and authorization capabilities for the Brix Runtime platform.

## Features

- Multiple auth provider support (OIDC, OAuth2, Keycloak)
- Token management (access, refresh, ID tokens)
- Permission checking
- Session management
- Silent token renewal

## Installation

```bash
npm install @brix-sdk/platform-auth-web
```

## Usage

```typescript
import { createAuthCapability } from '@brix-sdk/platform-auth-web';

// Create auth capability with OIDC provider
const auth = createAuthCapability({
  provider: 'oidc',
  config: {
    authority: 'https://auth.example.com',
    clientId: 'my-app',
    redirectUri: window.location.origin + '/callback'
  }
});

// Check authentication status
if (auth.isAuthenticated()) {
  const user = auth.getUser();
  console.log('Logged in as:', user.name);
}

// Login
await auth.login();

// Logout
await auth.logout();

// Get access token
const token = await auth.getAccessToken();
```

## Permission Checking

```typescript
// Check single permission
if (auth.hasPermission('users:read')) {
  // Show users list
}

// Check multiple permissions
if (auth.hasAllPermissions(['users:read', 'users:write'])) {
  // Show edit button
}

// Check any permission
if (auth.hasAnyPermission(['admin', 'manager'])) {
  // Show admin panel
}
```

## Token Management

```typescript
// Get tokens manually
const accessToken = await auth.getAccessToken();
const idToken = auth.getIdToken();

// Refresh tokens
await auth.refreshToken();
```

## License

Apache-2.0
