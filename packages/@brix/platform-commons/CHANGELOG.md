# Changelog

本文件记录 Platform Commons 的所有重要变更。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [3.2.0] - 2026-02-13

### 新增
- **commons-ui-components-web**: 新增 Modal、Toast 组件
- **commons-utils-web**: 新增日期格式化工具

### 变更
- **依赖更新**: 升级 Ant Design 至 5.x
- **样式优化**: 改进暗色主题支持

### 修复
- 修复 Table 组件排序状态不同步问题
- 修复工具函数类型推导不完整问题

### 删除
- 移除废弃的编码修复脚本（8个）

---

## [3.1.0] - 2026-01-15

### 新增
- **客户端模块**
  - `commons-ui-components-web` - 通用 UI 组件库
  - `commons-ui-styles-web` - 样式和主题
  - `commons-utils-web` - 前端工具函数

- **服务端模块**
  - `commons-auth` - 认证通用功能
  - `commons-web` - Web 层通用功能
  - `commons-utils` - 后端工具类

### 变更
- 重构为 monorepo 结构

---

## [3.0.0] - 2025-12-01

### 新增
- **工具层**: 建立与业务无关的公共模块

### 重大变更
- 从业务模块中抽取通用代码
- 统一工具类命名规范

---

## [2.x] - 归档版本

2.x 版本已归档，不再维护。请升级到 3.x。

---

[3.2.0]: https://github.com/brix-platform/platform-commons/compare/v3.1.0...v3.2.0
[3.1.0]: https://github.com/brix-platform/platform-commons/compare/v3.0.0...v3.1.0
[3.0.0]: https://github.com/brix-platform/platform-commons/releases/tag/v3.0.0
