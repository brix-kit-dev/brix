# @brix/create-brix

> Brix Platform 脚手架 CLI - 创建插件和服务

## 安装

```bash
# 全局安装
pnpm add -g @brix/create-brix

# 或使用 pnpm create
pnpm create @brix/brix
```

## 使用

### 创建插件

```bash
# 交互式创建插件
pnpm create @brix/brix plugin

# 快速创建插件（指定所有参数）
pnpm create @brix/brix plugin user \
  --flyway-prefix 001 \
  --with-web \
  --with-mobile \
  --output-dir ./plugins
```

#### 插件参数

| 参数 | 缩写 | 说明 | 默认值 |
|---|---|---|---|
| `--flyway-prefix` | `-f` | Flyway 版本前缀（3位数字） | 交互输入 |
| `--with-web` | | 包含 Web 前端模块 | `true` |
| `--with-mobile` | | 包含 Mobile 前端模块 | `false` |
| `--with-api` | | 包含 API 模块 | `true` |
| `--output-dir` | `-o` | 输出目录 | 当前目录 |

### 创建服务

```bash
# 交互式创建服务
pnpm create @brix/brix service

# 快速创建服务（指定所有参数）
pnpm create @brix/brix service platform \
  --port 8080 \
  --plugins user,contract,file-center \
  --output-dir ./services
```

#### 服务参数

| 参数 | 缩写 | 说明 | 默认值 |
|---|---|---|---|
| `--port` | `-p` | 服务端口号 | 交互输入 |
| `--plugins` | | 依赖的插件列表（逗号分隔） | 交互选择 |
| `--with-docker` | | 生成 Docker 配置 | `true` |
| `--with-k8s` | | 生成 Kubernetes 配置 | `false` |
| `--output-dir` | `-o` | 输出目录 | 当前目录 |

### 通用参数

| 参数 | 说明 | 默认值 |
|---|---|---|
| `--skip-install` | 跳过依赖安装 | `false` |
| `--skip-git` | 跳过 git 初始化 | `false` |
| `--dry-run` | 仅预览，不实际创建 | `false` |

## 生成结构

### 插件结构

```text
plugin-<name>/
├── pom.xml
├── README.md
├── plugin-<name>-api/           # API 模块（可选）
│   ├── pom.xml
│   └── src/
├── plugin-<name>-core/          # Core 模块
│   ├── pom.xml
│   └── src/
├── plugin-<name>-web/           # Web 前端（可选）
│   ├── package.json
│   └── src/
└── plugin-<name>-mobile/        # Mobile 前端（可选）
    ├── package.json
    └── src/
```

### 服务结构

```text
shinwa-service-<name>/
├── pom.xml
├── README.md
├── Dockerfile
├── docker-compose.yml
└── src/
    └── main/
        ├── java/
        │   └── shinwa/service/<name>/
        │       ├── Application.java
        │       └── config/
        └── resources/
            ├── application.yml
            └── db/migration/
```

## Flyway 前缀分配

| 插件 | 前缀 |
|---|---|
| plugin-user | 001 |
| plugin-contract | 002 |
| plugin-file-center | 003 |
| plugin-notification | 004 |
| plugin-partner-catalog | 005 |
| plugin-service-package | 006 |
| plugin-case-engine | 010-019 |
| plugin-medical-* | 020-029 |

## License

MIT
