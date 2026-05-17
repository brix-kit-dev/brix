# @brix-sdk/platform-i18n-web

> Internationalization capability implementation for Brix Platform

## Overview

This package implements the I18nCapability interface, providing internationalization support for the Brix Runtime platform.

## Features

- Multi-language support
- Dynamic language switching
- Namespace-based translations
- Interpolation and formatting
- Plugin-scoped translations
- i18next integration

## Installation

```bash
npm install @brix-sdk/platform-i18n-web
```

## Usage

```typescript
import { createI18nCapability } from '@brix-sdk/platform-i18n-web';

// Create i18n capability
const i18n = await createI18nCapability({
  defaultLanguage: 'en',
  supportedLanguages: ['en', 'zh', 'ja'],
  loadPath: '/locales/{{lng}}/{{ns}}.json'
});

// Translate text
const greeting = i18n.t('common:greeting'); // "Hello"

// With interpolation
const welcome = i18n.t('common:welcome', { name: 'John' }); // "Welcome, John!"

// Change language
await i18n.changeLanguage('zh');
```

## React Integration

```tsx
import { useTranslation } from '@brix-sdk/platform-i18n-web/react';

function MyComponent() {
  const { t, i18n } = useTranslation('my-plugin');
  
  return (
    <div>
      <h1>{t('title')}</h1>
      <button onClick={() => i18n.changeLanguage('zh')}>
        Switch to Chinese
      </button>
    </div>
  );
}
```

## License

Apache-2.0
