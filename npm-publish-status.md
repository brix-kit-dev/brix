# Brix Frontend Packages npm Publish Status

> Account: brix-sdk
> Updated: 2026-03-18
> Status: 6/27 published, rate limited - continuing tomorrow
> Progress: Phase 1 (1/5) | Phase 2 (0/8) | Phase 3 (5/10) | Phase 4 (0/3) | Phase 5 (0/1)
> Scaffold templates: Updated to use @brix-sdk/brix

## Overview

All previous packages have been unpublished. Starting fresh with version 1.0.0.

The publishing strategy follows Firebase v9 style - users only need to install the aggregate package `@brix-sdk/brix`.

## Publish Order

Packages must be published in dependency order:

### Phase 1: Core Runtime SDK (5 packages)

| Package | Version | Status | Path |
|---------|---------|--------|------|
| @brix-sdk/runtime-sdk-api-web | 1.0.0 | �?| packages/@brix/runtime-sdk/runtime-sdk-api-web |
| @brix-sdk/runtime-sdk-react | 1.0.0 | �?| packages/@brix/runtime-sdk/runtime-sdk-react |
| @brix-sdk/runtime-manifest-web | 1.0.0 | �?| packages/@brix/runtime-sdk/runtime-manifest-web |
| @brix-sdk/runtime-orchestrator-web | 1.0.0 | �?| packages/@brix/runtime-sdk/runtime-orchestrator-web |
| @brix-sdk/shared-runtime-web | 1.0.0 | �?| packages/@brix/shared-runtime/shared-runtime-web |

### Phase 2: Infrastructure Adapters - Web (8 packages)

| Package | Version | Status | Path |
|---------|---------|--------|------|
| @brix-sdk/infra-adapter-http-web | 1.0.0 | �?| packages/@brix/infra-adapters/packages/web/infra-adapter-http-web |
| @brix-sdk/infra-adapter-iframe-web | 1.0.0 | �?| packages/@brix/infra-adapters/packages/web/infra-adapter-iframe-web |
| @brix-sdk/infra-adapter-mf-web | 1.0.0 | �?| packages/@brix/infra-adapters/packages/web/infra-adapter-mf-web |
| @brix-sdk/infra-adapter-native-web | 1.0.0 | �?| packages/@brix/infra-adapters/packages/web/infra-adapter-native-web |
| @brix-sdk/infra-adapter-router-web | 1.0.0 | �?| packages/@brix/infra-adapters/packages/web/infra-adapter-router-web |
| @brix-sdk/infra-adapter-state-web | 1.0.0 | �?| packages/@brix/infra-adapters/packages/web/infra-adapter-state-web |
| @brix-sdk/infra-adapter-ui-mui | 1.0.0 | �?| packages/@brix/infra-adapters/packages/web/infra-adapter-ui-mui |
| @brix-sdk/infra-adapter-ui-native | 1.0.0 | �?| packages/@brix/infra-adapters/packages/web/infra-adapter-ui-native |

### Phase 3: Platform Capabilities (10 packages)

| Package | Version | Status | Path |
|---------|---------|--------|------|
| @brix-sdk/platform-shared | 1.0.0 | �?| packages/@brix/platform-commons/packages/client/platform-shared |
| @brix-sdk/platform-config-web | 1.0.0 | �?| packages/@brix/platform-commons/packages/client/platform-config-web |
| @brix-sdk/platform-eventbus-web | 1.0.0 | �?| packages/@brix/platform-commons/packages/client/platform-eventbus-web |
| @brix-sdk/platform-i18n-web | 1.0.0 | �?| packages/@brix/platform-commons/packages/client/platform-i18n-web |
| @brix-sdk/platform-navigation-web | 1.0.0 | �?| packages/@brix/platform-commons/packages/client/platform-navigation-web |
| @brix-sdk/platform-router-web | 1.0.0 | �?| packages/@brix/platform-commons/packages/client/platform-router-web |
| @brix-sdk/platform-state-web | 1.0.0 | �?| packages/@brix/platform-commons/packages/client/platform-state-web |
| @brix-sdk/platform-auth-web | 1.0.0 | �?| packages/@brix/platform-commons/packages/client/platform-auth-web |
| @brix-sdk/platform-auth-service-web | 1.0.0 | �?| packages/@brix/platform-commons/packages/client/platform-auth-service-web |
| @brix-sdk/platform-auth-ui-web | 1.0.0 | �?| packages/@brix/platform-commons/packages/client/platform-auth-ui-web |

### Phase 4: Devtools (3 packages)

