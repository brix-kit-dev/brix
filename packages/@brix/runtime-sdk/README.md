# Brix Runtime SDK

> **版本**: 3.0.0-SNAPSHOT  
> **许可**: Apache License 2.0  
> **品牌**: Brix（开源框架）
> **命名策略**: GroupId 使用品牌（`io.brix`），代码包名保持中立（`io.runtime.*`）

---

## 架构定位

**Layer 2: 能力契约层（Capability Contract）**

本仓库是 Brix Platform v3.0 运行壳架构的核心抽象层，定义模块与运行时之间的标准接口契约。

```text
Layer 3: Host 层（组装层）        ← 引用本层
    ↓
Layer 2.5: 能力实现层            ← 实现本层接口
    ↓
Layer 2: 能力契约层 ★ 本仓库 ★    ← 纯接口定义
    ↑
Layer 1: Plugin 层               ← 仅依赖本层
```

### 架构红线

| 规则 | 说明 |
|------|------|
| ❌ 禁止依赖任何实现模块 | 不依赖 infra-adapters、platform-commons |
| ❌ 禁止依赖基础设施 SDK | 不依赖 Kafka、Redis、MySQL 等 |
| ✅ 仅允许最小依赖 | 仅 slf4j-api 等日志门面 |
| ✅ 所有接口为纯契约定义 | 无具体实现代码 |

---

## 概述

Brix Runtime SDK 是运行壳（Runtime Shell）的核心抽象层，提供模块化应用的运行时能力契约。

### 核心特性

- **能力契约（Capability Contract）**：定义模块与运行时之间的标准接口
- **模块生命周期管理**：统一的初始化、启动、停止、销毁流程
- **声明式事件分发**：基于 Manifest 的事件订阅与发布
- **基础设施解耦**：模块不依赖任何具体技术实现

---

## 模块结构

```
runtime-sdk/
├── 【后端 Java 模块】
│   ├── runtime-sdk-api/          # 能力契约接口 + 注解 + 支持类
│   ├── runtime-orchestrator/     # 编排引擎（生命周期管理、事件分发）
│   └── runtime-manifest/         # Manifest 解析器
│
├── 【前端 TypeScript 模块 - Web】
│   ├── runtime-sdk-api-web/      # Web 能力契约 + 共享类型 + Hooks
│   ├── runtime-orchestrator-web/ # Web 运行时编排器
│   ├── runtime-manifest-web/     # Web 清单解析器
│   └── runtime-sdk-react/        # React 绑定 + 组件库 + Hooks
│
└── 【前端 TypeScript 模块 - Mobile】
    └── runtime-sdk-api-mobile/   # React Native 能力契约 + 移动端类型
```

### 命名规范

| 维度 | 命名 | 说明 |
|------|------|------|
| **Maven GroupId** | `io.brix` | 品牌标识，用于依赖管理 |
| **Maven ArtifactId** | `runtime-sdk-api`, `runtime-orchestrator` 等 | 中立命名，一眼看出作用 |
| **Java 包名** | `io.runtime.sdk.*`, `io.runtime.orchestrator.*` | 中立命名，代码透明 |
| **npm Scope** | `@brix` | 品牌标识，用于前端包管理 |

### 后端模块说明

| 模块 | 说明 | 依赖 |
|------|------|------|
| `runtime-sdk-api` | Capability 接口 + @Module/@EventHandler 注解 + AbstractModule 支持类 | 无外部依赖 |
| `runtime-orchestrator` | 模块注册、生命周期管理、事件分发 | runtime-sdk-api, runtime-manifest |
| `runtime-manifest` | YAML Manifest 加载与校验 | SnakeYAML |

### 前端模块说明

#### Web 模块

