# @brix-sdk/platform-navigation-web

> Navigation capability implementation for Brix Platform

## Overview

This package implements the NavigationCapability interface, providing navigation and routing capabilities for the Brix Runtime platform.

## Features

- Programmatic navigation
- Route path resolution
- Navigation guards
- Breadcrumb management
- History management
- Deep linking support

## Installation

```bash
npm install @brix-sdk/platform-navigation-web
```

## Usage

```typescript
import { createNavigationCapability } from '@brix-sdk/platform-navigation-web';

// Create navigation capability
const navigation = createNavigationCapability({
  router: reactRouterInstance,
  basePath: '/app'
});

// Navigate to a path
navigation.navigate('/dashboard');

// Navigate with parameters
navigation.navigate('/users/:id', { id: '123' });

// Navigate with query params
navigation.navigate('/search', {}, { q: 'keyword', page: '1' });

// Go back
navigation.goBack();

// Replace current entry
navigation.replace('/new-path');
```

## Navigation Guards

```typescript
// Add a guard before navigation
navigation.addGuard(async (to, from) => {
  if (to.path.startsWith('/admin') && !user.isAdmin) {
    return '/unauthorized';
  }
  return true; // Allow navigation
});
```

## Breadcrumbs

```typescript
// Get current breadcrumbs
const crumbs = navigation.getBreadcrumbs();
// [{ path: '/', label: 'Home' }, { path: '/users', label: 'Users' }]
```

## License

Apache-2.0
