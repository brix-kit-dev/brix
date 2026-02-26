# Contributing to Brix Platform - Platform DevTools

> **版本**: v3.2.0  
> **最后更新**: 2026-02-13

感谢您对 Brix Platform DevTools 的贡献！本文档提供了参与项目开发所需的指南。

## 目录

1. [开发环境设置](#开发环境设置)
2. [架构指南](#架构指南)
3. [开发规范](#开发规范)
4. [提交流程](#提交流程)

---

## 开发环境设置

### 环境要求

- **Java**: JDK 17+
- **Maven**: 3.8+
- **Node.js**: >= 18.0.0
- **pnpm**: >= 8.0.0

### 快速开始

```bash
# 1. 克隆仓库
git clone https://github.com/brix-platform/platform-devtools.git
cd platform-devtools

# 2. 安装依赖并构建
mvn clean install -DskipTests
pnpm install

# 3. 运行架构检查
mvn test -Dtest="*ArchitectureTest"
```

### 项目结构

```
platform-devtools/
├── @brix/
│   ├── create-brix/                # CLI 脚手架工具
│   └── design-tokens/              # 设计令牌
├── architecture-guard/             # 架构守卫 (ArchUnit)
│   ├── src/
│   │   └── test/java/
│   │       └── com/shinwa/architecture/
│   │           ├── ArchitectureTest.java
│   │           └── rules/
│   └── pom.xml
├── eslint-config-architecture/     # 前端架构 ESLint 规则
│   └── index.js
└── pom.xml
```

---

## 架构指南

### DevTools 职责

platform-devtools 提供**开发时工具**：

- **架构守卫**: 编译期架构约束检查
- **脚手架**: 项目/模块生成器
- **Lint 配置**: 代码质量规则

### 架构守卫 (Architecture Guard)

```
┌─────────────────────────────────────────────────────────────────┐
│  编译时 / CI Pipeline                                           │
├─────────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  ArchUnit Tests (Java)                                      ││
│  │  • 红线规则检查                                              ││
│  │  • 依赖方向检查                                              ││
│  │  • 循环依赖检测                                              ││
│  └─────────────────────────────────────────────────────────────┘│
│  ┌─────────────────────────────────────────────────────────────┐│
│  │  ESLint Rules (TypeScript)                                  ││
│  │  • 禁止直接 import 运行时模块                                ││
│  │  • 禁止跨层依赖                                              ││
│  │  • 命名规范检查                                              ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

### 架构红线实现

| 红线编号 | 守卫实现 |
|---------|----------|
| **R1** | `NoCircularDependencyRule.java` |
| **R2** | `DependencyDirectionRule.java` |
| **R3** | `CapabilityInterfaceRule.java` |
| **R4** | `ManifestDeclarationRule.java` |
| **R5** | `ContractLocationRule.java` |
| **R6** | `PluginConfigExternalizationRule.java` |
| **R7** | `RouteDeclarationRule.java` |

---

## 开发规范

### ArchUnit 规则编写

```java
/**
 * 依赖方向检查规则
 * 
 * 【架构红线 R2 实现】
 * 验证依赖只能向下，禁止下层依赖上层：
 * - Layer 4 (插件) → Layer 3 (Host) ✗
 * - Layer 3 (Host) → Layer 2 (SDK) ✓
 * 
 * @author Brix Team
 * @since 3.0.0
 */
public class DependencyDirectionRule {
    
    /**
     * 验证 Host 层不依赖插件层
     */
    @ArchTest
    static final ArchRule hostShouldNotDependOnPlugins = 
        noClasses()
            .that().resideInAPackage("..host..")
            .should().dependOnClassesThat()
            .resideInAPackage("..plugin..");
    
    /**
     * 验证 SDK 层不依赖 Host 层
     */
    @ArchTest
    static final ArchRule sdkShouldNotDependOnHost = 
        noClasses()
            .that().resideInAPackage("..sdk..")
            .should().dependOnClassesThat()
            .resideInAPackage("..host..");
}
```

### ESLint 规则编写

```javascript
/**
 * 前端架构规则配置
 * 
 * 【规则说明】
 * 实现前端架构红线检查，包括：
 * - 禁止直接导入运行时模块
 * - 禁止跨层依赖
 * - 强制使用能力接口
 * 
 * @module eslint-config-architecture
 * @version 3.2.0
 */
module.exports = {
    rules: {
        // 禁止插件层直接导入 Host 模块
        'no-restricted-imports': ['error', {
            patterns: [
                {
                    group: ['**/host-shell-*/**'],
                    message: '插件层不能直接依赖 Host 层，请使用 SDK 能力接口'
                }
            ]
        }],
        
        // 其他规则...
    }
};
```

### 脚手架开发规范

```typescript
/**
 * 插件模块生成器
 * 
 * 【功能说明】
 * 生成符合架构规范的插件模块骨架代码，包括：
 * - manifest.json 配置
 * - 标准目录结构
 * - 基础文件模板
 * 
 * @module create-brix
 * @version 3.2.0
 */
export async function createPlugin(options: CreatePluginOptions): Promise<void> {
    // 实现
}
```

---

## 提交流程

### 分支命名

- `feature/xxx` - 新规则或功能
- `fix/xxx` - 规则 Bug 修复
- `docs/xxx` - 文档更新

### Commit 规范

```
feat(architecture-guard): add manifest declaration rule

新增 R4 红线规则实现：Manifest 声明检查。

Refs: #234
```

### Pull Request 检查清单

对于架构规则变更：
- [ ] 规则对应特定红线编号
- [ ] 包含正例和反例测试
- [ ] 错误消息清晰指导修复方向
- [ ] 不影响现有合规代码

---

## 联系方式

- **Issue 追踪**: GitHub Issues
- **技术讨论**: GitHub Discussions
- **安全问题**: security@brix.dev

---

感谢您的贡献！
