# Changelog

本文件记录 Infra Adapters 的所有重要变更。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [3.2.0] - 2026-02-13

### 新增
- **infra-adapter-http-web**: 完善请求拦截器配置
- **infra-adapter-state-web**: 新增持久化状态支持

### 变更
- **依赖更新**: 升级 React Query 至 v5
- **类型完善**: 改进 TypeScript 类型导出

### 修复
- 修复 HTTP 适配器超时配置不生效问题
- 修复状态适配器内存泄漏问题

---

## [3.1.0] - 2026-01-15

### 新增
- **Web 适配器**
  - `infra-adapter-http-web` - Fetch API 封装
  - `infra-adapter-state-web` - Zustand 状态管理
  - `infra-adapter-router-web` - React Router 封装
  - `infra-adapter-mf-web` - Module Federation 支持
  - `infra-adapter-native-web` - 原生 API 封装

- **Server 适配器**
  - `infra-adapter-cache-redis` - Redis 缓存实现
  - `infra-adapter-eventbus-kafka` - Kafka 事件总线
  - `infra-adapter-lock-redis` - Redis 分布式锁

- **Mobile 适配器**
  - `infra-adapter-device-mobile` - 设备能力
  - `infra-adapter-module-mobile` - 模块加载

### 变更
- 统一条件装配模式

---

## [3.0.0] - 2025-12-01

### 新增
- **适配器模式**: 建立 Capability 实现规范
- **条件装配**: 支持运行时替换实现

### 重大变更
- 从直接技术依赖迁移到能力接口模式
- 所有适配器实现 runtime-sdk-api 定义的接口

---

## [2.x] - 归档版本

2.x 版本已归档，不再维护。请升级到 3.x。

---

[3.2.0]: https://github.com/brix-platform/infra-adapters/compare/v3.1.0...v3.2.0
[3.1.0]: https://github.com/brix-platform/infra-adapters/compare/v3.0.0...v3.1.0
[3.0.0]: https://github.com/brix-platform/infra-adapters/releases/tag/v3.0.0
