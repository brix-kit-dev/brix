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
package io.runtime.orchestrator.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.runtime.orchestrator.endpoint.PluginRegistryEndpoint;
import io.runtime.orchestrator.manifest.UIManifestLoader;
import io.runtime.orchestrator.registry.ModuleRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestController;

/**
 * 插件注册表端点自动配置
 *
 * <h2>架构定位（v3.0.5 Manifest-Driven 动态发现）</h2>
 * <p>
 * 自动配置 {@link PluginRegistryEndpoint}，提供 /api/plugins REST 端点。
 * 仅在 Web 应用且存在 {@link ModuleRegistry} Bean 时激活。
 * </p>
 *
 * <h2>激活条件</h2>
 * <ul>
 *   <li>Web 应用（有 DispatcherServlet）</li>
 *   <li>存在 ModuleRegistry Bean</li>
 *   <li>存在 @RestController 注解支持</li>
 *   <li>配置 brix.plugin-registry.enabled=true（默认 true）</li>
 * </ul>
 *
 * <h2>配置示例</h2>
 * <pre>{@code
 * brix:
 *   plugin-registry:
 *     enabled: true
 * host:
 *   mode: product
 *   plugins:
 *     base-url: /plugins
 * }</pre>
 *
 * @author Runtime SDK Team
 * @since 3.0.5
 * @see PluginRegistryEndpoint
 */
@AutoConfiguration
@ConditionalOnWebApplication
@ConditionalOnClass(RestController.class)
@ConditionalOnBean(ModuleRegistry.class)
@ConditionalOnProperty(prefix = "brix.plugin-registry", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PluginRegistryAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PluginRegistryAutoConfiguration.class);

    /**
     * 创建 UI Manifest 加载器 Bean
     *
     * @param objectMapper JSON 序列化器
     * @return UI Manifest 加载器
     */
    @Bean
    @ConditionalOnMissingBean(UIManifestLoader.class)
    public UIManifestLoader uiManifestLoader(ObjectMapper objectMapper) {
        log.info("Creating UIManifestLoader - loading UI manifests from classpath");
        return new UIManifestLoader(objectMapper);
    }

    /**
     * 创建插件注册表端点 Bean
     *
     * @param moduleRegistry 模块注册表
     * @param manifestLoader UI Manifest 加载器
     * @return 插件注册表端点
     */
    @Bean
    @ConditionalOnMissingBean(PluginRegistryEndpoint.class)
    public PluginRegistryEndpoint pluginRegistryEndpoint(
            ModuleRegistry moduleRegistry,
            UIManifestLoader manifestLoader) {
        log.info("Creating PluginRegistryEndpoint - /api/plugins endpoint will be available");
        return new PluginRegistryEndpoint(moduleRegistry, manifestLoader);
    }
}
