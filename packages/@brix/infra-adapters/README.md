# Brix Infrastructure Adapters

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Java](https://img.shields.io/badge/Java-17+-green.svg)](https://openjdk.org/)

基础设施适配器层 - Brix 开源框架的 Layer 2.5

---

## 架构定位

**Layer 2.5: 适配器层（Infrastructure Adapters）**

本仓库负责封装 Kafka、Redis、HTTP 等基础设施，实现 runtime-sdk 定义的能力契约。

```text
Layer 3: Host 层（组装层）        ← 引用本层
    ↓
Layer 2.5: 适配器层 ★ 本仓库 ★    ← 实现能力契约
    ↓
Layer 2: 能力契约层              ← 依赖（仅接口）
    ↑
Layer 1: Plugin 层               ← 不依赖本层
```

### 架构红线

| 规则 | 说明 |
|------|------|
| ❌ 禁止被 Plugin 层依赖 | 插件只依赖 runtime-sdk-api |
| ❌ 禁止依赖 platform-commons | 同层模块不互相依赖 |
| ❌ 禁止依赖 shinwa-solutions | 不依赖业务层 |
| ✅ 依赖 runtime-sdk-api | 实现能力契约接口 |
| ✅ 依赖基础设施 SDK | Kafka Client、Redis Client 等 |

---

## 📦 模块列表

| 模块 | 说明 | 实现的能力 |
|-----|------|-----------|
| `infra-adapter-kafka` | Apache Kafka 适配器 | `EventBusCapability` |
| `infra-adapter-redis` | Redis 适配器 | `StateStoreCapability`, `LockCapability` |
| `infra-adapter-webhook` | HTTP Webhook 适配器 | `EventBusCapability` (轻量级) |
| `infra-adapter-otel` | OpenTelemetry 适配器 | `ObservabilityCapability` |

## 🏗️ 架构定位

```
Layer 1: 接口契约层 (runtime-sdk-api)
    ↓ 定义能力接口
Layer 2: 适配器层 (infra-adapters) ← 本仓库
    ↓ 实现能力接口
Layer 3: Host 组装层 (host-assembly)
    ↓ 组装为 RuntimeContext
Layer 4+: 业务模块层
```

## 🚀 快速开始

### Maven 依赖

```xml
<!-- Kafka 事件总线 -->
<dependency>
    <groupId>io.brix</groupId>
    <artifactId>infra-adapter-kafka</artifactId>
    <version>3.0.0-SNAPSHOT</version>
</dependency>

<!-- Redis 状态存储 -->
<dependency>
    <groupId>io.brix</groupId>
    <artifactId>infra-adapter-redis</artifactId>
    <version>3.0.0-SNAPSHOT</version>
</dependency>
```

### Spring Boot 自动配置

适配器模块提供 Spring Boot Auto-Configuration，只需添加依赖即可自动注册。

```yaml
# application.yml
spring:
  kafka:
    bootstrap-servers: localhost:9092
  redis:
    host: localhost
    port: 6379
```

## 📖 命名规范

| 项目 | 规范 | 说明 |
|-----|------|------|
| Maven GroupId | `io.brix` | 品牌标识 |
| Java Package | `io.infra.adapter.*` | 中立命名 |
| 许可证 | Apache 2.0 | 开源 |

## 🔗 相关仓库

- [runtime-sdk](https://github.com/brix-framework/runtime-sdk) - SDK 核心（接口契约）
- [host-assembly](https://github.com/brix-framework/host-assembly) - Host 组装层
- [brix-ui](https://github.com/brix-framework/brix-ui) - 前端 UI SDK

## 📄 License

Apache License 2.0 - 详见 [LICENSE](LICENSE)
