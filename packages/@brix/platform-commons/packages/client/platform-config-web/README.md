# @brix/platform-config-web

> Web Configuration Capability Implementation - Implements ConfigCapability Interface

## 📖 Overview

`platform-config-web` is the implementation module for the `ConfigCapability` interface defined in `@brix/runtime-sdk-api-web`. It provides configuration loading, caching, and hot-reload capabilities for web applications.

## 🏗️ Architectural Position

```text
+-------------------------------------------------------------------------+
| Capability Contract Layer (runtime-sdk-api-web)                        |
| +-- ConfigCapability Interface Definition                              |
+-------------------------------------------------------------------------+
| Capability Implementation Layer (platform-commons)                     |
| +-- platform-config-web ⭐                                             |
|      +-- ConfigCapabilityImpl (interface implementation)               |
|      +-- ConfigStore (in-memory configuration cache)                   |
|      +-- ConfigHttpClient (fetch configuration via HttpCapability)     |
+-------------------------------------------------------------------------+
```

## 🚀 Installation

```bash
pnpm add @brix/platform-config-web
```

## 📦 Usage

### Basic Usage

```typescript
import { createConfigCapability } from '@brix/platform-config-web';
import type { HttpCapability } from '@brix/runtime-sdk-api-web';

// Create configuration capability
const configCapability = await createConfigCapability({
  httpCapability, // HttpCapability instance
  configEndpoint: '/api/v1/config',
  refreshInterval: 60000, // Auto-refresh every minute
});

// Get configuration value
const apiBase = configCapability.get<string>('api.baseUrl', '/api/v1');
const timeout = configCapability.get<number>('http.timeout', 30000);
```

### With Initial Configuration (SSR)

```typescript
const configCapability = await createConfigCapability({
  httpCapability,
  initialConfig: {
    api: { baseUrl: '/api/v1' },
    features: { darkMode: true },
  },
});
```

### Listen for Configuration Changes

```typescript
// Listen for specific key
const unsubscribe = configCapability.onConfigChange('api.baseUrl', (event) => {
  console.log('API base URL changed:', event.oldValue, '->', event.newValue);
});

// Listen for all changes
const unsubscribeAll = configCapability.onConfigChange('*', (event) => {
  console.log('Configuration changed:', event.key);
});

// Cleanup
unsubscribe();
unsubscribeAll();
```

### Manual Refresh

```typescript
await configCapability.refresh();
```

## 🔧 API Reference

### ConfigCapabilityImpl

Main implementation class.

#### Constructor Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `httpCapability` | `HttpCapability` | Required | HTTP capability for fetching config |
| `configEndpoint` | `string` | `/api/v1/config` | Configuration API endpoint |
| `refreshInterval` | `number` | `0` (disabled) | Auto-refresh interval in ms |
| `initialConfig` | `Record<string, unknown>` | `undefined` | Initial configuration |
| `pluginId` | `string` | `undefined` | Plugin ID for scoped config |
| `cacheTtl` | `number` | `300000` (5min) | Cache TTL in ms |
| `enableChangeLogging` | `boolean` | `true` | Log configuration changes |

#### Methods

- `get<T>(key: string, defaultValue?: T): T` - Get configuration value
- `getAll<T>(): T` - Get all configuration
- `refresh(): Promise<void>` - Refresh configuration from backend
- `onConfigChange(key: string, handler): () => void` - Listen for changes
- `set(key: string, value: unknown): void` - Set local configuration
- `has(key: string): boolean` - Check if key exists
- `destroy(): void` - Cleanup resources

## 📝 Design Constraints

- ❌ Do not call `fetch`/`axios` directly - use `HttpCapability`
- ❌ Do not store sensitive configuration in localStorage
- ✅ All configuration changes are logged for auditing

## 📄 License

Apache-2.0
