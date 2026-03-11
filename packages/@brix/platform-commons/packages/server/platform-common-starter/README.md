# Platform Common Starter

> v2.1 平台公共 Starter - 仅供 Service 使用

## 概述

`platform-common-starter` 是 Brix 平台 v2.1 架构中专为 **Service（服务）** 设计的 Spring Boot Starter。

### v2.1 架构说明

| 类型 | 描述 | 依赖 | 运行方式 |
|------|------|------|----------|
| **Plugin（插件）** | 纯业务能力 JAR | platform-common | 被 Service 组装 |
| **Service（服务）** | 可独立运行的 Spring Boot 应用 | platform-common-starter | 独立部署 |

## 功能特性

### 🔌 服务注册
- 应用启动后自动向基座注册
- 支持重试机制（可配置重试次数和间隔）
- 应用关闭时自动注销

### 💓 心跳维护
- 定时向基座发送心跳（可配置间隔）
- 包含健康指标（CPU、内存、线程数、RPS、错误率）
- 连续失败后自动重新注册

### 🛤️ 路由扫描
- 自动扫描 `@RestController` 暴露的 REST 端点
- 支持配置扫描的基础包
- 支持排除特定路径模式
- 提取参数、返回类型、标签等元信息

### 🌐 CORS 跨域
- 开箱即用的 CORS 配置
- 支持常见的跨域场景

### 📝 全局异常处理
- 统一的异常处理机制
- 标准的 `ApiResponse` 格式响应
- 支持多种异常类型

### 📊 审计日志
- 自动记录 REST API 调用日志
- 包含请求 ID、用户 ID、IP、耗时等信息

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>shinwa.platform</groupId>
    <artifactId>platform-common-starter</artifactId>
    <version>${platform.version}</version>
</dependency>
```

### 2. 配置服务信息

```yaml
shinwa:
  service:
    # 服务名称（必需）
    name: shinwa-service-user
    
    # 基座网关地址（必需）
    base-url: http://localhost:8900
    
    # 心跳间隔（可选，默认 30s）
    heartbeat-interval: 30s
    
    # 是否启用注册（可选，默认 true）
    registration-enabled: true
    
    # 路由扫描配置
    route-scan:
      enabled: true
      base-packages:
        - io.brix.enterprise.app.plugin.user
        - io.brix.enterprise.app.plugin.auth
      exclude-patterns:
        - /internal/**
      include-actuator: false

  # CORS 配置（可选）
  cors:
    enabled: true
    
  # 审计日志（可选）
  audit:
    enabled: true
```

## 配置参考

### 服务配置 (shinwa.service.*)

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `name` | String | - | 服务名称（必需） |
| `base-url` | String | - | 基座网关地址（必需） |
| `heartbeat-interval` | Duration | 30s | 心跳间隔 |
| `registration-enabled` | boolean | true | 是否启用注册 |
| `registration-retry-count` | int | 3 | 注册重试次数 |
| `registration-retry-interval` | Duration | 5s | 注册重试间隔 |

### 路由扫描配置 (shinwa.service.route-scan.*)

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | true | 是否启用路由扫描 |
| `base-packages` | Set<String> | io.brix.enterprise.app.plugin | 扫描的基础包 |
| `exclude-patterns` | Set<String> | - | 排除的路径模式 |
| `include-actuator` | boolean | false | 是否包含 actuator 端点 |

## 注册 API

### 服务注册

```http
POST {baseUrl}/api/registry/services
Content-Type: application/json

{
  "serviceName": "shinwa-service-user",
  "instanceId": "shinwa-service-user-host1-9010-abc123",
  "serviceUrl": "http://192.168.1.100:9010",
  "version": "0.1.0-SNAPSHOT",
  "description": "用户服务",
  "routes": [
    {
      "path": "/api/v1/users",
      "methods": ["GET", "POST"],
      "controllerClass": "io.brix.enterprise.app.plugin.user.controller.UserController",
      "methodName": "listUsers",
      "parameters": [...],
      "responseType": "ApiResponse<List<User>>",
      "deprecated": false,
      "tags": ["user"],
      "description": "获取用户列表"
    }
  ],
  "plugins": [
    {
      "pluginId": "plugin-user-core",
      "name": "用户管理插件",
      "version": "0.1.0-SNAPSHOT",
      "type": "CORE",
      "description": "提供用户管理能力"
    }
  ],
  "metadata": {
    "profiles": ["dev"],
    "javaVersion": "17"
  },
  "registrationTime": "2024-01-01T00:00:00Z"
}
```

### 服务心跳

```http
POST {baseUrl}/api/registry/heartbeat
Content-Type: application/json

{
  "serviceName": "shinwa-service-user",
  "instanceId": "shinwa-service-user-host1-9010-abc123",
  "status": "RUNNING",
  "timestamp": "2024-01-01T00:00:30Z",
  "healthMetrics": {
    "cpuUsage": 12.5,
    "memoryUsage": 45.2,
    "activeThreads": 25,
    "requestsPerSecond": 100.5,
    "avgResponseTimeMs": 15.3,
    "errorRate": 0.1
  }
}
```

### 服务注销

```http
DELETE {baseUrl}/api/registry/services/{instanceId}
```

## 最佳实践

### 1. 服务命名规范

```
shinwa-service-{domain}
```

示例：
- `shinwa-service-user` - 用户服务
- `shinwa-service-contract` - 合同服务
- `shinwa-service-notification` - 通知服务

### 2. 插件包命名规范

```
io.brix.enterprise.app.plugin.{domain}
```

示例：
- `io.brix.enterprise.app.plugin.user` - 用户插件
- `io.brix.enterprise.app.plugin.contract` - 合同插件

### 3. 配置路由扫描

为了提高扫描效率，建议明确指定 `base-packages`：

```yaml
shinwa:
  service:
    route-scan:
      base-packages:
        - io.brix.enterprise.app.plugin.user
        - io.brix.enterprise.app.plugin.auth
```

## 依赖关系

```
platform-common-starter
├── platform-common (Entity/DTO/Exception)
├── spring-boot-starter-web
├── spring-boot-starter-actuator
├── spring-boot-starter-validation
├── spring-boot-starter-aop
├── spring-boot-starter-webflux (HTTP 客户端)
├── spring-boot-autoconfigure
└── lombok (optional)
```

## 许可证

Apache License 2.0 - See [LICENSE](../../LICENSE)

