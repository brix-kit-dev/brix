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
package io.runtime.manifest.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Module Manifest
 *
 * <p>Defines module metadata, dependencies, permissions, and resource configuration.
 * Corresponds to module.manifest.yaml or module.manifest.json files.</p>
 *
 * <p>As part of v3.2 architecture refactoring, configuration nested classes have been 
 * extracted to individual files to keep this file under 500 lines:</p>
 * <ul>
 *   <li>{@link ResourceConfig}, {@link RouteConfig}, {@link MenuConfig}, {@link AssetConfig}</li>
 *   <li>{@link CapabilitiesConfig}</li>
 *   <li>{@link EventsConfig}, {@link EventPublishConfig}, {@link EventSubscribeConfig}, {@link RetryConfig}</li>
 *   <li>{@link ResilienceConfig}, {@link CircuitBreakerConfig}, {@link RateLimiterConfig}</li>
 * </ul>
 *
 * <h3>Manifest File Example</h3>
 * <pre>{@code
 * # module.manifest.yaml
 * module:
 *   id: brix-app-booking
 *   name: Booking Management
 *   version: 3.0.0
 *   description: Provides booking-related functionality
 *
 * runtime:
 *   minVersion: 3.0.0
 *   startupOrder: 100
 *
 * dependencies:
 *   - moduleId: brix-core-user
 *     version: ">=3.0.0"
 *     optional: false
 *
 * permissions:
 *   - eventBus:publish
 *   - stateStore:read
 *   - stateStore:write
 *
 * resources:
 *   routes:
 *     - path: /api/booking
 *       methods: [GET, POST]
 *   menus:
 *     - id: menu-booking
 *       title: Booking Management
 *       icon: calendar
 * }</pre>
 *
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public class ModuleManifest {

    /**
     * Module basic information.
     */
    private ModuleInfo module;

    /**
     * Runtime configuration.
     */
    private RuntimeConfig runtime;

    /**
     * Module dependency list.
     */
    private List<ModuleDependency> dependencies = new ArrayList<>();

    /**
     * Permission list.
     */
    private List<String> permissions = new ArrayList<>();

    /**
     * Resource configuration.
     */
    private ResourceConfig resources;

    /**
     * Capabilities configuration.
     *
     * <p>Declares required runtime capabilities including required and optional capabilities.</p>
     */
    private CapabilitiesConfig capabilities;

    /**
     * Events configuration.
     *
     * <p>Declares events published and subscribed by this module.</p>
     */
    private EventsConfig events;

    /**
     * Resilience configuration.
     *
     * <p>Circuit breaker, rate limiter, and other fault tolerance configurations.</p>
     */
    private ResilienceConfig resilience;

    /**
     * Extension properties.
     */
    private Map<String, Object> extensions = new HashMap<>();

    // ==================== Getters and Setters ====================

    public ModuleInfo getModule() {
        return module;
    }

    public void setModule(ModuleInfo module) {
        this.module = module;
    }

    public RuntimeConfig getRuntime() {
        return runtime;
    }

    public void setRuntime(RuntimeConfig runtime) {
        this.runtime = runtime;
    }

    public List<ModuleDependency> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<ModuleDependency> dependencies) {
        this.dependencies = dependencies != null ? dependencies : new ArrayList<>();
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions != null ? permissions : new ArrayList<>();
    }

    public ResourceConfig getResources() {
        return resources;
    }

    public void setResources(ResourceConfig resources) {
        this.resources = resources;
    }

    public Map<String, Object> getExtensions() {
        return extensions;
    }

    public void setExtensions(Map<String, Object> extensions) {
        this.extensions = extensions != null ? extensions : new HashMap<>();
    }

    public CapabilitiesConfig getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(CapabilitiesConfig capabilities) {
        this.capabilities = capabilities;
    }

    public EventsConfig getEvents() {
        return events;
    }

    public void setEvents(EventsConfig events) {
        this.events = events;
    }

    public ResilienceConfig getResilience() {
        return resilience;
    }

    public void setResilience(ResilienceConfig resilience) {
        this.resilience = resilience;
    }

    // ==================== Convenience Methods ====================

    /**
     * Gets module ID.
     *
     * @return Module ID
     */
    public String getModuleId() {
        return module != null ? module.getId() : null;
    }

    /**
     * Gets module name.
     *
     * @return Module name
     */
    public String getModuleName() {
        return module != null ? module.getName() : null;
    }

    /**
     * Gets module version.
     *
     * @return Module version
     */
    public String getModuleVersion() {
        return module != null ? module.getVersion() : null;
    }

    /**
     * Gets startup order.
     *
     * @return Startup order, defaults to 0
     */
    public int getStartupOrder() {
        return runtime != null ? runtime.getStartupOrder() : 0;
    }

    /**
     * Gets list of dependent module IDs.
     *
     * @return List of dependent module IDs
     */
    public List<String> getDependencyIds() {
        if (dependencies == null || dependencies.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> ids = new ArrayList<>();
        for (ModuleDependency dep : dependencies) {
            ids.add(dep.getModuleId());
        }
        return ids;
    }

    /**
     * Checks if module has specified permission.
     *
     * @param permission Permission identifier
     * @return true if the module has this permission
     */
    public boolean hasPermission(String permission) {
        return permissions != null && permissions.contains(permission);
    }

    /**
     * Gets list of required capabilities.
     *
     * @return Required capabilities list, returns empty list if not configured
     */
    public List<String> getRequiredCapabilities() {
        if (capabilities == null || capabilities.getRequired() == null) {
            return Collections.emptyList();
        }
        return capabilities.getRequired();
    }

    /**
     * Gets list of optional capabilities.
     *
     * @return Optional capabilities list, returns empty list if not configured
     */
    public List<String> getOptionalCapabilities() {
        if (capabilities == null || capabilities.getOptional() == null) {
            return Collections.emptyList();
        }
        return capabilities.getOptional();
    }

    /**
     * Checks if module requires specified capability.
     *
     * @param capability Capability identifier
     * @return true if the capability is required
     */
    public boolean requiresCapability(String capability) {
        return getRequiredCapabilities().contains(capability);
    }

    @Override
    public String toString() {
        return "ModuleManifest{" +
               "module=" + module +
               ", runtime=" + runtime +
               ", dependencies=" + dependencies.size() +
               ", permissions=" + permissions.size() +
               '}';
    }

    // ==================== Nested Classes ====================

    /**
     * Module Basic Information.
     * 模块基本信息
     */
    public static class ModuleInfo {

        /** Module unique identifier | 模块唯一标识 */
        private String id;

        /** Module display name | 模块显示名称 */
        private String name;

        /** Module version | 模块版本 */
        private String version;

        /** Module description | 模块描述 */
        private String description;

        /** Author | 作者 */
        private String author;

        /** Homepage URL | 主页 URL */
        private String homepage;

        /** License | 许可证 */
        private String license;

        /** Tags | 标签 */
        private List<String> tags = new ArrayList<>();

        // Getters and Setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        
        public String getHomepage() { return homepage; }
        public void setHomepage(String homepage) { this.homepage = homepage; }
        
        public String getLicense() { return license; }
        public void setLicense(String license) { this.license = license; }
        
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags != null ? tags : new ArrayList<>(); }

        @Override
        public String toString() {
            return "ModuleInfo{id='" + id + "', name='" + name + "', version='" + version + "'}";
        }
    }

    /**
     * Runtime Configuration.
     * 运行时配置
     */
    public static class RuntimeConfig {

        /** Minimum runtime version requirement | 最低运行时版本要求 */
        private String minVersion;

        /** Startup order | 启动顺序 */
        private int startupOrder = 0;

        /** Whether to lazy load | 是否懒加载 */
        private boolean lazyLoad = false;

        /** Whether singleton | 是否单例 */
        private boolean singleton = true;

        /** Timeout in milliseconds | 超时时间（毫秒） */
        private long timeout = 30000;

        // Getters and Setters
        public String getMinVersion() { return minVersion; }
        public void setMinVersion(String minVersion) { this.minVersion = minVersion; }
        
        public int getStartupOrder() { return startupOrder; }
        public void setStartupOrder(int startupOrder) { this.startupOrder = startupOrder; }
        
        public boolean isLazyLoad() { return lazyLoad; }
        public void setLazyLoad(boolean lazyLoad) { this.lazyLoad = lazyLoad; }
        
        public boolean isSingleton() { return singleton; }
        public void setSingleton(boolean singleton) { this.singleton = singleton; }
        
        public long getTimeout() { return timeout; }
        public void setTimeout(long timeout) { this.timeout = timeout; }

        @Override
        public String toString() {
            return "RuntimeConfig{minVersion='" + minVersion + "', startupOrder=" + startupOrder + "}";
        }
    }

    /**
     * Module Dependency.
     * 模块依赖
     */
    public static class ModuleDependency {

        /** Dependent module ID | 依赖模块 ID */
        private String moduleId;

        /** Version requirement (supports semver expressions) | 版本要求（支持语义化版本表达式） */
        private String version;

        /** Whether optional | 是否可选 */
        private boolean optional = false;

        // Getters and Setters
        public String getModuleId() { return moduleId; }
        public void setModuleId(String moduleId) { this.moduleId = moduleId; }
        
        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        
        public boolean isOptional() { return optional; }
        public void setOptional(boolean optional) { this.optional = optional; }

        @Override
        public String toString() {
            return "ModuleDependency{moduleId='" + moduleId + "', version='" + version + 
                   "', optional=" + optional + "}";
        }
    }
}
