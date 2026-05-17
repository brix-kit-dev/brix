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

import io.runtime.sdk.annotation.Since;

/**
 * Tenant Provisioning Capability — 租户开通 / 生命周期管理契约。
 *
 * <p>本契约属于 Layer 2A（runtime-sdk-api），是上层（platform-admin、
 * enterprise-host 控制台、运维脚本）执行租户开通 / 激活 / 暂停操作时
 * 的唯一入口。实现位于 Layer 2C（platform-tenant），底层委托给
 * {@code TenantProvisioningService} 完成原子事务（建租户、建 OWNER 成员、
 * 建默认组织）。</p>
 *
 * <h3>分层依据（v3.0.9 蓝图）</h3>
 * <ul>
 *   <li>Layer 1（plugins / extensions）通过 Capability 间接获得开通能力，
 *       禁止直接依赖 platform-tenant 的内部 Service。</li>
 *   <li>Layer 2C 实现 {@link TenantProvisioningCapabilityImpl 内部委托}
 *       现有 {@code TenantProvisioningService}，避免逻辑重复实现。</li>
 * </ul>
 *
 * <h3>状态机</h3>
 * <pre>
 *   PENDING_ACTIVATION ──activateTenant()──▶ ACTIVE
 *   ACTIVE ──suspendTenant()──▶ SUSPENDED ──activateTenant()──▶ ACTIVE
 * </pre>
 *
 * <h3>异常约定</h3>
 * <ul>
 *   <li>租户编码已存在 → {@link IllegalStateException}</li>
 *   <li>OwnerIdentityId 不存在 → {@link IllegalArgumentException}</li>
 *   <li>状态迁移非法（如 TERMINATED → ACTIVE）→ {@link IllegalStateException}</li>
 * </ul>
 *
 * @since 3.2.0
 */
@Since("3.2.0")
public interface TenantProvisioningCapability {

    /**
     * 创建新租户（含 OWNER 成员与默认组织），整个流程在单一事务内完成。
     *
     * @param command 创建命令；不可为 {@code null}
     * @return 新建租户的快照记录
     * @throws IllegalArgumentException 参数缺失或 OwnerIdentity 不存在
     * @throws IllegalStateException    租户编码已被占用
     */
    TenantRecord createTenant(CreateTenantCommand command);

    /**
     * 激活指定租户，将状态置为 ACTIVE。
     *
     * @param tenantId 租户 ID；不可为 {@code null}
     * @throws IllegalArgumentException 租户不存在
     * @throws IllegalStateException    当前状态不允许激活
     */
    void activateTenant(Long tenantId);

    /**
     * 暂停指定租户，将状态置为 SUSPENDED。
     *
     * @param tenantId 租户 ID；不可为 {@code null}
     * @throws IllegalArgumentException 租户不存在
     * @throws IllegalStateException    当前状态不允许暂停
     */
    void suspendTenant(Long tenantId);

    /**
     * 创建租户的命令对象。
     *
     * @param code            租户业务编码（小写字母数字 + 连字符）
     * @param name            租户显示名
     * @param ownerIdentityId OWNER 身份 ID，必须已存在于 sys_identity
     */
    record CreateTenantCommand(String code, String name, Long ownerIdentityId) {}

    /**
     * 租户快照记录。{@code status} 为枚举字符串（PENDING_ACTIVATION / ACTIVE / SUSPENDED 等）。
     */
    record TenantRecord(Long id, String code, String name, String status) {}
}
