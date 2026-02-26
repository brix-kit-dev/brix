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
package io.runtime.manifest.loader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import io.runtime.manifest.model.ModuleManifest;

/**
 * YAML Format Manifest Loader.
 *
 * <p>Supports loading YAML and JSON format manifest files.</p>
 * <p>支持 YAML 和 JSON 格式的 Manifest 文件加载。</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public class YamlManifestLoader implements ManifestLoader {

    private static final Logger logger = LoggerFactory.getLogger(YamlManifestLoader.class);

    private static final List<String> SUPPORTED_EXTENSIONS = List.of("yaml", "yml", "json");

    /**
     * YAML ObjectMapper.
     */
    private final ObjectMapper yamlMapper;

    /**
     * JSON ObjectMapper.
     */
    private final ObjectMapper jsonMapper;

    /**
     * Creates a YAML Manifest loader.
     */
    public YamlManifestLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        configureMapper(this.yamlMapper);

        this.jsonMapper = new ObjectMapper();
        configureMapper(this.jsonMapper);
    }

    /**
     * Configures ObjectMapper.
     * 配置 ObjectMapper
     */
    private void configureMapper(ObjectMapper mapper) {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ModuleManifest load(Path path) {
        Objects.requireNonNull(path, "Path cannot be null");
        
        if (!Files.exists(path)) {
            throw new ManifestLoadException("Manifest file not found: " + path);
        }

        logger.debug("Loading manifest from: {}", path);

        try (InputStream is = Files.newInputStream(path)) {
            ObjectMapper mapper = getMapperForFile(path.getFileName().toString());
            ModuleManifest manifest = mapper.readValue(is, ModuleManifest.class);
            logger.info("Loaded manifest: {} ({})", manifest.getModuleId(), manifest.getModuleVersion());
            return manifest;
        } catch (IOException e) {
            throw new ManifestLoadException("Failed to load manifest from: " + path, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ModuleManifest load(InputStream inputStream) {
        Objects.requireNonNull(inputStream, "InputStream cannot be null");

        try {
            // Default to YAML parser (also JSON compatible)
            // 默认使用 YAML 解析器（也兼容 JSON）
            return yamlMapper.readValue(inputStream, ModuleManifest.class);
        } catch (IOException e) {
            throw new ManifestLoadException("Failed to load manifest from input stream", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ModuleManifest loadFromString(String content) {
        Objects.requireNonNull(content, "Content cannot be null");

        try {
            // Default to YAML parser
            // 默认使用 YAML 解析器
            return yamlMapper.readValue(content, ModuleManifest.class);
        } catch (IOException e) {
            throw new ManifestLoadException("Failed to load manifest from string", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ModuleManifest loadFromClasspath(String resourcePath) {
        return loadFromClasspath(resourcePath, Thread.currentThread().getContextClassLoader());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ModuleManifest loadFromClasspath(String resourcePath, ClassLoader classLoader) {
        Objects.requireNonNull(resourcePath, "Resource path cannot be null");
        Objects.requireNonNull(classLoader, "ClassLoader cannot be null");

        logger.debug("Loading manifest from classpath: {}", resourcePath);

        try (InputStream is = classLoader.getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new ManifestLoadException("Manifest not found in classpath: " + resourcePath);
            }
            
            ObjectMapper mapper = getMapperForFile(resourcePath);
            ModuleManifest manifest = mapper.readValue(is, ModuleManifest.class);
            logger.info("Loaded manifest from classpath: {} ({})", 
                manifest.getModuleId(), manifest.getModuleVersion());
            return manifest;
        } catch (IOException e) {
            throw new ManifestLoadException("Failed to load manifest from classpath: " + resourcePath, e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean supports(Path path) {
        return path != null && supports(path.getFileName().toString());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean supports(String filename) {
        if (filename == null) return false;
        String lower = filename.toLowerCase();
        return SUPPORTED_EXTENSIONS.stream().anyMatch(ext -> lower.endsWith("." + ext));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<String> getSupportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }

    /**
     * Gets corresponding ObjectMapper based on filename.
     * 根据文件名获取对应的 ObjectMapper
     */
    private ObjectMapper getMapperForFile(String filename) {
        if (filename != null && filename.toLowerCase().endsWith(".json")) {
            return jsonMapper;
        }
        return yamlMapper;
    }
}