| 模块 | 说明 | 依赖 |
|------|------|------|
| `@brix/runtime-sdk-api-web` | Web Capability 接口 + 共享类型 + Hooks | React |
| `@brix/runtime-orchestrator-web` | 插件管理、能力装配、运行时上下文 | runtime-sdk-api-web |
| `@brix/runtime-manifest-web` | Web 端清单解析与验证 | 无 |
| `@brix/runtime-sdk-react` | React 组件绑定 + 能力注入 Hooks + Provider | React, runtime-sdk-api-web |

#### Mobile 模块

| 模块 | 说明 | 依赖 |
|------|------|------|
| `@brix/runtime-sdk-api-mobile` | React Native 能力契约 + 移动端特定 Capability 类型 | React Native |

---

## 快速开始

### 后端：添加依赖

```xml
<!-- 模块开发只需依赖 runtime-sdk-api -->
<dependency>
    <groupId>io.brix</groupId>
    <artifactId>runtime-sdk-api</artifactId>
    <version>3.0.0-SNAPSHOT</version>
</dependency>
```

### 前端：添加依赖

```bash
# 插件开发只需依赖 runtime-sdk-api-web
pnpm add @brix/runtime-sdk-api-web
```

### 后端：定义模块

```java
import io.runtime.sdk.annotation.Module;
import io.runtime.sdk.support.AbstractModule;

@Module(id = "my-module", name = "我的模块")
public class MyModule extends AbstractModule {
    
    @Override
    protected void doInit(RuntimeContext context) {
        // 初始化逻辑
    }
    
    @Override
    protected void doStart() {
        // 启动逻辑
    }
    
    @EventHandler
    public void onUserCreated(UserCreatedEvent event) {
        // 处理事件
    }
}
```

### 3. 声明 Manifest

```yaml
# module-manifest.yaml
module:
  id: my-module
  name: 我的模块
  version: 1.0.0

capabilities:
  required:
    - event-bus
    - state-store

events:
  publishes:
    - type: com.example.MyEvent
  subscribes:
    - type: com.example.UserCreatedEvent
      handler: com.example.MyModule.onUserCreated
```

---

## 能力契约

Runtime SDK 定义了以下核心能力：

| 能力 | 接口 | 说明 |
|------|------|------|
| 事件总线 | `EventBusCapability` | 发布领域事件和集成事件 |
| 状态存储 | `StateStoreCapability` | 键值存储抽象 |
| 认证上下文 | `AuthContextCapability` | 当前用户身份与权限 |
| 可观测性 | `ObservabilityCapability` | 日志、指标、追踪 |
| 配置存储 | `ConfigStoreCapability` | 配置读取 |
| 生命周期 | `LifecycleCapability` | 模块生命周期回调 |
| 韧性能力 | `ResilienceCapability` | 熔断、限流、降级 |
| 分布式锁 | `LockCapability` | 分布式锁（可选） |
| 定时任务 | `SchedulingCapability` | 定时任务调度（可选） |

---

## 架构原则

### 红线约束

1. **模块只依赖 Capability Contract**：禁止直接使用 Kafka、Redis、HTTP Client 等基础设施
2. **事件通信解耦**：模块之间只通过事件通信，不直接调用
3. **声明式配置**：通过 Manifest 声明能力需求和事件订阅

### 分层架构

```
┌─────────────────────────────────────┐
│  Host 层（Capability 实现）          │  ← 不同 Host 提供不同实现
├─────────────────────────────────────┤
│  Runtime Shell（能力契约）           │  ← 本 SDK 定义
├─────────────────────────────────────┤
│  Plugin/Module（业务逻辑）           │  ← 仅依赖 SDK
├─────────────────────────────────────┤
│  Infrastructure（基础设施）          │  ← 对模块不可见
└─────────────────────────────────────┘
```

---

## 构建

```bash
# 编译所有模块
mvn clean install

# 跳过测试
mvn clean install -DskipTests

# 生成源码和文档
mvn clean install -P release
```

---

## 许可证

本项目采用 [Apache License 2.0](LICENSE) 许可证。
