package io.brix.platform.common.tenant;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * 租户上下文（统一版本
 * 
 * <p>v2.1 多租户支持的核心组件，使用 ThreadLocal 存储当前请求的租户信息
 * 
 * <p>设计说明
 * <ul>
 *   <li>租户 ID TenantFilter HTTP Header 中提取并设置</li>
 *   <li>所有数据库操作自动应用租户过滤条件</li>
 *   <li>请求结束时必须调用 clear() 方法清理上下</li>
 * </ul>
 * 
 * <p>使用示例
 * <pre>{@code
 * // 获取当前租户 ID
 * String tenantId = TenantContext.getTenantId()
 *     .orElseThrow(() -> new TenantNotFoundException("租户信息缺失"));
 * 
 * // 在新实体中设置租户ID
 * entity.setTenantId(TenantContext.requireTenantId());
 * }</pre>
 * 
 * <p>注意事项
 * <ul>
 *   <li>异步线程需要手动传递租户ID</li>
 *   <li>定时任务需要明确指定租户或使用系统租户</li>
 *   <li>跨服务调用需要在 Header 中传递租户ID</li>
 * </ul>
 * 
 * @author Brix Platform Authors Platform Team
 * @since 1.0.0
 */
public final class TenantContext {

    /**
     * 默认租户 ID
     * 用于未指定租户的场景（如系统初始化、定时任务等
     */
    public static final String DEFAULT_TENANT_ID = "default";

    /**
     * 系统租户 ID
     * 用于平台级操作，不受租户隔离约束
     */
    public static final String SYSTEM_TENANT_ID = "system";

    /**
     * 租户 ID HTTP Header 名称
     */
    public static final String TENANT_HEADER = "X-Tenant-ID";

    /**
     * 用户 ID HTTP Header 名称
     */
    public static final String USER_HEADER = "X-User-ID";

    /**
     * ThreadLocal 存储租户 ID
     */
    private static final ThreadLocal<String> TENANT_ID_HOLDER = new ThreadLocal<>();

    /**
     * ThreadLocal 存储用户 ID
     */
    private static final ThreadLocal<String> USER_ID_HOLDER = new ThreadLocal<>();

    /**
     * ThreadLocal 存储租户附加信息（可选）
     */
    private static final ThreadLocal<TenantInfo> TENANT_INFO_HOLDER = new ThreadLocal<>();

    /**
     * 私有构造函数，防止实例
     */
    private TenantContext() {
        throw new UnsupportedOperationException("TenantContext 是工具类，不可实例化");
    }

    // =====================================================
    // 租户 ID 操作
    // =====================================================

