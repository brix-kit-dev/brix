# @brix/design-tokens

> Brix Platform 设计令牌

## 安装

```bash
pnpm add -D @brix/design-tokens
```

## 使用

### TypeScript/JavaScript

```typescript
import {
  colors,
  spacing,
  typography,
  breakpoints,
  animation,
} from '@brix/design-tokens';

// 使用颜色
const primaryColor = colors.brand.primary;

// 使用间距
const padding = spacing.md;

// 使用字体
const fontSize = typography.fontSize.base;
```

### CSS

```css
@import '@brix/design-tokens/css';

.button {
  background-color: var(--color-brand-primary);
  padding: var(--spacing-md);
  font-size: var(--font-size-base);
}
```

## 令牌清单

### 颜色 (colors)

- `brand`: 品牌色（primary, secondary, accent）
- `semantic`: 语义色（success, warning, error, info）
- `neutral`: 中性色（gray-50 ~ gray-900）

### 间距 (spacing)

- `xs`: 4px
- `sm`: 8px
- `md`: 16px
- `lg`: 24px
- `xl`: 32px
- `2xl`: 48px

### 排版 (typography)

- `fontFamily`: 字体栈
- `fontSize`: 字号（xs ~ 4xl）
- `fontWeight`: 字重
- `lineHeight`: 行高

### 断点 (breakpoints)

- `sm`: 640px
- `md`: 768px
- `lg`: 1024px
- `xl`: 1280px
- `2xl`: 1536px

### 动画 (animation)

- `duration`: 持续时间
- `easing`: 缓动函数

## License

MIT
