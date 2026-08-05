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
package io.brix.platform.identity.entity;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Singleton bootstrap state for first platform-super-admin setup.
 *
 * <p>Row {@code id=1} represents the global Stage A/B state. Stage A is open
 * while {@code completed_at IS NULL}; Stage B is permanently closed when it is
 * populated.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
@Entity(name = "PlatformIdentityBootstrapState")
@Table(name = "sys_bootstrap_state")
public class BootstrapState {

    public static final Long SINGLETON_ID = 1L;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id = SINGLETON_ID;

    @Column(name = "bootstrap_identity_id")
    private Long bootstrapIdentityId;

    @Column(name = "setup_code_hash", length = 128)
    private String setupCodeHash;

    @Column(name = "setup_code_expires_at")
    private OffsetDateTime setupCodeExpiresAt;

    @Column(name = "setup_code_used_at")
    private OffsetDateTime setupCodeUsedAt;

    @Column(name = "bootstrap_session_jti", length = 128)
    private String bootstrapSessionJti;

    @Column(name = "bootstrap_session_expires_at")
    private OffsetDateTime bootstrapSessionExpiresAt;

    @Column(name = "bootstrap_session_used_at")
    private OffsetDateTime bootstrapSessionUsedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "completed_by_identity_id")
    private Long completedByIdentityId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public boolean isSetupCodeUsable(OffsetDateTime now) {
        return !isCompleted()
                && setupCodeHash != null
                && setupCodeUsedAt == null
                && setupCodeExpiresAt != null
                && now.isBefore(setupCodeExpiresAt);
    }

    public boolean isBootstrapSessionUsable(String jti, OffsetDateTime now) {
        return !isCompleted()
                && jti != null
                && jti.equals(bootstrapSessionJti)
                && bootstrapSessionUsedAt == null
                && bootstrapSessionExpiresAt != null
                && now.isBefore(bootstrapSessionExpiresAt);
    }

    public void openSetupCode(String setupCodeHash, OffsetDateTime expiresAt) {
        this.setupCodeHash = setupCodeHash;
        this.setupCodeExpiresAt = expiresAt;
        this.setupCodeUsedAt = null;
        this.bootstrapSessionJti = null;
        this.bootstrapSessionExpiresAt = null;
        this.bootstrapSessionUsedAt = null;
    }

    public void activateSession(String jti, OffsetDateTime expiresAt) {
        this.setupCodeUsedAt = OffsetDateTime.now();
        this.bootstrapSessionJti = jti;
        this.bootstrapSessionExpiresAt = expiresAt;
        this.bootstrapSessionUsedAt = null;
    }

    public void consumeSession() {
        this.bootstrapSessionUsedAt = OffsetDateTime.now();
    }

    public void complete(Long identityId) {
        OffsetDateTime now = OffsetDateTime.now();
        this.completedAt = now;
        this.completedByIdentityId = identityId;
        this.setupCodeHash = null;
        this.setupCodeExpiresAt = null;
        this.setupCodeUsedAt = now;
        this.bootstrapSessionJti = null;
        this.bootstrapSessionExpiresAt = null;
        this.bootstrapSessionUsedAt = now;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBootstrapIdentityId() {
        return bootstrapIdentityId;
    }

    public void setBootstrapIdentityId(Long bootstrapIdentityId) {
        this.bootstrapIdentityId = bootstrapIdentityId;
    }

    public String getSetupCodeHash() {
        return setupCodeHash;
    }

    public void setSetupCodeHash(String setupCodeHash) {
        this.setupCodeHash = setupCodeHash;
    }

    public OffsetDateTime getSetupCodeExpiresAt() {
        return setupCodeExpiresAt;
    }

    public void setSetupCodeExpiresAt(OffsetDateTime setupCodeExpiresAt) {
        this.setupCodeExpiresAt = setupCodeExpiresAt;
    }

    public OffsetDateTime getSetupCodeUsedAt() {
        return setupCodeUsedAt;
    }

    public void setSetupCodeUsedAt(OffsetDateTime setupCodeUsedAt) {
        this.setupCodeUsedAt = setupCodeUsedAt;
    }

    public String getBootstrapSessionJti() {
        return bootstrapSessionJti;
    }

    public void setBootstrapSessionJti(String bootstrapSessionJti) {
        this.bootstrapSessionJti = bootstrapSessionJti;
    }

    public OffsetDateTime getBootstrapSessionExpiresAt() {
        return bootstrapSessionExpiresAt;
    }

    public void setBootstrapSessionExpiresAt(OffsetDateTime bootstrapSessionExpiresAt) {
        this.bootstrapSessionExpiresAt = bootstrapSessionExpiresAt;
    }

    public OffsetDateTime getBootstrapSessionUsedAt() {
        return bootstrapSessionUsedAt;
    }

    public void setBootstrapSessionUsedAt(OffsetDateTime bootstrapSessionUsedAt) {
        this.bootstrapSessionUsedAt = bootstrapSessionUsedAt;
    }

    public OffsetDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(OffsetDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public Long getCompletedByIdentityId() {
        return completedByIdentityId;
    }

    public void setCompletedByIdentityId(Long completedByIdentityId) {
        this.completedByIdentityId = completedByIdentityId;
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
}
