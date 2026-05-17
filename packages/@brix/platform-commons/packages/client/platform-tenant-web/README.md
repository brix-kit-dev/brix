# @brix/platform-tenant-web

Multi-tenant context management for React applications in the Brix platform.

## Overview

This module provides the `useTenant()` hook and supporting infrastructure for multi-tenant applications. It follows the v3.0.9 blueprint Section 14 specifications for multi-tenant architecture.

## Installation

```bash
pnpm add @brix/platform-tenant-web
```

## Quick Start

### 1. Wrap Your Application

```tsx
import { TenantProvider } from '@brix/platform-tenant-web';

function App() {
  return (
    <TenantProvider>
      <YourApplication />
    </TenantProvider>
  );
}
```

### 2. Use the Hook

```tsx
import { useTenant } from '@brix/platform-tenant-web';

function UserList() {
  const { tenant, isLoading, error } = useTenant();
  
  if (isLoading) return <Loading />;
  if (error) return <ErrorDisplay error={error} />;
  if (!tenant) return <NoTenantAccess />;
  
  // Use tenant.id for API calls
  return <UserTable tenantId={tenant.id} />;
}
```

## API Reference

### Hooks

#### `useTenant()`

Primary hook for accessing tenant context.

```tsx
const {
  tenant,           // Current Tenant object
  isLoading,        // Loading state
  error,            // Error state
  availableTenants, // List of accessible tenants
  features,         // Tenant feature flags
  isFeatureEnabled, // Check if feature is enabled
  switchTenant,     // Switch to different tenant
  refreshTenant,    // Refresh tenant data
} = useTenant();
```

#### `useTenantId()`

Convenience hook for getting just the tenant ID.

```tsx
const tenantId = useTenantId(); // string | null
```

#### `useRequiredTenantId()`

Hook that throws if tenant is not available. Use when tenant is mandatory.

```tsx
const tenantId = useRequiredTenantId(); // string (throws if not available)
```

#### `useFeatureEnabled(featureKey)`

Check if a feature is enabled for the current tenant.

```tsx
const isEnabled = useFeatureEnabled('advanced-analytics'); // boolean
```

### Components

#### `<TenantProvider>`

Provider component that must wrap your application.

```tsx
<TenantProvider
  httpClient={customHttpClient}  // Optional: custom HTTP client
  apiBaseUrl="/api/v1/tenant"    // Optional: API base URL
  onTenantChange={(tenant) => {}} // Optional: callback on tenant change
  onError={(error) => {}}        // Optional: callback on error
>
  {children}
</TenantProvider>
```

### Types

```tsx
interface Tenant {
  id: string;
  code: string;
  name: string;
  status: TenantStatus;
  displayName?: string;
  description?: string;
  logoUrl?: string;
  timezone?: string;
  locale?: string;
  settings?: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

type TenantStatus = 'PENDING' | 'ACTIVE' | 'SUSPENDED' | 'TERMINATED';

interface TenantFeature {
  key: string;
  name: string;
  enabled: boolean;
  metadata?: Record<string, unknown>;
}
```

## Usage Patterns

### Multi-tenant Switching

```tsx
function TenantSwitcher() {
  const { tenant, availableTenants, switchTenant, isLoading } = useTenant();
  
  if (availableTenants.length <= 1) return null;
  
  return (
    <Select
      value={tenant?.id}
      onChange={(e) => switchTenant(e.target.value)}
      disabled={isLoading}
    >
      {availableTenants.map(t => (
        <Option key={t.id} value={t.id}>{t.name}</Option>
      ))}
    </Select>
  );
}
```

### Feature Flags

```tsx
function AdvancedFeature() {
  const { isFeatureEnabled } = useTenant();
  
  if (!isFeatureEnabled('advanced-analytics')) {
    return <UpgradePrompt feature="Advanced Analytics" />;
  }
  
  return <AnalyticsDashboard />;
}
```

### API Calls with Tenant

```tsx
function UserManagement() {
  const { tenant } = useTenant();
  
  const { data: users } = useQuery({
    queryKey: ['users', tenant?.id],
    queryFn: () => api.getUsers({ tenantId: tenant!.id }),
    enabled: !!tenant,
  });
  
  return <UserTable users={users} />;
}
```

## Migration from Hardcoded Tenant IDs

Before (hardcoded):
```tsx
// ❌ Don't do this
const tenantId = 'default';
api.createUser({ ...userData, tenantId });
```

After (using useTenant):
```tsx
// ✅ Do this
const { tenant } = useTenant();
api.createUser({ ...userData, tenantId: tenant.id });
```

## Architecture

This module follows the v3.0.9 blueprint:

- **Section 14.1**: Tenant identification via JWT claims and X-Tenant-Id header
- **Section 14.2**: Tenant data isolation through tenant-scoped queries
- **Section 14.3**: Feature management with tenant-specific flags
- **Section 14.4**: Tenant switching for multi-tenant users
- **Section 14.5**: React context implementation with hooks

## License

MIT © Brix Platform
