/*
 * Copyright 2026 Brix Authors
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
package io.brix.platform.starter.shell;

/**
 * Runtime Shell Configuration Properties Base Class
 * 
 * <p>Extracts common configuration fields for Standalone and Embedded modes,
 * eliminating approximately 60% of duplicate configuration code. Subclasses only need to declare mode-specific fields.</p>
 * 
 * <h2>Common Fields</h2>
 * <ul>
 *   <li><b>moduleId</b> — Module identifier, used for capability registration and log identification</li>
 *   <li><b>tenantId</b> — Default tenant ID, used in multi-tenant scenarios</li>
 *   <li><b>scheduling</b> — Scheduled task configuration (supported in both modes)</li>
 *   <li><b>lock</b> — Distributed lock base configuration (supported in both modes)</li>
 * </ul>
 * 
 * <h2>Inheritance Hierarchy</h2>
 * <pre>
 * AbstractShellProperties
 *     ├── StandaloneShellProperties  — Product mode (Kafka/Redis/Declarative capability configuration)
 *     └── EmbeddedShellProperties    — Embedded mode (Webhook/In-memory storage/Lightweight configuration)
 * </pre>
 * 
 * <h2>Configuration Example</h2>
 * <pre>{@code
 * brix:
 *   shell:
 *     standalone:         # or embedded
 *       module-id: my-module
 *       tenant-id: default
 *       scheduling:
 *         enabled: true
 *         pool-size: 5
 * }</pre>
 *
 * @author Platform Team
 * @since 3.1.0
 */
public abstract class AbstractShellProperties {

    /**
     * Module ID, used to identify the currently running module
     * 
     * <p>This field serves as module identifier in capability registration, log output, and monitoring metrics.
     * Recommend keeping consistent with Maven artifactId.</p>
     */
    private String moduleId;

    /**
     * Tenant ID, used in multi-tenant scenarios
     * 
     * <p>Default value "default" indicates single-tenant mode.
     * Can be dynamically switched at runtime via TenantContext.</p>
     */
    private String tenantId = "default";

    /**
     * Scheduled task configuration
     */
    private SchedulingConfig scheduling = new SchedulingConfig();

    /**
     * Lock configuration (base part)
     */
    private LockConfig lock = new LockConfig();

    // ==================== Getters & Setters ====================

    public String getModuleId() {
        return moduleId;
    }

    public void setModuleId(String moduleId) {
        this.moduleId = moduleId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public SchedulingConfig getScheduling() {
        return scheduling;
    }

    public void setScheduling(SchedulingConfig scheduling) {
        this.scheduling = scheduling;
    }

    public LockConfig getLock() {
        return lock;
    }

    public void setLock(LockConfig lock) {
        this.lock = lock;
    }

    // ==================== Common Nested Configuration Classes ====================

    /**
     * Scheduled Task Configuration
     * 
     * <p>Both Standalone and Embedded modes support scheduled tasks,
     * difference is default thread pool size (Standalone=5, Embedded=2).</p>
     */
    public static class SchedulingConfig {
        private boolean enabled = true;
        private int poolSize = 5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getPoolSize() {
            return poolSize;
        }

        public void setPoolSize(int poolSize) {
            this.poolSize = poolSize;
        }
    }

    /**
     * Lock Configuration (Base Part)
     * 
     * <p>Subclasses can extend with additional lock configuration fields
     * (e.g., Standalone's type, Embedded's fair).</p>
     */
    public static class LockConfig {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
