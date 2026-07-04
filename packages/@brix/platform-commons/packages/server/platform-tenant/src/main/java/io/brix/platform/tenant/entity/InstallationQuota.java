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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Installation-level tenant quota row.
 *
 * <p>The row is locked with {@code SELECT ... FOR UPDATE} before changing
 * active tenant usage, providing an instance-wide admission control point.</p>
 */
@Entity
@Table(name = "sys_installation_quota")
public class InstallationQuota {

    public static final String DEFAULT_INSTALLATION_ID = "default";
    public static final int DEFAULT_TENANT_QUOTA = 3;

    @Id
    @Column(name = "installation_id", nullable = false, length = 100)
    private String installationId;

    @Column(name = "quota", nullable = false)
    private Integer quota = DEFAULT_TENANT_QUOTA;

    @Column(name = "used", nullable = false)
    private Integer used = 0;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public InstallationQuota() {
    }

    public InstallationQuota(String installationId, Integer quota, Integer used) {
        this.installationId = installationId;
        this.quota = quota;
        this.used = used;
    }

    @PrePersist
    @PreUpdate
    protected void touchUpdatedAt() {
        this.updatedAt = OffsetDateTime.now();
    }

    public boolean hasAvailableSlot() {
        return used < quota;
    }

    public void reserveSlot() {
        if (!hasAvailableSlot()) {
            throw new IllegalStateException("Installation tenant quota has no available slot");
        }
        this.used = used + 1;
    }

    public void releaseSlot() {
        if (used > 0) {
            this.used = used - 1;
        }
    }

    public String getInstallationId() {
        return installationId;
    }

    public void setInstallationId(String installationId) {
        this.installationId = installationId;
    }

    public Integer getQuota() {
        return quota;
    }

    public void setQuota(Integer quota) {
        this.quota = quota;
    }

    public Integer getUsed() {
        return used;
    }

    public void setUsed(Integer used) {
        this.used = used;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InstallationQuota that = (InstallationQuota) o;
        return Objects.equals(installationId, that.installationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(installationId);
    }
}