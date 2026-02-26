# Platform Commons

> **版本**: 3.0.0-SNAPSHOT  
> **许可**: Apache License 2.0  
> **品牌**: Brix（开源框架）

---

## 架构定位

**Layer 2.5: 平台通用能力层（Platform Commons）**

本仓库实现平台级通用能力，包括鉴权、网关、可观测性、国际化等。

```text
Layer 3: Host 层（组装层）        ← 引用本层
    ↓
Layer 2.5: 平台通用能力层 ★ 本仓库 ★  ← 实现平台能力
    ↓
Layer 2: 能力契约层              ← 依赖（仅接口）
    ↑
Layer 1: Plugin 层               ← 可选依赖本层（如 auth）
```

### 架构红线

| 规则 | 说明 |
|------|------|
| ❌ 禁止依赖 shinwa-solutions | 不依赖业务层 |
| ✅ 依赖 runtime-sdk-api | 实现能力契约接口 |
| ✅ 可依赖 infra-adapters | 可选，用于基础设施集成 |
| ✅ 实现平台级通用能力 | 鉴权、网关、可观测性等 |

---

## 📦 模块结构

```
platform-commons/
├── packages/
│   ├── client/                    # 前端能力实现
│   │   ├── platform-auth-web/     # 认证能力
│   │   ├── platform-eventbus-web/ # 事件总线
│   │   ├── platform-i18n-web/     # 国际化
│   │   ├── platform-navigation-web/ # 导航
│   │   ├── platform-router-web/   # 路由封装
│   │   ├── platform-shared/       # 共享代码
│   │   └── platform-state-web/    # 状态管理
│   │
│   └── server/                    # 后端能力实现
│       ├── platform-parent/       # 依赖管理 BOM
│       ├── platform-common/       # 公共 Entity/DTO
│       ├── platform-common-starter/ # 自动配置
│       ├── platform-gateway/      # API 网关
│       ├── platform-auth/         # JWT 认证
│       ├── platform-observability/ # 可观测性
│       └── platform-config/       # 配置中心
```

---

## 前端能力

| 模块 | 包名 | 实现的能力契约 |
|------|------|---------------|
| platform-auth-web | `@brix/platform-auth-web` | `AuthCapability` |
| platform-eventbus-web | `@brix/platform-eventbus-web` | `GovernedEventBusCapability` |
| platform-i18n-web | `@brix/platform-i18n-web` | `I18nCapability` |
| platform-navigation-web | `@brix/platform-navigation-web` | `NavigationCapability` |
| platform-state-web | `@brix/platform-state-web` | `PluginStateCapability` |

---

## 后端能力

| 模块 | GroupId:ArtifactId | 功能 |
|------|-------------------|------|
| platform-gateway | `io.platform:platform-gateway` | API 网关、路由、限流、熔断 |
| platform-auth | `io.platform:platform-auth` | JWT 验证、权限注解 |
| platform-observability | `io.platform:platform-observability` | 链路追踪、日志、指标 |
| platform-config | `io.platform:platform-config` | 配置加载、动态刷新 |

---

## 快速开始

### 后端依赖

```xml
<dependency>
    <groupId>shinwa.platform</groupId>
    <artifactId>platform-common-starter</artifactId>
    <version>3.0.0-SNAPSHOT</version>
</dependency>
```

### 前端依赖

```bash
pnpm add @brix/platform-auth-web
```

---

## 📖 命名规范

| 项目 | 规范 | 说明 |
|-----|------|------|
| npm Scope | `@brix` | 开源框架品牌 |
| Maven GroupId | `io.platform` / `shinwa.platform` | 待统一 |
| 许可证 | Apache 2.0 | 开源 |

---

## 📄 License

Apache License 2.0 - 详见 [LICENSE](LICENSE)
