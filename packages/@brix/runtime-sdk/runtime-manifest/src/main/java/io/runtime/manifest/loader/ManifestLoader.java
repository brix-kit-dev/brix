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

import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

import io.runtime.manifest.model.ModuleManifest;

/**
 * Manifest Loader.
 *
 * <p>Responsible for loading module manifest files from different sources.
 * Supports file system, classpath, and input stream loading.</p>
 * <p>负责从不同来源加载模块清单文件。支持文件系统、类路径和输入流加载。</p>
 *
 * <h3>Supported Formats</h3>
 * <ul>
 *   <li>YAML - module.manifest.yaml or module.manifest.yml</li>
 *   <li>JSON - module.manifest.json</li>
 * </ul>
 *
 * <h3>Usage Example</h3>
 * <pre>{@code
 * ManifestLoader loader = new YamlManifestLoader();
 *
 * // Load from file
 * ModuleManifest manifest = loader.load(Path.of("module.manifest.yaml"));
 *
 * // Load from classpath
 * ModuleManifest manifest = loader.loadFromClasspath("META-INF/module.manifest.yaml");
 *
 * // Load from input stream
 * try (InputStream is = getInputStream()) {
 *     ModuleManifest manifest = loader.load(is);
 * }
 * }</pre>
 *
 * @author Runtime SDK Team
 * @since 3.0.0
 */
public interface ManifestLoader {

    /**
     * Default manifest file names.
     * 默认 manifest 文件名列表
     */
    List<String> DEFAULT_MANIFEST_NAMES = List.of(
        "module.manifest.yaml",
        "module.manifest.yml",
        "module.manifest.json"
    );

    /**
     * Loads Manifest from file path.
     *
     * @param path File path
     * @return Module manifest
     * @throws ManifestLoadException if loading fails
     */
    ModuleManifest load(Path path);

    /**
     * Loads Manifest from input stream.
     *
     * @param inputStream Input stream
     * @return Module manifest
     * @throws ManifestLoadException if loading fails
     */
    ModuleManifest load(InputStream inputStream);

    /**
     * Loads Manifest from string.
     *
     * @param content Manifest content
     * @return Module manifest
     * @throws ManifestLoadException if loading fails
     */
    ModuleManifest loadFromString(String content);

    /**
     * Loads Manifest from classpath.
     *
     * @param resourcePath Resource path
     * @return Module manifest
     * @throws ManifestLoadException if loading fails
     */
    ModuleManifest loadFromClasspath(String resourcePath);

    /**
     * Loads Manifest from classpath with specified class loader.
     *
     * @param resourcePath Resource path
     * @param classLoader Class loader
     * @return Module manifest
     * @throws ManifestLoadException if loading fails
     */
    ModuleManifest loadFromClasspath(String resourcePath, ClassLoader classLoader);

    /**
     * Checks if file is a supported Manifest format.
     *
     * @param path File path
     * @return true if supported
     */
    boolean supports(Path path);

    /**
     * Checks if filename is a supported Manifest format.
     *
     * @param filename Filename
     * @return true if supported
     */
    boolean supports(String filename);

    /**
     * Gets supported file extensions.
     *
     * @return List of file extensions
     */
    List<String> getSupportedExtensions();
}
