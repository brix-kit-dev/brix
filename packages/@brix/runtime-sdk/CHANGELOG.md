# Changelog

本文件记录 Runtime SDK 的所有重要变更。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [3.2.0] - 2026-02-13

### 新增
- **架构守卫**: 新增 RuntimeManifest 验证能力
- **能力接口**: 完善 HttpCapability、StateCapability 类型定义
- **编排器**: RuntimeOrchestrator 支持动态模块加载

### 变更
- **依赖更新**: 升级 TypeScript 至 5.3
- **构建优化**: 改进 pnpm workspace 配置

### 修复
- 修复 RuntimeManifest 类型推导问题
- 修复模块热更新时状态丢失问题

---

## [3.1.0] - 2026-01-15

### 新增
- **runtime-sdk-api**: 新增核心能力接口定义
  - `HttpCapability` - HTTP 请求能力
  - `StateCapability` - 状态管理能力
  - `RouterCapability` - 路由能力
  - `EventBusCapability` - 事件总线能力
- **runtime-manifest**: Manifest 解析和验证
- **runtime-orchestrator**: 运行时编排器

### 变更
- 重构项目结构，采用 monorepo 管理

---

## [3.0.0] - 2025-12-01

### 新增
- **极薄Host架构**: 完整实现运行壳架构
- **架构红线**: 定义 R1-R7 架构约束规则
- **能力模式**: 建立 Capability 模式规范

### 重大变更
- 从传统分层架构迁移到运行壳架构
- 所有能力访问必须通过接口

---

## [2.x] - 归档版本

2.x 版本已归档，不再维护。请升级到 3.x。

---

[3.2.0]: https://github.com/brix-platform/runtime-sdk/compare/v3.1.0...v3.2.0
[3.1.0]: https://github.com/brix-platform/runtime-sdk/compare/v3.0.0...v3.1.0
[3.0.0]: https://github.com/brix-platform/runtime-sdk/releases/tag/v3.0.0
