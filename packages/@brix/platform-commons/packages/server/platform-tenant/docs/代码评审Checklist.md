# Brix Platform 多租户代码评审 Checklist

> **版本**: 1.0  
> **日期**: 2026-03-18  
> **作者**: Brix Platform Team

---

## 概述

本文档提供多租户相关代码评审的标准化检查清单。所有涉及数据库、实体、查询的代码变更都必须通过此清单评审。

---

## 1. 数据库变更检查

### 1.1 新增表检查

| 检查项 | 是否通过 | 备注 |
|--------|----------|------|
| ☐ 业务表是否包含 `tenant_id` 字段 | | |
| ☐ `tenant_id` 字段是否定义为 `VARCHAR(64) NOT NULL` | | |
| ☐ 是否创建了 `tenant_id` 索引（单独或复合） | | |
| ☐ 表名是否遵循命名规范（biz_*, sys_*, etc.） | | |

**详细要求**：

```sql
-- ✅ 正确的表定义
CREATE TABLE biz_order (
    id BIGINT PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    order_number VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- 索引
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_tenant_order_number (tenant_id, order_number)
);

-- ❌ 错误：缺少 tenant_id
CREATE TABLE biz_order (
    id BIGINT PRIMARY KEY,
    order_number VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL
);
```

### 1.2 唯一约束检查

| 检查项 | 是否通过 | 备注 |
|--------|----------|------|
| ☐ 所有唯一约束是否包含 `tenant_id` | | |
| ☐ 复合唯一约束中 `tenant_id` 是否为首列 | | |
| ☐ 约束名是否符合 `uk_<table>_<columns>` 规范 | | |

**示例代码**：

```java
// ❌ 错误：唯一约束缺少 tenant_id
@Table(uniqueConstraints = @UniqueConstraint(
    name = "uk_product_code",
    columnNames = {"code"}
))
public class Product { }

// ✅ 正确：唯一约束包含 tenant_id
@Table(uniqueConstraints = @UniqueConstraint(
    name = "uk_product_tenant_code",
    columnNames = {"tenant_id", "code"}
))
public class Product { }
```

### 1.3 外键约束检查

| 检查项 | 是否通过 | 备注 |
|--------|----------|------|
| ☐ 外键引用是否在同一租户内 | | |
| ☐ 是否通过应用层验证租户一致性 | | |
| ☐ 跨租户引用是否有明确的业务理由 | | |

---

## 2. 实体类检查

### 2.1 继承关系检查

| 检查项 | 是否通过 | 备注 |
|--------|----------|------|
| ☐ 业务实体是否继承 `TenantOwnedEntity` | | |
| ☐ 系统实体是否继承 `BaseEntity`（无租户） | | |
| ☐ 实体是否正确标注 `@TenantIsolated` | | |

**示例**：

```java
// ✅ 业务实体
@Entity
@Table(name = "biz_customer")
@TenantIsolated
public class Customer extends TenantOwnedEntity {
    // ...
}

// ✅ 系统实体（无需租户隔离）
@Entity
@Table(name = "sys_tenant")
public class Tenant extends BaseEntity {
    // 租户表本身不需要 tenant_id
}
```

### 2.2 字段映射检查

| 检查项 | 是否通过 | 备注 |
|--------|----------|------|
| ☐ `tenant_id` 字段映射是否在基类中定义 | | |
| ☐ 关联实体是否使用正确的 `@ManyToOne` / `@OneToMany` | | |
| ☐ 级联操作是否适当（避免跨租户级联） | | |

---

## 3. Repository / DAO 检查

### 3.1 查询方法检查

| 检查项 | 是否通过 | 备注 |
|--------|----------|------|
| ☐ Spring Data 方法是否依赖 `TenantInterceptor` 自动过滤 | | |
| ☐ `@Query` 原生 SQL 是否手动添加 `tenant_id` 条件 | | |
| ☐ Criteria 查询是否包含租户条件 | | |

**示例检查**：

```java
// ✅ JPQL 查询（自动过滤）
@Query("SELECT o FROM Order o WHERE o.status = :status")
List<Order> findByStatus(@Param("status") String status);

// ❌ 原生 SQL（需要手动添加 tenant_id）
@Query(value = "SELECT * FROM biz_order WHERE status = ?", nativeQuery = true)
List<Order> findByStatusNative(String status);

// ✅ 原生 SQL（已添加 tenant_id）
@Query(value = "SELECT * FROM biz_order WHERE tenant_id = ?1 AND status = ?2", 
       nativeQuery = true)
List<Order> findByTenantAndStatusNative(String tenantId, String status);
```

### 3.2 批量操作检查

| 检查项 | 是否通过 | 备注 |
|--------|----------|------|
| ☐ 批量 UPDATE 是否包含 `tenant_id` 条件 | | |
| ☐ 批量 DELETE 是否包含 `tenant_id` 条件 | | |
| ☐ 原生批量操作是否经过 DBA 审核 | | |

---

## 4. Service 层检查

### 4.1 引用验证检查

| 检查项 | 是否通过 | 备注 |
|--------|----------|------|
| ☐ 跨实体引用是否使用 `TenantReferenceValidator` 验证 | | |
| ☐ 验证失败是否抛出明确的异常信息 | | |
| ☐ 批量引用是否使用集合验证方法 | | |

**示例**：

