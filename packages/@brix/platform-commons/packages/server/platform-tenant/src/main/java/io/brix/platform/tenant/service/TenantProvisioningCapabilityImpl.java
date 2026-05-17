/*
 * Copyright 2026 Brix Platform Authors
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
package io.brix.platform.tenant.service;

import io.brix.platform.tenant.dto.CreateTenantRequest;
import io.brix.platform.tenant.entity.Tenant;
import io.brix.platform.tenant.exception.InvalidReferenceException;
import io.runtime.sdk.capability.TenantProvisioningCapability;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * S4 — Layer 2C 实现：将 {@link TenantProvisioningCapability} 契约委托给
 * 现有 {@link TenantProvisioningService}（事务、ID 生成、默认组织等逻辑
 * 复用，避免重复实现）。
 *
 * <p>异常映射：
 * <ul>
 *   <li>{@link InvalidReferenceException}（OwnerIdentity 不存在）→ {@link IllegalArgumentException}</li>
 *   <li>底层抛出的 {@link IllegalStateException}（租户编码冲突 / 状态非法）原样透传</li>
 * </ul>
 *
 * @since 3.2.0
 */
@Service
public class TenantProvisioningCapabilityImpl implements TenantProvisioningCapability {

    private static final Logger log = LoggerFactory.getLogger(TenantProvisioningCapabilityImpl.class);

    private final TenantProvisioningService delegate;

    public TenantProvisioningCapabilityImpl(TenantProvisioningService delegate) {
        this.delegate = delegate;
    }

    @Override
    public TenantRecord createTenant(CreateTenantCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("CreateTenantCommand cannot be null");
        }
        CreateTenantRequest request = CreateTenantRequest.builder()
                .code(command.code())
                .name(command.name())
                .ownerIdentityId(command.ownerIdentityId())
                .build();
        try {
            Tenant created = delegate.createTenant(request);
            log.info("[TenantProvisioning] tenant created: id={}, code={}",
                    created.getId(), created.getCode());
            return toRecord(created);
        } catch (InvalidReferenceException e) {
            // OwnerIdentity 不存在 — 转译为参数错误，符合契约语义。
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    @Override
    public void activateTenant(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        delegate.activateTenant(tenantId);
        log.info("[TenantProvisioning] tenant activated: id={}", tenantId);
    }

    @Override
    public void suspendTenant(Long tenantId) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        delegate.suspendTenant(tenantId);
        log.info("[TenantProvisioning] tenant suspended: id={}", tenantId);
    }

    private static TenantRecord toRecord(Tenant tenant) {
        return new TenantRecord(
                tenant.getId(),
                tenant.getCode(),
                tenant.getName(),
                tenant.getStatus() != null ? tenant.getStatus().name() : null);
    }
}