    /**
     * 设置当前租户 ID
     * 
     * @param tenantId 租户 ID，不能为
     * @throws IllegalArgumentException 如果 tenantId 为空
     */
    public static void setTenantId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("租户 ID 不能为空");
        }
        TENANT_ID_HOLDER.set(tenantId);
    }

    /**
     * 获取当前租户 ID
     * 
     * @return 当前租户 ID，如果未设置则返Optional.empty()
     */
    public static Optional<String> getTenantId() {
        return Optional.ofNullable(TENANT_ID_HOLDER.get());
    }

    /**
     * 获取当前租户 ID（必须存在）
     * 
     * @return 当前租户 ID
     * @throws IllegalStateException 如果租户 ID 未设
     */
    public static String requireTenantId() {
        return getTenantId()
            .orElseThrow(() -> new IllegalStateException("租户上下文未初始化，请确保请求通过 TenantFilter"));
    }

    /**
     * 获取当前租户 ID，如果未设置则返回默认
     * 
     * @return 当前租户 ID 或默认租户ID
     */
    public static String getTenantIdOrDefault() {
        return getTenantId().orElse(DEFAULT_TENANT_ID);
    }

    /**
     * 检查当前是否有租户上下
     * 
     * @return 如果已设置租户ID 则返回 true
     */
    public static boolean hasTenant() {
        return TENANT_ID_HOLDER.get() != null;
    }

    /**
     * 检查当前是否为系统租户
     * 
     * @return 如果当前租户是系统租户则返回 true
     */
    public static boolean isSystemTenant() {
        return SYSTEM_TENANT_ID.equals(TENANT_ID_HOLDER.get());
    }

    /**
     * 获取当前租户 ID（便捷方法）
     * 
     * @return 当前租户 ID
     * @throws IllegalStateException 如果租户 ID 未设
     */
    public static String getCurrentTenantId() {
        return requireTenantId();
    }

    // =====================================================
    // 用户 ID 操作
    // =====================================================

    /**
     * 设置当前用户 ID
     * 
     * @param userId 用户 ID，不能为
     * @throws IllegalArgumentException 如果 userId 为空
     */
    public static void setUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }
        USER_ID_HOLDER.set(userId);
    }

    /**
     * 获取当前用户 ID
     * 
     * @return 当前用户 ID，如果未设置则返Optional.empty()
     */
    public static Optional<String> getUserId() {
        return Optional.ofNullable(USER_ID_HOLDER.get());
    }

    /**
     * 获取当前用户 ID（必须存在）
     * 
     * @return 当前用户 ID
     * @throws IllegalStateException 如果用户 ID 未设
     */
    public static String requireUserId() {
        return getUserId()
            .orElseThrow(() -> new IllegalStateException("用户上下文未初始化，请确保请求通过认证过滤"));
    }

    /**
     * 获取当前用户 ID（便捷方法）
     * 
     * @return 当前用户 ID
     * @throws IllegalStateException 如果用户 ID 未设
     */
    public static String getCurrentUserId() {
        return requireUserId();
    }

    /**
     * 检查当前是否有用户上下
     * 
     * @return 如果已设置用户ID 则返回 true
     */
    public static boolean hasUser() {
        return USER_ID_HOLDER.get() != null;
    }

    // =====================================================
    // 租户信息操作
    // =====================================================

    /**
     * 设置租户附加信息
     * 
     * @param info 租户附加信息
     */
    public static void setTenantInfo(TenantInfo info) {
        TENANT_INFO_HOLDER.set(info);
    }

    /**
     * 获取租户附加信息
     * 
     * @return 租户附加信息
     */
    public static Optional<TenantInfo> getTenantInfo() {
        return Optional.ofNullable(TENANT_INFO_HOLDER.get());
    }

    // =====================================================
    // 清理操作
    // =====================================================

    /**
     * 清除当前线程的租户上下文
     * 
     * <p>必须在请求结束时调用，避免线程复用时的租户信息泄漏
     */
    public static void clear() {
        TENANT_ID_HOLDER.remove();
        USER_ID_HOLDER.remove();
        TENANT_INFO_HOLDER.remove();
    }

    // =====================================================
    // 执行上下文切
    // =====================================================

    /**
     * 在指定租户上下文中执行操
     * 
     * @param tenantId 目标租户 ID
     * @param runnable 要执行的操作
     */
    public static void runWithTenant(String tenantId, Runnable runnable) {
        String previousTenantId = TENANT_ID_HOLDER.get();
        try {
            setTenantId(tenantId);
            runnable.run();
        } finally {
            if (previousTenantId != null) {
                TENANT_ID_HOLDER.set(previousTenantId);
            } else {
                TENANT_ID_HOLDER.remove();
            }
        }
    }

    /**
     * 在指定租户上下文中执行操作并返回结果
     * 
     * @param tenantId 目标租户 ID
     * @param supplier 要执行的操作
     * @param <T> 返回值类
     * @return 操作返回
     */
    public static <T> T runWithTenant(String tenantId, Supplier<T> supplier) {
        String previousTenantId = TENANT_ID_HOLDER.get();
        try {
            setTenantId(tenantId);
            return supplier.get();
        } finally {
            if (previousTenantId != null) {
                TENANT_ID_HOLDER.set(previousTenantId);
            } else {
                TENANT_ID_HOLDER.remove();
            }
        }
    }

    /**
     * 在系统租户上下文中执行操
     * 
     * @param runnable 要执行的操作
     */
    public static void runAsSystem(Runnable runnable) {
        runWithTenant(SYSTEM_TENANT_ID, runnable);
    }

    /**
     * 在系统租户上下文中执行操作并返回结果
     * 
     * @param supplier 要执行的操作
     * @param <T> 返回值类
     * @return 操作返回
     */
    public static <T> T runAsSystem(Supplier<T> supplier) {
        return runWithTenant(SYSTEM_TENANT_ID, supplier);
    }
}