```java
// ✅ 正确的引用验证
public Order createOrder(Long customerId) {
    Customer customer = customerRepository.findById(customerId)
        .orElseThrow(() -> new EntityNotFoundException("Customer not found"));
    
    // 验证客户属于当前租户
    validator.validateReferenceFromContext(
        customer.getTenantId(),
        "Order", null,
        "Customer", customerId,
        "customerId"
    );
    
    // ... 创建订单
}

// ❌ 缺少验证
public Order createOrderWrong(Long customerId) {
    Customer customer = customerRepository.findById(customerId).orElseThrow();
    // ⚠️ 未验证租户一致性，可能引用其他租户的客户！
    Order order = new Order();
    order.setCustomerId(customerId);
    return orderRepository.save(order);
}
```

### 4.2 异步任务检查

| 检查项 | 是否通过 | 备注 |
|--------|----------|------|
| ☐ 异步任务是否使用 `TenantContext.wrap()` | | |
| ☐ `@Async` 方法是否配置租户上下文传播 | | |
| ☐ CompletableFuture 链是否正确传播上下文 | | |

**示例**：

```java
// ✅ 正确的异步处理
executor.submit(TenantContext.wrap(() -> {
    // 租户上下文已传播
    processData();
}));

// ❌ 错误：上下文丢失
executor.submit(() -> {
    // ⚠️ TenantContext 为空！
    processData();
});
```

### 4.3 跨租户访问检查

| 检查项 | 是否通过 | 备注 |
|--------|----------|------|
| ☐ 跨租户方法是否标注 `@CrossTenantAccess` | | |
| ☐ `reason` 参数是否填写有效业务原因 | | |
| ☐ 是否进入/退出跨租户作用域 | | |
| ☐ 跨租户访问是否有审计日志 | | |

---

## 5. 枚举变更检查

### 5.1 新增枚举值

| 检查项 | 是否通过 | 备注 |
|--------|----------|------|
| ☐ 是否为不可变位置添加（避免破坏序列化） | | |
| ☐ 是否更新数据库 CHECK 约束（如有） | | |
| ☐ 是否配套数据库迁移脚本 | | |

### 5.2 修改/删除枚举值

| 检查项 | 是否通过 | 备注 |
|--------|----------|------|
| ☐ 是否先查询受影响的数据量 | | |
| ☐ 是否提供数据迁移方案 | | |
| ☐ 是否考虑历史数据的反序列化 | | |
| ☐ 是否通知所有下游系统 | | |

---

## 6. 安全性检查

### 6.1 SQL 注入防护

| 检查项 | 是否通过 | 备注 |
|--------|----------|------|
| ☐ 是否使用参数化查询 | | |
| ☐ 动态 SQL 是否经过转义处理 | | |
| ☐ 排序字段是否来自白名单 | | |

### 6.2 租户 ID 来源

| 检查项 | 是否通过 | 备注 |
|--------|----------|------|
| ☐ 租户 ID 是否源自认证 Token 而非请求参数 | | |
| ☐ 是否防止租户 ID 被篡改 | | |
| ☐ 管理员切换租户是否有审计记录 | | |

---

## 7. 测试要求检查

### 7.1 单元测试

| 检查项 | 是否通过 | 备注 |
|--------|----------|------|
| ☐ 是否覆盖租户隔离的正向场景 | | |
| ☐ 是否覆盖跨租户访问的异常场景 | | |
| ☐ 测试用例是否清理 `TenantContext` | | |

### 7.2 集成测试

| 检查项 | 是否通过 | 备注 |
|--------|----------|------|
| ☐ 是否使用多租户验证数据隔离 | | |
| ☐ 是否测试并发场景下的上下文传播 | | |

---

## 8. 快速评审指南

### 简化版 Checklist（用于日常评审）

```markdown
## 快速检查（每个 PR 必查）

### 数据库
- [ ] 新表有 tenant_id 字段且非空
- [ ] 唯一约束包含 tenant_id

### 代码
- [ ] 业务实体继承 TenantOwnedEntity
- [ ] 原生 SQL 有 tenant_id 条件
- [ ] 引用关系有租户验证
- [ ] 异步任务有上下文传播

### 安全
- [ ] 无硬编码租户 ID
- [ ] 无绕过拦截器的操作
```

---

## 9. 评审结论模板

```markdown
## 多租户评审结论

**PR**: #xxx
**评审人**: xxx
**日期**: xxxx-xx-xx

### 检查结果

| 检查类别 | 通过 | 未通过 | 不适用 | 备注 |
|----------|------|--------|--------|------|
| 数据库变更 | X | | | |
| 实体类 | | X | | 需补充 TenantIsolated 注解 |
| Repository | X | | | |
| Service 层 | X | | | |
| 测试覆盖 | | X | | 缺少跨租户异常测试 |

### 阻塞问题
1. [问题描述]

### 建议改进
1. [改进建议]

### 结论
- [ ] 通过
- [x] 需修改后复审
- [ ] 驳回
```

---

## 10. 常见问题记录

| 序号 | 问题描述 | 风险等级 | 解决方案 |
|------|---------|---------|---------|
| 1 | 唯一约束缺少 tenant_id | 高 | 添加复合唯一约束 |
| 2 | 原生 SQL 缺少 tenant_id | 高 | 手动添加 WHERE 条件 |
| 3 | 异步任务上下文丢失 | 高 | 使用 TenantContext.wrap() |
| 4 | 跨实体引用未验证 | 中 | 使用 TenantReferenceValidator |
| 5 | 枚举变更未考虑历史数据 | 中 | 提供数据迁移脚本 |
| 6 | 测试未清理 TenantContext | 低 | @AfterEach 中调用 clear() |

---

## 相关文档

- [多租户开发规范.md](./多租户开发规范.md)
- [v1.0-多租户架构设计方案.md](../../../../../../../../docs/v1.0-多租户架构设计方案.md)
- [v0.1-多租户MVP实施清单.md](../../../../../../../../docs/v0.1-多租户MVP实施清单.md)
