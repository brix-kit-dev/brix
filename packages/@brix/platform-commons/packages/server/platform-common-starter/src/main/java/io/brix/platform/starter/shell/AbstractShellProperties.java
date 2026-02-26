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
 * 运行壳配置属性基类
 * 
 * <p>抽取 Standalone 和 Embedded 两种模式的公共配置字段，
 * 消除约 60% 的重复配置代码。子类只需声明模式特有的字段。</p>
 * 
 * <h2>公共字段</h2>
 * <ul>
 *   <li><b>moduleId</b> — 模块标识，用于能力注册和日志标识</li>
 *   <li><b>tenantId</b> — 默认租户ID，多租户场景下使用</li>
 *   <li><b>scheduling</b> — 定时任务配置（两种模式均支持）</li>
 *   <li><b>lock</b> — 分布式锁基础配置（两种模式均支持）</li>
 * </ul>
 * 
 * <h2>继承关系</h2>
 * <pre>
 * AbstractShellProperties
 *     ├── StandaloneShellProperties  — 产品模式（Kafka/Redis/能力声明式配置）
 *     └── EmbeddedShellProperties    — 嵌入模式（Webhook/内存存储/轻量配置）
 * </pre>
 * 
 * <h2>配置示例</h2>
 * <pre>{@code
 * brix:
 *   shell:
 *     standalone:         # 或 embedded
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
     * 模块 ID，用于标识当前运行的模块
     * 
     * <p>该字段在能力注册、日志输出、监控指标中作为模块标识。
     * 建议与 Maven artifactId 保持一致。</p>
     */
    private String moduleId;

    /**
     * 租户 ID，多租户场景下使用
     * 
     * <p>默认值 "default" 表示单租户模式。
     * 运行时可通过 TenantContext 动态切换。</p>
     */
    private String tenantId = "default";

    /**
     * 定时任务配置
     */
    private SchedulingConfig scheduling = new SchedulingConfig();

    /**
     * 锁配置（基础部分）
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

    // ==================== 公共嵌套配置类 ====================

    /**
     * 定时任务配置
     * 
     * <p>Standalone 和 Embedded 模式均支持定时任务，
     * 区别在于默认线程池大小（Standalone=5, Embedded=2）。</p>
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
     * 锁配置（基础部分）
     * 
     * <p>子类可扩展额外的锁配置字段
     * （如 Standalone 的 type，Embedded 的 fair）。</p>
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
