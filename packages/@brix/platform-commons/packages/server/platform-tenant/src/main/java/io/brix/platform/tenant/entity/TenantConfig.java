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
package io.brix.platform.tenant.entity;

import java.time.OffsetDateTime;
import java.util.Objects;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.brix.platform.tenant.enums.ConfigType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Tenant Configuration Entity — plugin-level key-value configuration per tenant.
 *
 * <p>Maps to the {@code sys_tenant_config} table. Each row stores one
 * configuration entry scoped by (tenant_id, namespace, key). Plugins use
 * this table to persist tenant-specific configuration without schema changes.
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Implementation entity in platform-tenant.</p>
 *
 * <h3>Key Design Decisions</h3>
 * <ul>
 *   <li><b>JSONB value:</b> config_value is JSONB, supporting primitive and complex structures</li>
 *   <li><b>Namespace scoping:</b> plugin isolation via config_namespace (e.g. "platform", "reservation")</li>
 *   <li><b>Sensitivity flag:</b> is_sensitive marks values requiring encryption/masking in UI</li>
 *   <li><b>Read-only flag:</b> is_readonly restricts modification to platform admins only</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see ConfigType
 */
@Entity
@Table(
    name = "sys_tenant_config",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_tenant_config", columnNames = {"tenant_id", "config_namespace", "config_key"})
    },
    indexes = {
        @Index(name = "idx_tenant_config_tenant", columnList = "tenant_id"),
        @Index(name = "idx_tenant_config_ns", columnList = "tenant_id, config_namespace")
    }
)
public class TenantConfig {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "config_namespace", nullable = false, length = 100)
    private String configNamespace;

    @Column(name = "config_key", nullable = false, length = 200)
    private String configKey;

    @Column(name = "config_value", nullable = false, columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String configValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "config_type", nullable = false, length = 20)
    private ConfigType configType = ConfigType.STRING;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "is_sensitive", nullable = false)
    private boolean sensitive;

    @Column(name = "is_readonly", nullable = false)
    private boolean readonly;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;

    // ========================================================================
    // Constructors
    // ========================================================================

    public TenantConfig() {
    }

    public TenantConfig(Long tenantId, String configNamespace, String configKey, String configValue) {
        this.tenantId = tenantId;
        this.configNamespace = configNamespace;
        this.configKey = configKey;
        this.configValue = configValue;
        this.configType = ConfigType.STRING;
    }

    // ========================================================================
    // JPA Lifecycle Callbacks
    // ========================================================================

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    // ========================================================================
    // Getters and Setters
    // ========================================================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getConfigNamespace() {
        return configNamespace;
    }

    public void setConfigNamespace(String configNamespace) {
        this.configNamespace = configNamespace;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public ConfigType getConfigType() {
        return configType;
    }

    public void setConfigType(ConfigType configType) {
        this.configType = configType;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isSensitive() {
        return sensitive;
    }

    public void setSensitive(boolean sensitive) {
        this.sensitive = sensitive;
    }

    public boolean isReadonly() {
        return readonly;
    }

    public void setReadonly(boolean readonly) {
        this.readonly = readonly;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    // ========================================================================
    // Object Methods
    // ========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TenantConfig that = (TenantConfig) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "TenantConfig{" +
               "id=" + id +
               ", tenantId=" + tenantId +
               ", namespace='" + configNamespace + '\'' +
               ", key='" + configKey + '\'' +
               ", type=" + configType +
               '}';
    }
}
