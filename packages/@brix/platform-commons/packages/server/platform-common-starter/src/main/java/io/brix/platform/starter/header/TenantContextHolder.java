package io.brix.platform.starter.header;

/**
 * 租户上下文持有
 * 
 * <p>使用 ThreadLocal 在请求链路中传递租户ID
 * 确保在同一个请求线程中可以随时获取当前租户信息</p>
 * 
 * <p>设计目的</p>
 * <ul>
 *   <li>解决问题6：X-Tenant-Id 请求头经常遗</li>
 *   <li>通过拦截器自动提取请求头中的租户 ID 并存入上下文</li>
 *   <li>在服务间调用时自动传递租户ID</li>
 * </ul>
 * 
 * <p>使用流程</p>
 * <ol>
 *   <li>TenantHeaderFilter 在请求入站时提取 X-Tenant-Id 并调setTenantId()</li>
 *   <li>业务代码通过 getTenantId() 获取当前租户</li>
 *   <li>PlatformHeadersInterceptor 在出站请求时自动添加 X-Tenant-Id</li>
 *   <li>TenantHeaderFilter 在请求结束时调用 clear() 清理上下</li>
 * </ol>
 * 
 * <p>使用示例</p>
 * <pre>
 * // 获取当前租户 ID
 * String tenantId = TenantContextHolder.getTenantId();
 * 
 * // 手动设置租户 ID（通常Filter 自动处理
 * TenantContextHolder.setTenantId("tenant-123");
 * 
 * // 清理上下文（通常Filter 自动处理
 * TenantContextHolder.clear();
 * </pre>
 * 
 * <p>线程安全说明</p>
 * <ul>
 *   <li>每个线程有独立的租户上下</li>
 *   <li>异步场景需要手动传递上下文或使用上下文传播工具</li>
 * </ul>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 * @see TenantHeaderFilter
 * @see PlatformHeadersInterceptor
 */
public final class TenantContextHolder {
    
    /**
     * 线程本地变量 - 存储当前线程的租户ID
     * 
     * <p>使用 ThreadLocal 保证线程隔离</p>
     */
    private static final ThreadLocal<String> TENANT_ID_HOLDER = new ThreadLocal<>();
    
    /**
     * 线程本地变量 - 存储当前线程的用户ID
     */
    private static final ThreadLocal<String> USER_ID_HOLDER = new ThreadLocal<>();
    
    /**
     * 线程本地变量 - 存储当前线程的追ID
     */
    private static final ThreadLocal<String> TRACE_ID_HOLDER = new ThreadLocal<>();
    
    /**
     * 私有构造函数，防止实例
     */
    private TenantContextHolder() {
        throw new UnsupportedOperationException("工具类不允许实例");
    }
    
    // ==================== 绉熸埛 ID ====================
    
    /**
     * 设置当前线程的租户ID
     * 
     * <p>通常TenantHeaderFilter 在请求入站时调用</p>
     * 
     * @param tenantId 租户 ID，不能为 null
     * @throws IllegalArgumentException 如果 tenantId null 或空字符
     */
    public static void setTenantId(String tenantId) {
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new IllegalArgumentException("租户 ID 不能为空");
        }
        TENANT_ID_HOLDER.set(tenantId.trim());
    }
    
    /**
     * 获取当前线程的租户ID
     * 
     * <p>如果未设置，返回默认租户 ID</p>
     * 
     * @return 当前租户 ID，永不返回 null
     */
    public static String getTenantId() {
        String tenantId = TENANT_ID_HOLDER.get();
        return tenantId != null ? tenantId : PlatformHeaders.DEFAULT_TENANT_ID;
    }
    
    /**
     * 获取当前线程的租户ID（可空）
     * 
     * <p>不使用默认值，如果未设置则返回 null</p>
     * 
     * @return 当前租户 ID，可能为 null
     */
    public static String getTenantIdNullable() {
        return TENANT_ID_HOLDER.get();
    }
    
    /**
     * 判断当前线程是否设置了租户ID
     * 
     * @return 如果已设置租户ID 返回 true
     */
    public static boolean hasTenantId() {
        return TENANT_ID_HOLDER.get() != null;
    }
    
    // ==================== 用户 ID ====================
    
    /**
     * 设置当前线程的用户ID
     * 
     * @param userId 用户 ID
     */
    public static void setUserId(String userId) {
        if (userId != null && !userId.trim().isEmpty()) {
            USER_ID_HOLDER.set(userId.trim());
        }
    }
    
    /**
     * 获取当前线程的用户ID
     * 
     * @return 用户 ID，可能为 null
     */
    public static String getUserId() {
        return USER_ID_HOLDER.get();
    }
    
    /**
     * 判断当前线程是否设置了用户ID
     * 
     * @return 如果已设置用户ID 返回 true
     */
    public static boolean hasUserId() {
        return USER_ID_HOLDER.get() != null;
    }
    
    // ==================== 杩借釜 ID ====================
    
    /**
     * 设置当前线程的追ID
     * 
     * @param traceId 杩借釜 ID
     */
    public static void setTraceId(String traceId) {
        if (traceId != null && !traceId.trim().isEmpty()) {
            TRACE_ID_HOLDER.set(traceId.trim());
        }
    }
    
    /**
     * 获取当前线程的追ID
     * 
     * @return 追踪 ID，可能为 null
     */
    public static String getTraceId() {
        return TRACE_ID_HOLDER.get();
    }
    
    // ==================== 上下文管====================
    
    /**
     * 清理当前线程的所有上下文
     * 
     * <p>必须在请求处理完成后调用，防止内存泄</p>
     * <p>通常TenantHeaderFilter finally 块中调用</p>
     */
    public static void clear() {
        TENANT_ID_HOLDER.remove();
        USER_ID_HOLDER.remove();
        TRACE_ID_HOLDER.remove();
    }
    
    /**
     * 获取当前上下文的快照
     * 
     * <p>用于异步场景传递上下文</p>
     * 
     * @return 上下文快
     */
    public static ContextSnapshot snapshot() {
        return new ContextSnapshot(
            TENANT_ID_HOLDER.get(),
            USER_ID_HOLDER.get(),
            TRACE_ID_HOLDER.get()
        );
    }
    
    /**
     * 从快照恢复上下文
     * 
     * <p>用于异步场景恢复上下</p>
     * 
     * @param snapshot 上下文快
     */
    public static void restore(ContextSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        if (snapshot.tenantId() != null) {
            TENANT_ID_HOLDER.set(snapshot.tenantId());
        }
        if (snapshot.userId() != null) {
            USER_ID_HOLDER.set(snapshot.userId());
        }
        if (snapshot.traceId() != null) {
            TRACE_ID_HOLDER.set(snapshot.traceId());
        }
    }
    
    /**
     * 上下文快
     * 
     * <p>用于在异步场景中传递租户上下文</p>
     * 
     * @param tenantId 绉熸埛 ID
     * @param userId   用户 ID
     * @param traceId  杩借釜 ID
     */
    public record ContextSnapshot(
        String tenantId,
        String userId,
        String traceId
    ) {}
}
