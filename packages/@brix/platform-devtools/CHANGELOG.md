# Changelog

本文件记录 Platform DevTools 的所有重要变更。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [3.2.0] - 2026-02-13

### 新增
- **architecture-guard**: 新增 R6、R7 红线规则实现
- **eslint-config-architecture**: 新增前端跨层依赖检测

### 变更
- **ArchUnit**: 升级至 1.2.x，改进规则表达能力
- **规则优化**: 改进错误提示信息，增加修复建议

### 修复
- 修复 Manifest 声明规则误报问题
- 修复 ESLint 规则与 TypeScript 5.3 兼容性问题

---

## [3.1.0] - 2026-01-15

### 新增
- **架构守卫**
  - `DependencyDirectionRule` - 依赖方向检查 (R2)
  - `NoCircularDependencyRule` - 循环依赖检测 (R1)
  - `CapabilityInterfaceRule` - 能力接口检查 (R3)
  - `ManifestDeclarationRule` - Manifest 声明检查 (R4)
  - `ContractLocationRule` - 契约位置检查 (R5)

- **脚手架工具**
  - `create-brix` - 插件模块生成器
  - `design-tokens` - 设计令牌管理

- **Lint 配置**
  - `eslint-config-architecture` - 前端架构规则

### 变更
- 基于 ArchUnit 重构架构测试

---

## [3.0.0] - 2025-12-01

### 新增
- **DevTools 体系**: 建立开发时工具集
- **架构红线**: 定义 R1-R7 规则和检测机制

### 重大变更
- 从运行时检查迁移到编译时检查
- 引入 ArchUnit 作为架构测试框架

---

## [2.x] - 归档版本

2.x 版本已归档，不再维护。请升级到 3.x。

---

[3.2.0]: https://github.com/brix-platform/platform-devtools/compare/v3.1.0...v3.2.0
[3.1.0]: https://github.com/brix-platform/platform-devtools/compare/v3.0.0...v3.1.0
[3.0.0]: https://github.com/brix-platform/platform-devtools/releases/tag/v3.0.0
