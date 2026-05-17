# @brix-sdk/platform-design-tokens

> Design tokens for Brix Platform

## Installation

```bash
npm install @brix-sdk/platform-design-tokens --save-dev
```

## Usage

### TypeScript/JavaScript

```typescript
import {
  colors,
  spacing,
  typography,
  breakpoints,
  animation,
} from '@brix-sdk/platform-design-tokens';

// Use colors
const primaryColor = colors.brand.primary;

// Use spacing
const padding = spacing.md;

// Use typography
const fontSize = typography.fontSize.base;
```

### CSS

```css
@import '@brix-sdk/platform-design-tokens/css';

.button {
  background-color: var(--color-brand-primary);
  padding: var(--spacing-md);
  font-size: var(--font-size-base);
}
```

## Token Reference

### Colors

- `brand`: Brand colors (primary, secondary, accent)
- `semantic`: Semantic colors (success, warning, error, info)
- `neutral`: Neutral palette (gray-50 ~ gray-900)

### Spacing

- `xs`: 4px
- `sm`: 8px
- `md`: 16px
- `lg`: 24px
- `xl`: 32px
- `2xl`: 48px

### Typography

- `fontFamily`: Font family stack
- `fontSize`: Font sizes (xs ~ 4xl)
- `fontWeight`: Font weights
- `lineHeight`: Line heights

### Breakpoints

- `sm`: 640px
- `md`: 768px
- `lg`: 1024px
- `xl`: 1280px
- `2xl`: 1536px

### Animation

- `duration`: Animation durations
- `easing`: Easing functions

## License

Apache-2.0
