package io.brix.platform.auth.context;

import java.util.Optional;

/**
 * 安全上下文持有
 * <p>
 * 使用 ThreadLocal 存储当前线程的认证用户信息
 * 配合 SecurityContextFilter 在请求开始时设置，结束时清理
 * </p>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 */
public class SecurityContextHolder {

    private static final ThreadLocal<AuthenticatedUser> CONTEXT = new ThreadLocal<>();

    /**
     * 设置当前用户
     *
     * @param user 认证用户
     */
    public void setCurrentUser(AuthenticatedUser user) {
        CONTEXT.set(user);
    }

    /**
     * 获取当前用户
     *
     * @return 认证用户，可能为
     */
    public Optional<AuthenticatedUser> getCurrentUser() {
        return Optional.ofNullable(CONTEXT.get());
    }

    /**
     * 获取当前用户，不存在则抛异常
     *
     * @return 认证用户
     * @throws SecurityException 用户未认
     */
    public AuthenticatedUser requireCurrentUser() {
        return getCurrentUser()
                .orElseThrow(() -> new SecurityException("User not authenticated"));
    }

    /**
     * 获取当前用户 ID
     *
     * @return 用户 ID，可能为
     */
    public Optional<String> getCurrentUserId() {
        return getCurrentUser().map(AuthenticatedUser::getUserId);
    }

    /**
     * 获取当前租户 ID
     *
     * @return 租户 ID，可能为
     */
    public Optional<String> getCurrentTenantId() {
        return getCurrentUser().map(AuthenticatedUser::getTenantId);
    }

    /**
     * 检查当前用户是否已认证
     *
     * @return 是否已认
     */
    public boolean isAuthenticated() {
        return CONTEXT.get() != null;
    }

    /**
     * 检查当前用户是否拥有指定权
     *
     * @param permission 权限标识
     * @return 是否拥有权限
     */
    public boolean hasPermission(String permission) {
        AuthenticatedUser user = CONTEXT.get();
        return user != null && user.hasPermission(permission);
    }

    /**
     * 检查当前用户是否拥有指定角
     *
     * @param role 角色名称
     * @return 是否拥有角色
     */
    public boolean hasRole(String role) {
        AuthenticatedUser user = CONTEXT.get();
        return user != null && user.hasRole(role);
    }

    /**
     * 清除当前上下
     * <p>
     * 必须在请求结束时调用，防止内存泄漏
     * </p>
     */
    public void clear() {
        CONTEXT.remove();
    }

    /**
     * 静态方法：获取当前上下文（兼容旧用法）
     *
     * @return 认证用户，可能为 null
     */
    public static AuthenticatedUser getContext() {
        return CONTEXT.get();
    }

    /**
     * 静态方法：设置当前上下文（兼容旧用法）
     *
     * @param user 认证用户
     */
    public static void setContext(AuthenticatedUser user) {
        CONTEXT.set(user);
    }

    /**
     * 静态方法：清除上下文（兼容旧用法）
     */
    public static void clearContext() {
        CONTEXT.remove();
    }
}
