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
package io.runtime.orchestrator.endpoint;

import io.runtime.orchestrator.manifest.UIManifestLoader;
import io.runtime.orchestrator.registry.ModuleRegistry;
import io.runtime.sdk.capability.LifecycleCapability;
import io.runtime.sdk.capability.ModuleMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 插件注册表 REST 端点
 *
 * <h2>架构定位（v3.0.5 Manifest-Driven 动态发现）</h2>
 * <p>
 * 此端点提供 /api/plugins REST API，使前端 Host 能够动态获取已注册的插件列表。
 * 遵循 <b>Manifest-Driven</b> 原则：插件信息来自 {@link ModuleRegistry}，
 * 无需在 Host 层硬编码任何插件列表。
 * </p>
 *
 * <h2>核心职责</h2>
 * <ul>
 *   <li>从 ModuleRegistry 读取已注册的模块</li>
 *   <li>转换为前端所需的 PluginInfo 格式</li>
 *   <li>支持根据 host.mode 过滤插件</li>
 * </ul>
 *
 * <h2>API 契约</h2>
 * <pre>
 * GET /api/plugins
 * Response: {
 *   "plugins": [
 *     {
 *       "id": "booking",
 *       "name": "预约管理",
 *       "remoteEntry": "/plugins/booking/remoteEntry.js",
 *       "manifestUrl": "/plugins/booking/ui-manifest.json",
 *       "enabled": true,
 *       "priority": 20
 *     }
 *   ],
 *   "mode": "product"
 * }
 * </pre>
 *
 * @author Runtime SDK Team
 * @since 3.0.5
 * @see ModuleRegistry
 */
@RestController
@RequestMapping("/api/plugins")
public class PluginRegistryEndpoint {

    private static final Logger log = LoggerFactory.getLogger(PluginRegistryEndpoint.class);

    private final ModuleRegistry moduleRegistry;
    private final UIManifestLoader manifestLoader;

    @Value("${host.mode:product}")
    private String hostMode;

    @Value("${host.plugins.base-url:/plugins}")
    private String pluginsBaseUrl;

    /**
     * 构造插件注册表端点
     *
     * @param moduleRegistry 模块注册表
     * @param manifestLoader UI Manifest 加载器
     */
    public PluginRegistryEndpoint(ModuleRegistry moduleRegistry, UIManifestLoader manifestLoader) {
        this.moduleRegistry = moduleRegistry;
        this.manifestLoader = manifestLoader;
        log.info("PluginRegistryEndpoint initialized");
    }

    /**
     * 获取已注册的插件列表
     *
     * <p>从 ModuleRegistry 读取所有已注册模块，转换为前端所需格式。</p>
     *
     * @return 插件列表响应
     */
    @GetMapping
    public PluginsResponse getPlugins() {
        log.debug("Fetching plugins from ModuleRegistry, mode={}", hostMode);

        List<PluginInfo> plugins = moduleRegistry.getByStartupOrder().stream()
                .map(this::toPluginInfo)
                .toList();

        log.info("Returning {} plugins for mode={}", plugins.size(), hostMode);
        return new PluginsResponse(plugins, hostMode);
    }

    /**
     * 将 LifecycleCapability 转换为 PluginInfo
     */
    @SuppressWarnings("unchecked")
    private PluginInfo toPluginInfo(LifecycleCapability module) {
        ModuleMetadata metadata = module.getMetadata();
        String moduleId = metadata.getModuleId();
        String name = metadata.getModuleName() != null ? metadata.getModuleName() : moduleId;
        int priority = metadata.getStartupOrder();

        // 构建前端资源路径
        // 约定：UI 资源路径为 /plugins/{id}/
        String baseUrl = pluginsBaseUrl.endsWith("/") ? pluginsBaseUrl : pluginsBaseUrl + "/";
        String remoteEntry = baseUrl + moduleId + "/remoteEntry.js";
        String manifestUrl = baseUrl + moduleId + "/ui-manifest.json";

        // 1. 首先从 UIManifestLoader 获取 manifest（从配置文件加载）
        Map<String, Object> manifest = manifestLoader.getManifest(moduleId);
        
        // 2. 如果没有，尝试从 attributes 获取（插件启动时注入）
        if (manifest == null && metadata.getAttributes() != null) {
            Object uiManifest = metadata.getAttributes().get("uiManifest");
            if (uiManifest instanceof Map) {
                manifest = (Map<String, Object>) uiManifest;
            }
        }

        return new PluginInfo(
                moduleId,
                name,
                remoteEntry,
                manifestUrl,
                true,  // 已注册即启用
                priority,
                manifest
        );
    }

    /**
     * 插件信息
     */
    public record PluginInfo(
            String id,
            String name,
            String remoteEntry,
            String manifestUrl,
            boolean enabled,
            int priority,
            Map<String, Object> manifest
    ) {}

    /**
     * 插件列表响应
     */
    public record PluginsResponse(
            List<PluginInfo> plugins,
            String mode
    ) {}
}
