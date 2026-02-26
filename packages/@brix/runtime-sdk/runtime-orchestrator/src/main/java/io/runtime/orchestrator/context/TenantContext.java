/*
 * Copyright 2026 Runtime SDK Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.runtime.orchestrator.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 租户上下文持有器
 * 
 * <p>基于 ThreadLocal 实现的租户上下文管理，在请求处理期间存储当前租户信息。
 * 这是多租户隔离架构的核心组件。</p>
 * 
 * <h2>核心用途</h2>
 * <ul>
 *   <li>请求处理期间保持租户身份</li>
 *   <li>数据库查询自动追加租户条件</li>
 *   <li>缓存 Key 自动添加租户前缀</li>
 *   <li>事件发布时自动携带租户信息</li>
 * </ul>
 * 
 * <h2>上下文传播流程</h2>
 * <pre>
 * 客户端请求
 *     │
 *     ▼  Header: X-Tenant-Id: tenant-001
 * ┌─────────────────┐
 * │  Gateway        │  TenantContextFilter 解析并验证租户ID
 * └────────┬────────┘
 *          │  TenantContext.set("tenant-001")
 *          ▼
 * ┌─────────────────┐
 * │  Module Service │  RuntimeContext.getTenantId()
 * └────────┬────────┘
 *          │  事件发布时自动携带 tenantId
 *          ▼
 * ┌─────────────────┐
 * │  EventBus       │  事件头部包含 tenantId，消费时自动恢复
 * └─────────────────┘
 * </pre>
 * 
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 在 Filter 中设置租户上下文
 * public void doFilter(ServletRequest request, ...) {
 *     String tenantId = httpRequest.getHeader("X-Tenant-Id");
 *     TenantContext.set(tenantId);
 *     try {
 *         chain.doFilter(request, response);
 *     } finally {
 *         TenantContext.clear();  // 重要！必须清理
 *     }
 * }
 * 
 * // 在业务代码中获取租户
 * String tenantId = TenantContext.get();
 * }</pre>
 * 
 * <h2>重要提醒</h2>
 * <p><b>必须</b>在请求结束时调用 {@link #clear()} 方法清理上下文，
 * 避免线程复用导致的租户信息泄露。</p>
 * 
 * <h2>架构归属</h2>
 * <p>本类属于 <b>编排层（Orchestrator）</b>，负责运行时多租户上下文管理。
 * 从 runtime-sdk-api 迁移至此，因为租户上下文管理属于运行时编排职责，
 * 而非基础契约定义。SDK API 层只定义纯 Capability 接口契约。</p>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public final class TenantContext {

    private static final Logger log = LoggerFactory.getLogger(TenantContext.class);

    /**
     * 请求头中的租户ID字段名
     */
    public static final String TENANT_HEADER = "X-Tenant-Id";

    /**
     * 默认租户ID（系统级操作使用）
     */
    public static final String DEFAULT_TENANT = "default";

    /**
     * 系统租户ID（超级管理员使用）
     */
    public static final String SYSTEM_TENANT = "system";

    /**
     * ThreadLocal 存储当前租户ID
     * 
     * <p>每个线程持有独立的租户ID副本，实现线程安全的租户上下文传播。
     * 在异步场景（如 CompletableFuture）中需要手动传播。</p>
     */
    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    /**
     * ThreadLocal 存储是否忽略租户过滤
     * 
     * <p>用于系统级跨租户操作，如定时任务统计、超级管理员查询等。
     * 默认值为 false，即默认启用租户过滤。</p>
     */
    private static final ThreadLocal<Boolean> IGNORE_TENANT = ThreadLocal.withInitial(() -> false);

    /**
     * 私有构造函数，工具类不允许实例化
     */
    private TenantContext() {
        throw new UnsupportedOperationException("工具类不允许实例化");
    }

    // ==================== 基础操作 ====================

    /**
     * 设置当前租户ID
     * 
     * <p>通常由 Filter 或 Interceptor 在请求入口处调用。</p>
     * 
     * @param tenantId 租户ID，不能为 null 或空
     * @throws IllegalArgumentException 如果 tenantId 为 null 或空
     */
    public static void set(String tenantId) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new IllegalArgumentException("租户ID不能为空");
        }
        CURRENT_TENANT.set(tenantId.trim());
        log.debug("设置租户上下文: {}", tenantId);
    }

    /**
     * 获取当前租户ID
     * 
     * @return 当前租户ID，未设置时返回 null
     */
    public static String get() {
        return CURRENT_TENANT.get();
    }

    /**
     * 获取当前租户ID，如果未设置返回默认值
     * 
     * @param defaultValue 默认值
     * @return 当前租户ID或默认值
     */
    public static String getOrDefault(String defaultValue) {
        String tenantId = CURRENT_TENANT.get();
        return tenantId != null ? tenantId : defaultValue;
    }

    /**
     * 获取当前租户ID，如果未设置则抛出异常
     * 
     * @return 当前租户ID
     * @throws TenantNotSetException 如果租户上下文未设置
     */
    public static String getRequired() {
        String tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new TenantNotSetException("租户上下文未设置");
        }
        return tenantId;
    }

    /**
     * 清除当前租户上下文
     * 
     * <p><b>重要</b>：必须在请求结束时调用，避免线程复用导致的数据泄露。
     * 推荐在 try-finally 中使用。</p>
     */
    public static void clear() {
        String tenantId = CURRENT_TENANT.get();
        CURRENT_TENANT.remove();
        IGNORE_TENANT.remove();
        if (tenantId != null) {
            log.debug("清除租户上下文: {}", tenantId);
        }
    }

    // ==================== 状态检查 ====================

    /**
     * 检查是否已设置租户上下文
     * 
     * @return 如果已设置返回 true
     */
    public static boolean isSet() {
        return CURRENT_TENANT.get() != null;
    }

    /**
     * 检查当前租户是否为默认租户
     * 
     * @return 如果是默认租户返回 true
     */
    public static boolean isDefaultTenant() {
        return DEFAULT_TENANT.equals(CURRENT_TENANT.get());
    }

    /**
     * 检查当前租户是否为系统租户
     * 
     * @return 如果是系统租户返回 true
     */
    public static boolean isSystemTenant() {
        return SYSTEM_TENANT.equals(CURRENT_TENANT.get());
    }

    // ==================== 租户过滤控制 ====================

    /**
     * 设置忽略租户过滤标志
     * 
     * <p>用于系统级操作需要查询所有租户数据的场景。
     * 设置后，数据访问层应跳过租户过滤条件。</p>
     * 
     * @param ignore 是否忽略租户过滤
     */
    public static void setIgnoreFilter(boolean ignore) {
        IGNORE_TENANT.set(ignore);
        log.debug("设置忽略租户过滤: {}", ignore);
    }

    /**
     * 检查是否应该忽略租户过滤
     * 
     * @return 如果应忽略返回 true
     */
    public static boolean shouldIgnoreFilter() {
        return Boolean.TRUE.equals(IGNORE_TENANT.get());
    }

    // ==================== 执行上下文 ====================

    /**
     * 在指定租户上下文中执行操作
     * 
     * <p>临时切换到指定租户，执行完成后自动恢复原租户上下文。
     * 适用于需要跨租户操作的场景，如数据迁移、批量处理等。</p>
     * 
     * <h4>使用示例</h4>
     * <pre>{@code
     * TenantContext.runAs("tenant-002", () -> {
     *     // 在 tenant-002 上下文中执行
     *     repository.findAll();  // 只查询 tenant-002 的数据
     * });
     * // 恢复原租户上下文
     * }</pre>
     * 
     * @param tenantId 临时租户ID
     * @param runnable 要执行的操作
     */
    public static void runAs(String tenantId, Runnable runnable) {
        String previous = CURRENT_TENANT.get();
        try {
            set(tenantId);
            runnable.run();
        } finally {
            if (previous != null) {
                CURRENT_TENANT.set(previous);
            } else {
                CURRENT_TENANT.remove();
            }
        }
    }

    /**
     * 在指定租户上下文中执行操作并返回结果
     * 
     * @param tenantId 临时租户ID
     * @param supplier 要执行的操作
     * @param <T> 返回值类型
     * @return 操作结果
     */
    public static <T> T callAs(String tenantId, java.util.function.Supplier<T> supplier) {
        String previous = CURRENT_TENANT.get();
        try {
            set(tenantId);
            return supplier.get();
        } finally {
            if (previous != null) {
                CURRENT_TENANT.set(previous);
            } else {
                CURRENT_TENANT.remove();
            }
        }
    }

    /**
     * 在忽略租户过滤的情况下执行操作
     * 
     * <p>用于需要访问所有租户数据的系统级操作。
     * 执行完毕后自动恢复之前的过滤状态。</p>
     * 
     * @param runnable 要执行的操作
     */
    public static void runWithoutFilter(Runnable runnable) {
        Boolean previous = IGNORE_TENANT.get();
        try {
            IGNORE_TENANT.set(true);
            runnable.run();
        } finally {
            IGNORE_TENANT.set(previous);
        }
    }

    /**
     * 租户上下文未设置异常
     * 
     * <p>当业务代码要求租户上下文但未设置时抛出。
     * 通常意味着请求链路中缺少 TenantContextFilter 或 tenantId 未传播。</p>
     */
    public static class TenantNotSetException extends RuntimeException {
        public TenantNotSetException(String message) {
            super(message);
        }
    }
}
