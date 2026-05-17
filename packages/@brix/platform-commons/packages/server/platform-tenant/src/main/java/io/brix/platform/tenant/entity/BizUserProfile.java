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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * User Profile Entity — per-tenant user profile with preferences.
 *
 * <p>Maps to the {@code biz_user_profile} table (created in V009).
 * This entity provides access to the user's preferences JSONB column
 * for the three-layer configuration merge:
 * {@code effectiveValue = userPreference ?? tenantConfig ?? platformDefault}.</p>
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Platform Commons entity — used by TenantSettingsService
 * for user preference reads/writes.</p>
 *
 * <h3>Polymorphic Reference Model</h3>
 * <pre>
 * ref_type = 'MEMBER'    → ref_id references sys_tenant_member.id
 * ref_type = 'PRINCIPAL' → ref_id references sys_tenant_principal.id
 * </pre>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 * @see io.brix.platform.tenant.enums.ProfileRefType
 */
@Entity
@Table(
    name = "biz_user_profile",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_profile_tenant_ref", columnNames = {"tenant_id", "ref_type", "ref_id"})
    },
    indexes = {
        @Index(name = "idx_user_profile_tenant", columnList = "tenant_id"),
        @Index(name = "idx_user_profile_ref", columnList = "ref_type, ref_id")
    }
)
public class BizUserProfile {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "ref_type", nullable = false, length = 20)
    private String refType;

    @Column(name = "ref_id", nullable = false)
    private Long refId;

    @Column(name = "nickname", length = 100)
    private String nickname;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(name = "display_name", length = 100)
    private String displayName;

    @Column(name = "preferences", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String preferences;

    @Column(name = "extended", columnDefinition = "JSONB")
    @JdbcTypeCode(SqlTypes.JSON)
    private String extended;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    // ========================================================================
    // Constructors
    // ========================================================================

    public BizUserProfile() {
    }

    // ========================================================================
    // JPA Lifecycle Callbacks
    // ========================================================================

    @PrePersist
    protected void onCreate() {
        this.createdAt = OffsetDateTime.now();
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

    public String getRefType() {
        return refType;
    }

    public void setRefType(String refType) {
        this.refType = refType;
    }

    public Long getRefId() {
        return refId;
    }

    public void setRefId(Long refId) {
        this.refId = refId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPreferences() {
        return preferences;
    }

    public void setPreferences(String preferences) {
        this.preferences = preferences;
    }

    public String getExtended() {
        return extended;
    }

    public void setExtended(String extended) {
        this.extended = extended;
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

    // ========================================================================
    // Object Methods
    // ========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BizUserProfile that = (BizUserProfile) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "BizUserProfile{" +
               "id=" + id +
               ", tenantId=" + tenantId +
               ", refType='" + refType + '\'' +
               ", refId=" + refId +
               '}';
    }
}
