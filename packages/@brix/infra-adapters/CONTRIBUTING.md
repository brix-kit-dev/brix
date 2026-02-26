# Contributing to Brix Platform - Infra Adapters

> **版本**: v3.2.0  
> **最后更新**: 2026-02-13

感谢您对 Brix Platform Infra Adapters 的贡献！本文档提供了参与项目开发所需的指南。

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
- **Node.js**: >= 18.0.0 (Web 适配器)
- **pnpm**: >= 8.0.0 (Web 适配器)

### 快速开始

```bash
# 1. 克隆仓库
git clone https://github.com/brix-platform/infra-adapters.git
cd infra-adapters

# 2. 安装 Java 依赖并构建
mvn clean install

# 3. 安装 Web 依赖
pnpm install

# 4. 运行测试
mvn test
pnpm test

# 5. 运行 lint 检查
pnpm lint
```

### 项目结构

```
infra-adapters/
├── packages/
│   ├── mobile/                     # 移动端适配器
│   │   ├── infra-adapter-device-mobile/
│   │   └── infra-adapter-module-mobile/
│   ├── server/                     # 服务端适配器
│   │   ├── infra-adapter-cache-redis/
│   │   ├── infra-adapter-eventbus-kafka/
│   │   └── infra-adapter-lock-redis/
│   └── web/                        # Web 端适配器
│       ├── infra-adapter-http-web/
│       ├── infra-adapter-mf-web/
│       ├── infra-adapter-native-web/
│       ├── infra-adapter-router-web/
│       └── infra-adapter-state-web/
└── pom.xml
```

---

## 架构指南

### 适配器职责

infra-adapters 是**能力实现层 (Layer 2.5)**，负责：

- 实现 runtime-sdk-api 定义的能力接口
- 封装具体技术实现（Redis、Kafka、Fetch 等）
- 提供可替换的基础设施实现

### 极薄Host架构原则 (Extreme Thin Host)

```
┌─────────────────────────────────────────────────────────────────┐
│  Host层 (Layer 3) - 纯组装，无实现                              │
│  • @Import / @Bean 组装                                         │
│  • 不包含任何实现代码                                           │
└─────────────────────────────────────────────────────────────────┘
                              │ 导入
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  infra-adapters (Layer 2.5) - 能力实现                          │  ← 本仓库
│  • 实现 HttpCapability, EventBusCapability 等                   │
│  • 封装 Redis, Kafka, Fetch 等技术细节                          │
└─────────────────────────────────────────────────────────────────┘
                              │ implements
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│  runtime-sdk-api (Layer 2) - 纯契约                             │
│  • 只定义接口，不包含实现                                        │
└─────────────────────────────────────────────────────────────────┘
```

### 架构红线 (Architecture Red Lines)

| 红线编号 | 规则 | 说明 |
|---------|------|------|
| **R1** | 禁止循环依赖 | 适配器间不能相互依赖 |
| **R2** | 禁止上层依赖 | 不能依赖 Host 层或插件层 |
| **R3** | 实现必须可替换 | 通过条件装配支持替换 |
| **R5** | 契约定义在上层 | 适配器不能定义新契约接口 |

### 适配器注册模式

```java
// ✅ 正确：使用条件装配
@Configuration
@ConditionalOnClass(RedisTemplate.class)
@ConditionalOnProperty(name = "brix.cache.type", havingValue = "redis")
public class RedisCacheAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean(CacheCapability.class)
    public CacheCapability cacheCapability(RedisTemplate<String, Object> redisTemplate) {
        return new RedisCacheCapability(redisTemplate);
    }
}
```

---

## 开发规范

### Java 适配器规范

```java
/**
 * Redis 缓存能力实现
 * 
 * 【架构说明】
 * 本类是 CacheCapability 的 Redis 实现，位于 Layer 2.5。
 * 通过条件装配注入到 Host 层，可被其他实现替换。
 * 
 * 【架构红线 R3 合规】
 * 本实现封装了 RedisTemplate，插件层通过 CacheCapability 接口访问，
 * 不直接依赖 Redis API。
 * 
 * @author Brix Team
 * @since 3.0.0
 * @see CacheCapability
 */
@Capability(contract = CacheCapability.class)
public class RedisCacheCapability implements CacheCapability {
    // 实现代码
}
```

### TypeScript 适配器规范

```typescript
/**
 * Fetch HTTP 能力实现
 * 
 * 【架构说明】
 * 本模块是 HttpCapability 的 Fetch API 实现。
 * 封装了原生 fetch，提供统一的 HTTP 调用接口。
 * 
 * 【架构红线 R3 合规】
 * 插件层通过 HttpCapability 接口发起请求，
 * 不直接使用 fetch/axios。
 * 
 * @module infra-adapter-http-web
 * @version 3.2.0
 */
export function createFetchHttpCapability(config: HttpConfig): HttpCapability {
    return {
        async get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
            // 实现
        },
        // ...
    };
}
```

### 命名规范

| 类型 | Java | TypeScript |
|------|------|------------|
| 适配器类 | `XxxCapabilityImpl` / `XxxAdapter` | `createXxxCapability` |
| 配置类 | `XxxAutoConfiguration` | `XxxConfig` |
| 模块名 | `infra-adapter-xxx-yyy` | `@brix/infra-adapter-xxx-yyy` |

---

## 提交流程

### 分支命名

- `feature/xxx` - 新适配器或功能
- `fix/xxx` - Bug 修复
- `refactor/xxx` - 重构

### Commit 规范

```
feat(infra-adapter-cache-redis): add TTL support

为 Redis 缓存适配器添加 TTL 过期时间支持。

Refs: #456
```

### Pull Request 流程

1. **创建 PR 前**
   ```bash
   # 运行架构测试
   mvn test -Dtest="*ArchitectureTest"
   
   # 运行 lint
   pnpm lint
   
   # 运行单元测试
   mvn test
   pnpm test
   ```

2. **架构合规检查清单**
   - [ ] 适配器实现 runtime-sdk-api 定义的接口
   - [ ] 使用条件装配 (@ConditionalOnXxx)
   - [ ] 不依赖 Host 层或插件层
   - [ ] 不定义新的契约接口（契约应在 runtime-sdk-api）

---

## 联系方式

- **Issue 追踪**: GitHub Issues
- **技术讨论**: GitHub Discussions
- **安全问题**: security@brix.dev

---

感谢您的贡献！
