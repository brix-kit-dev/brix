# Contributing to Brix Platform - Runtime SDK

> **版本**: v3.2.0  
> **最后更新**: 2026-02-13

感谢您对 Brix Platform Runtime SDK 的贡献！本文档提供了参与项目开发所需的指南。

## 目录

1. [开发环境设置](#开发环境设置)
2. [架构指南](#架构指南)
3. [开发规范](#开发规范)
4. [提交流程](#提交流程)

---

## 开发环境设置

### 环境要求

- **Node.js**: >= 18.0.0
- **pnpm**: >= 8.0.0
- **TypeScript**: >= 5.0.0

### 快速开始

```bash
# 1. 克隆仓库
git clone https://github.com/brix-platform/runtime-sdk.git
cd runtime-sdk

# 2. 安装依赖
pnpm install

# 3. 构建项目
pnpm build

# 4. 运行测试
pnpm test

# 5. 运行 lint 检查
pnpm lint
```

### 项目结构

```
runtime-sdk/
├── runtime-manifest/           # 清单定义（Java）
├── runtime-manifest-web/       # 清单定义（Web）
├── runtime-orchestrator/       # 运行编排器（Java）
├── runtime-orchestrator-web/   # 运行编排器（Web）
├── runtime-sdk-api/            # SDK API 契约（Java）
├── runtime-sdk-api-web/        # SDK API 契约（Web）
└── architecture-guard/         # 架构守护
```

---

## 架构指南

### 极薄Host架构原则 (Extreme Thin Host)

Runtime SDK 遵循"极薄Host架构"原则：

- **runtime-sdk-api(-web)** 是纯契约层，不包含任何框架特定代码
- React Hooks 等框架绑定代码应放在 **runtime-sdk-react** 包中
- 能力实现在 **infra-adapters** 或 **platform-commons** 中

### 架构红线 (Architecture Red Lines)

提交代码前，请确保不违反以下红线：

| 红线编号 | 规则 | 说明 |
|---------|------|------|
| **R1** | 禁止循环依赖 | 不允许模块间循环引用 |
| **R2** | 契约层禁止实现 | runtime-sdk-api 只定义接口 |
| **R3** | 禁止直接HTTP客户端 | 使用 `HttpCapability` 而非 fetch/axios |
| **R4** | 禁止直接状态存储 | 使用 `StateStoreCapability` 而非 localStorage |
| **R5** | 禁止跨层依赖 | 上层可依赖下层，反之禁止 |

### 层次结构

```
Layer 4: 售卖层 (Commercial)
    ↓
Layer 3: Host层 (极薄组装层)
    ↓
Layer 2.5: 能力实现层 (infra-adapters, platform-commons)
    ↓
Layer 2: 能力契约层 (runtime-sdk-api) ← 本仓库
    ↓
Layer 1: 插件层 (shinwa-solutions)
```

---

## 开发规范

### TypeScript 规范

```typescript
// ✅ 正确：导出接口定义
export interface HttpCapability {
  get<T>(url: string, params?: Record<string, unknown>): Promise<T>;
  post<T>(url: string, data?: unknown): Promise<T>;
}

// ❌ 错误：在契约层包含实现
export class HttpCapabilityImpl implements HttpCapability {
  // 实现代码应在 infra-adapters
}
```

### 命名规范

| 类型 | 命名规则 | 示例 |
|------|---------|------|
| 接口 | PascalCase | `HttpCapability`, `EventBusCapability` |
| 类型 | PascalCase | `NavigationOptions`, `PluginManifest` |
| 函数 | camelCase | `createHttpCapability`, `useHttp` |
| 常量 | UPPER_SNAKE_CASE | `DEFAULT_TIMEOUT`, `API_VERSION` |

### 注释规范

所有公开 API 必须包含 JSDoc/TSDoc 注释：

```typescript
/**
 * HTTP 能力接口
 * 
 * 【架构说明】
 * 本接口定义于能力契约层，实现在 infra-adapters。
 * 插件层通过依赖注入获取实例，禁止直接实例化。
 * 
 * @since 3.0.0
 * @see v3.0-运行壳架构设计蓝图.md §7.1
 */
export interface HttpCapability {
  /**
   * 发送 GET 请求
   * 
   * @typeParam T - 响应数据类型
   * @param url - 请求 URL
   * @param params - 查询参数
   * @returns Promise 包含响应数据
   */
  get<T>(url: string, params?: Record<string, unknown>): Promise<T>;
}
```

---

## 提交流程

### 分支命名

- `feature/xxx` - 新功能
- `fix/xxx` - Bug 修复
- `refactor/xxx` - 重构
- `docs/xxx` - 文档更新

### Commit 规范

使用 Conventional Commits 格式：

```
<type>(<scope>): <subject>

<body>

<footer>
```

**类型 (type)**:
- `feat`: 新功能
- `fix`: Bug 修复
- `docs`: 文档
- `refactor`: 重构
- `test`: 测试
- `chore`: 构建/工具

**示例**:
```
feat(runtime-sdk-api-web): add ConfigStoreCapability interface

添加配置存储能力接口，用于统一配置访问。
符合架构红线 R6 要求。

Refs: #123
```

### Pull Request 流程

1. **创建 PR 前**
   ```bash
   # 运行架构测试
   mvn test -Dtest="*ArchitectureTest"
   
   # 运行 lint
   pnpm lint
   
   # 运行单元测试
   pnpm test
   ```

2. **PR 标题格式**: `[模块名] 简短描述`

3. **PR 描述模板**:
   ```markdown
   ## 变更说明
   - 描述本次变更的内容
   
   ## 关联问题
   - Closes #xxx
   
   ## 测试验证
   - [ ] 单元测试通过
   - [ ] 架构测试通过
   - [ ] Lint 检查通过
   
   ## 架构合规
   - [ ] 不违反架构红线
   - [ ] 符合极薄Host原则
   ```

4. **代码审查**: 至少需要 1 位维护者审批

---

## 联系方式

- **Issue 追踪**: GitHub Issues
- **技术讨论**: GitHub Discussions
- **安全问题**: security@brix.dev

---

感谢您的贡献！
