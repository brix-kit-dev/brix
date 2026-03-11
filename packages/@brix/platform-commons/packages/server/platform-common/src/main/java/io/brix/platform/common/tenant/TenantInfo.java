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
package io.brix.platform.common.tenant;

/**
 * Tenant Additional Information
 * 
 * <p>Optional tenant metadata for storing additional tenant configuration
 * 
 * @author Brix Platform Authors Platform Team
 * @since 1.0.0
 */
public class TenantInfo {
    
    /** Tenant ID */
    private final String tenantId;
    
    /** Tenant name */
    private final String tenantName;
    
    /** Tenant status */
    private final String status;
    
    /** Tenant configuration (JSON format) */
    private final String config;

    public TenantInfo(String tenantId, String tenantName, String status) {
        this(tenantId, tenantName, status, null);
    }

    public TenantInfo(String tenantId, String tenantName, String status, String config) {
        this.tenantId = tenantId;
        this.tenantName = tenantName;
        this.status = status;
        this.config = config;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getTenantName() {
        return tenantName;
    }

    public String getStatus() {
        return status;
    }

    public String getConfig() {
        return config;
    }

    /**
     * Create a Builder
     * 
     * @param tenantId Tenant ID
     * @return Builder instance
     */
    public static Builder builder(String tenantId) {
        return new Builder(tenantId);
    }

    /**
     * TenantInfo Builder
     */
    public static class Builder {
        private final String tenantId;
        private String tenantName;
        private String status = "ACTIVE";
        private String config;

        private Builder(String tenantId) {
            this.tenantId = tenantId;
        }

        public Builder tenantName(String tenantName) {
            this.tenantName = tenantName;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder config(String config) {
            this.config = config;
            return this;
        }

        public TenantInfo build() {
            return new TenantInfo(tenantId, tenantName, status, config);
        }
    }
}
