# Contributing to Brix Platform - Platform Commons

> **版本**: v3.2.0  
> **最后更新**: 2026-02-13

感谢您对 Brix Platform Commons 的贡献！本文档提供了参与项目开发所需的指南。

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
git clone https://github.com/brix-platform/platform-commons.git
cd platform-commons

# 2. 安装依赖并构建
mvn clean install
pnpm install

# 3. 运行测试
mvn test
pnpm test
```

### 项目结构

```
platform-commons/
├── packages/
│   ├── client/                           # 客户端公共模块
│   │   ├── commons-ui-components-web/    # UI 组件库
│   │   ├── commons-ui-styles-web/        # 样式库
│   │   └── commons-utils-web/            # 工具库
│   └── server/                           # 服务端公共模块
│       ├── commons-auth/                 # 认证通用
│       ├── commons-web/                  # Web 通用
│       └── commons-utils/                # 工具类
└── pom.xml
```

---

## 架构指南

### Commons 职责

platform-commons 是**公共工具层 (Layer 1)**，提供：

- 跨模块复用的工具类和组件
- 与业务无关的基础设施代码
- 通用 UI 组件（客户端）
- 通用工具方法

### 架构定位

```
┌─────────────────────────────────────────────────────────────────┐
│  业务插件层 (Layer 4)                                           │
│  • 使用 commons 提供的工具和组件                                 │
└─────────────────────────────────────────────────────────────────┘
                              │ 依赖
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  runtime-sdk-api (Layer 2)                                      │
│  • 可使用 commons 工具                                          │
└─────────────────────────────────────────────────────────────────┘
                              │ 依赖
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  platform-commons (Layer 1) - 公共基础                          │  ← 本仓库
│  • 无业务逻辑                                                   │
│  • 无架构约束                                                   │
│  • 纯工具性质                                                   │
└─────────────────────────────────────────────────────────────────┘
```

### Commons 设计原则

| 原则 | 说明 |
|------|------|
| **无业务逻辑** | Commons 不包含任何业务相关代码 |
| **稳定性优先** | API 变更需要严格评审 |
| **最小依赖** | 尽量减少外部依赖 |
| **向后兼容** | 不破坏已有 API |

---

## 开发规范

### Java 工具类规范

```java
/**
 * 字符串工具类
 * 
 * 【设计说明】
 * 提供通用的字符串处理方法，不依赖任何业务逻辑。
 * 所有方法都是静态方法，线程安全。
 * 
 * 【使用示例】
 * <pre>{@code
 * String result = StringUtils.camelToSnake("userName");
 * // 返回: "user_name"
 * }</pre>
 * 
 * @author Brix Team
 * @since 1.0.0
 */
public final class StringUtils {
    
    private StringUtils() {
        throw new UnsupportedOperationException("Utility class");
    }
    
    /**
     * 将驼峰命名转换为蛇形命名
     * 
     * @param input 驼峰命名字符串，不能为 null
     * @return 蛇形命名字符串
     * @throws IllegalArgumentException 如果 input 为 null
     */
    public static String camelToSnake(String input) {
        // 实现
    }
}
```

### TypeScript 组件规范

```typescript
/**
 * 通用按钮组件
 * 
 * 【设计说明】
 * 基础按钮组件，支持多种变体和尺寸。
 * 不包含任何业务逻辑，纯展示组件。
 * 
 * 【使用示例】
 * ```tsx
 * <Button variant="primary" size="medium" onClick={handleClick}>
 *   点击我
 * </Button>
 * ```
 * 
 * @module commons-ui-components-web
 * @version 3.2.0
 */
export interface ButtonProps {
    /** 按钮变体 */
    variant?: 'primary' | 'secondary' | 'text';
    /** 尺寸 */
    size?: 'small' | 'medium' | 'large';
    /** 点击回调 */
    onClick?: () => void;
    /** 子元素 */
    children: React.ReactNode;
}

export const Button: React.FC<ButtonProps> = ({ 
    variant = 'primary',
    size = 'medium',
    onClick,
    children 
}) => {
    // 实现
};
```

### 命名规范

| 类型 | Java | TypeScript |
|------|------|------------|
| 工具类 | `XxxUtils` | `xxxUtils.ts` |
| 常量类 | `XxxConstants` | `xxxConstants.ts` |
| 组件 | - | `PascalCase.tsx` |
| 模块名 | `commons-xxx` | `@brix/commons-xxx` |

### 测试覆盖要求

- **工具类**: 100% 单元测试覆盖
- **组件**: 核心功能测试 + 快照测试
- **边界条件**: 必须覆盖 null/undefined/empty 情况

---

## 提交流程

### 分支命名

- `feature/xxx` - 新功能
- `fix/xxx` - Bug 修复
- `docs/xxx` - 文档更新

### Commit 规范

```
feat(commons-utils): add date formatting utilities

新增日期格式化工具方法。

Refs: #789
```

### Pull Request 检查清单

- [ ] 无业务逻辑
- [ ] 100% 单元测试覆盖
- [ ] API 向后兼容
- [ ] 完整的 JSDoc/Javadoc

---

## 联系方式

- **Issue 追踪**: GitHub Issues
- **技术讨论**: GitHub Discussions
- **安全问题**: security@brix.dev

---

感谢您的贡献！
