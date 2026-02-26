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
package io.runtime.sdk.capability;

import java.security.Principal;
import java.util.Set;

/**
 * 认证上下文能力契约
 * 
 * <p>提供当前请求的身份认证和权限信息，是安全能力的核心抽象。
 * 模块通过此接口获取用户身份、检查权限，无需感知认证实现细节（JWT/OAuth/SAML）。</p>
 * 
 * <h3>命名说明（v3.2.0）</h3>
 * <p>为统一前后端能力命名，新增 {@link AuthCapability} 作为标准名称。
 * 建议新代码使用 {@code AuthCapability}，此接口保留用于向后兼容。</p>
 * 
 * <h3>核心职责</h3>
 * <ul>
 *   <li>获取当前用户身份（Principal）</li>
 *   <li>权限检查（Permission）</li>
 *   <li>角色检查（Role）</li>
 *   <li>数据权限范围（DataScope）</li>
 * </ul>
 * 
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>上下文透明</b>：认证信息通过请求上下文自动传递</li>
 *   <li><b>实现无关</b>：不暴露 JWT Token 等实现细节</li>
 *   <li><b>多租户支持</b>：支持获取租户信息和数据权限</li>
 * </ul>
 * 
 * <h3>权限模型</h3>
 * <ul>
 *   <li><b>Permission（权限）</b>：细粒度操作权限，如 "booking:create"</li>
 *   <li><b>Role（角色）</b>：权限集合，如 "ADMIN", "OPERATOR"</li>
 *   <li><b>DataScope（数据范围）</b>：数据访问边界，如部门、地区</li>
 * </ul>
 * 
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Inject
 * private AuthContextCapability authContext;
 * 
 * public void createReservation(ReservationCommand command) {
 *     // 获取当前用户
 *     Principal user = authContext.getCurrentPrincipal();
 *     
 *     // 检查权限
 *     if (!authContext.hasPermission("booking:create")) {
 *         throw new AccessDeniedException("无预约创建权限");
 *     }
 *     
 *     // 获取数据权限范围
 *     Set<DataScope> scopes = authContext.getAuthorizedScopes();
 *     // 基于 scopes 过滤可访问的数据...
 * }
 * }</pre>
 * 
 * <h3>实现说明</h3>
 * <ul>
 *   <li>Full Product Host：JWT + 本地权限缓存</li>
 *   <li>Embedded Host：委托客户系统认证（Delegated Auth）</li>
 * </ul>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see AuthCapability 推荐使用此标准化名称
 * @see Principal
 * @see DataScope
 */
public interface AuthContextCapability {

    /**
     * 获取当前用户身份
     * 
     * <p>返回的 Principal 包含用户标识信息，可能包括：</p>
     * <ul>
     *   <li>用户 ID</li>
     *   <li>用户名</li>
     *   <li>租户 ID</li>
     * </ul>
     * 
     * @return 当前用户身份，如果未认证返回 null
     */
    Principal getCurrentPrincipal();

    /**
     * 检查是否拥有指定权限
     * 
     * <p>权限命名规范：{模块}:{操作}，如 "booking:create", "user:read"</p>
     * 
     * @param permission 权限标识，不能为空
     * @return 如果拥有权限返回 true，否则返回 false
     * @throws IllegalArgumentException 如果 permission 为 null 或空
     */
    boolean hasPermission(String permission);

    /**
     * 检查是否拥有指定角色
     * 
     * <p>角色通常为大写字母，如 "ADMIN", "OPERATOR", "USER"</p>
     * 
     * @param role 角色标识，不能为空
     * @return 如果拥有角色返回 true，否则返回 false
     * @throws IllegalArgumentException 如果 role 为 null 或空
     */
    boolean hasRole(String role);

    /**
     * 获取授权的数据范围
     * 
     * <p>数据范围用于行级数据权限控制，常见类型：</p>
     * <ul>
     *   <li>部门范围：用户只能访问所属部门的数据</li>
     *   <li>地区范围：用户只能访问指定地区的数据</li>
     *   <li>自定义范围：根据业务定义的数据边界</li>
     * </ul>
     * 
     * @return 授权的数据范围集合，不会返回 null
     */
    Set<DataScope> getAuthorizedScopes();

    /**
     * 检查是否已认证
     * 
     * @return 如果当前请求已认证返回 true
     */
    default boolean isAuthenticated() {
        return getCurrentPrincipal() != null;
    }

    /**
     * 检查是否拥有所有指定权限
     * 
     * @param permissions 权限列表
     * @return 如果拥有所有权限返回 true
     */
    default boolean hasAllPermissions(String... permissions) {
        for (String permission : permissions) {
            if (!hasPermission(permission)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 检查是否拥有任一指定权限
     * 
     * @param permissions 权限列表
     * @return 如果拥有任一权限返回 true
     */
    default boolean hasAnyPermission(String... permissions) {
        for (String permission : permissions) {
            if (hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取当前租户 ID
     * 
     * <p>在多租户场景下，返回当前请求所属的租户标识</p>
     * 
     * @return 租户 ID，如果不是多租户场景返回 null
     */
    default String getTenantId() {
        Principal principal = getCurrentPrincipal();
        if (principal instanceof TenantAwarePrincipal) {
            return ((TenantAwarePrincipal) principal).getTenantId();
        }
        return null;
    }
}
