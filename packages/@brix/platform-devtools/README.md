# Platform DevTools

> Brix Platform 开发工具集 - 仅开发时使用

## 概述

`platform-devtools` 包含开发时使用的工具，与运行时 `platform-packages` 分离。

## 包清单

| 包 | 用途 | 安装方式 |
|---|---|---|
| `@brix/create-brix` | 脚手架 CLI（创建插件/服务） | `pnpm create @brix/brix` |
| `@brix/design-tokens` | 设计令牌（颜色、间距、字体） | `devDependencies` |

## 快速开始

### 创建插件

```bash
# 交互式创建
pnpm create @brix/brix plugin

# 指定参数创建
pnpm create @brix/brix plugin user \
  --flyway-prefix 001 \
  --with-web \
  --with-mobile
```

### 创建服务

```bash
# 交互式创建
pnpm create @brix/brix service

# 指定参数创建
pnpm create @brix/brix service platform \
  --port 8080 \
  --plugins user,contract,file-center
```

## 目录结构

```text
platform-devtools/
├── package.json
├── pnpm-workspace.yaml
├── README.md
└── @brix/
    ├── create-brix/             # 脚手架 CLI
    │   ├── src/
    │   │   ├── cli.ts
    │   │   ├── generator.ts
    │   │   ├── prompts.ts
    │   │   ├── types.ts
    │   │   └── generators/
    │   │       ├── plugin.ts
    │   │       └── service.ts
    │   └── templates/
    │       ├── backend/
    │       ├── frontend/
    │       ├── service/
    │       └── common/
    │
    └── design-tokens/             # 设计令牌
        └── src/
            ├── colors.ts
            ├── typography.ts
            ├── spacing.ts
            └── breakpoints.ts
```

## 与 platform-packages 的关系

| 仓库 | 用途 | 依赖时机 |
|---|---|---|
| `platform-packages` | 运行时依赖 | dependencies |
| `platform-devtools` | 开发时工具 | devDependencies |

**规则**：
- 服务/插件的 `dependencies` 只允许依赖 `platform-packages`
- 服务/插件的 `devDependencies` 可以依赖 `platform-devtools`

## 开发

```bash
# 安装依赖
pnpm install

# 构建所有包
pnpm build

# 开发模式
pnpm dev
```

## 版本历史

| 版本 | 日期 | 变更 |
|---|---|---|
| 1.0.0 | 2026-01-15 | 从 platform-packages 拆分 |

## License

MIT
