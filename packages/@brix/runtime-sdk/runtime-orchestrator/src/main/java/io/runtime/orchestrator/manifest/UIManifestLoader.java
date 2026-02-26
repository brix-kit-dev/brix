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
package io.runtime.orchestrator.manifest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

/**
 * UI Manifest 加载服务
 *
 * <p>临时实现：从 classpath 的 ui-manifests.json 文件加载所有插件的 UI manifest。</p>
 * 
 * <p>未来改进方向：
 * <ul>
 *   <li>从各插件 jar 包的 META-INF 目录扫描 ui-manifest.yaml/json</li>
 *   <li>支持 YAML 格式</li>
 *   <li>支持热加载</li>
 * </ul>
 * </p>
 *
 * @author Runtime SDK Team
 * @since 3.1.0
 */
@Service
public class UIManifestLoader {

    private static final Logger log = LoggerFactory.getLogger(UIManifestLoader.class);
    private static final String MANIFEST_FILE = "ui-manifests.json";

    private final ObjectMapper objectMapper;
    private Map<String, Map<String, Object>> manifests = Collections.emptyMap();

    public UIManifestLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void loadManifests() {
        try {
            ClassPathResource resource = new ClassPathResource(MANIFEST_FILE);
            if (resource.exists()) {
                try (InputStream is = resource.getInputStream()) {
                    Map<String, Map<String, Map<String, Object>>> wrapper = objectMapper.readValue(
                        is,
                        new TypeReference<>() {}
                    );
                    this.manifests = wrapper.getOrDefault("manifests", Collections.emptyMap());
                    log.info("Loaded {} UI manifests from {}", manifests.size(), MANIFEST_FILE);
                }
            } else {
                log.warn("UI manifest file not found: {}", MANIFEST_FILE);
            }
        } catch (IOException e) {
            log.error("Failed to load UI manifests from {}: {}", MANIFEST_FILE, e.getMessage());
        }
    }

    /**
     * 获取指定插件的 UI manifest
     *
     * @param moduleId 模块ID
     * @return UI manifest 数据，如果不存在则返回 null
     */
    public Map<String, Object> getManifest(String moduleId) {
        return manifests.get(moduleId);
    }

    /**
     * 获取所有 UI manifest
     *
     * @return 所有 UI manifest（moduleId -> manifest）
     */
    public Map<String, Map<String, Object>> getAllManifests() {
        return Collections.unmodifiableMap(manifests);
    }

    /**
     * 检查指定模块是否有 UI manifest
     *
     * @param moduleId 模块ID
     * @return 是否存在
     */
    public boolean hasManifest(String moduleId) {
        return manifests.containsKey(moduleId);
    }
}