| Package | Version | Status | Path |
|---------|---------|--------|------|
| @brix-sdk/eslint-config-architecture | 1.0.0 | �?| packages/@brix/platform-devtools/eslint-config-architecture |
| @brix-sdk/platform-design-tokens | 1.0.0 | �?| packages/@brix/platform-commons/packages/client/platform-design-tokens |
| @brix-sdk/create-brix | 1.0.0 | �?| packages/@brix/platform-devtools/@brix/create-brix |

### Phase 5: Aggregate Package (1 package)

| Package | Version | Status | Path |
|---------|---------|--------|------|
| @brix-sdk/brix | 1.0.0 | �?| packages/@brix/brix |

**Status Legend:** �?Pending | �?Published | �?Failed

## Publish Commands

```powershell
# Phase 1: Core Runtime SDK
cd d:\1.Sources\brix\packages\@brix\runtime-sdk\runtime-sdk-api-web; npm publish --access public
cd d:\1.Sources\brix\packages\@brix\runtime-sdk\runtime-sdk-react; npm publish --access public
cd d:\1.Sources\brix\packages\@brix\runtime-sdk\runtime-manifest-web; npm publish --access public
cd d:\1.Sources\brix\packages\@brix\runtime-sdk\runtime-orchestrator-web; npm publish --access public
cd d:\1.Sources\brix\packages\@brix\shared-runtime\shared-runtime-web; npm publish --access public

# Phase 2: Infrastructure Adapters
cd d:\1.Sources\brix\packages\@brix\infra-adapters\packages\web\infra-adapter-http-web; npm publish --access public
cd d:\1.Sources\brix\packages\@brix\infra-adapters\packages\web\infra-adapter-iframe-web; npm publish --access public
cd d:\1.Sources\brix\packages\@brix\infra-adapters\packages\web\infra-adapter-mf-web; npm publish --access public
cd d:\1.Sources\brix\packages\@brix\infra-adapters\packages\web\infra-adapter-native-web; npm publish --access public
cd d:\1.Sources\brix\packages\@brix\infra-adapters\packages\web\infra-adapter-router-web; npm publish --access public
cd d:\1.Sources\brix\packages\@brix\infra-adapters\packages\web\infra-adapter-state-web; npm publish --access public
cd d:\1.Sources\brix\packages\@brix\infra-adapters\packages\web\infra-adapter-ui-mui; npm publish --access public
cd d:\1.Sources\brix\packages\@brix\infra-adapters\packages\web\infra-adapter-ui-native; npm publish --access public

# Phase 3: Platform Capabilities
cd d:\1.Sources\brix\packages\@brix\platform-commons\packages\client\platform-shared; npm publish --access public
cd d:\1.Sources\brix\packages\@brix\platform-commons\packages\client\platform-config-web; npm publish --access public
cd d:\1.Sources\brix\packages\@brix\platform-commons\packages\client\platform-eventbus-web; npm publish --access public
cd d:\1.Sources\brix\packages\@brix\platform-commons\packages\client\platform-i18n-web; npm publish --access public
cd d:\1.Sources\brix\packages\@brix\platform-commons\packages\client\platform-navigation-web; npm publish --access public
cd d:\1.Sources\brix\packages\@brix\platform-commons\packages\client\platform-router-web; npm publish --access public
cd d:\1.Sources\brix\packages\@brix\platform-commons\packages\client\platform-state-web; npm publish --access public
cd d:\1.Sources\brix\packages\@brix\platform-commons\packages\client\platform-auth-web; npm publish --access public
cd d:\1.Sources\brix\packages\@brix\platform-commons\packages\client\platform-auth-service-web; npm publish --access public
cd d:\1.Sources\brix\packages\@brix\platform-commons\packages\client\platform-auth-ui-web; npm publish --access public

# Phase 4: Devtools
cd d:\1.Sources\brix\packages\@brix\platform-devtools\@brix\eslint-config-architecture; npm publish --access public
cd d:\1.Sources\brix\packages\@brix\platform-devtools\@brix\design-tokens; npm publish --access public
cd d:\1.Sources\brix\packages\@brix\platform-devtools\@brix\create-brix; npm publish --access public

# Phase 5: Build and publish aggregate package
cd d:\1.Sources\brix\packages\@brix\brix
pnpm install
pnpm build
npm publish --access public
```

## User Installation

After publishing, users only need:

```bash
npm install @brix-sdk/brix
```

```typescript
// Import hooks (most common)
import { useAuth, useHttp, useNavigation } from '@brix-sdk/brix';

// Or import from subpaths
import { RuntimeContext } from '@brix-sdk/brix/runtime';
import { withRetry, SimpleCache } from '@brix-sdk/brix/adapters';
```

## Notes

- All packages start at version 1.0.0
- Browser authentication required for each publish
- If rate limited (E429), wait 5-10 minutes before retrying
- Aggregate package (@brix-sdk/brix) must be published last
