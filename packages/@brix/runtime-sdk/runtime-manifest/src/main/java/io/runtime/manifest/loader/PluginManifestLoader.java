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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import io.runtime.manifest.model.PluginManifest;
import io.runtime.manifest.validation.PluginManifestValidator;

/**
 * Strict v3.0.10 plugin manifest loader.
 *
 * <p>The active plugin manifest path is fixed at
 * {@value PluginManifestValidator#ACTIVE_MANIFEST_RESOURCE}. This loader accepts
 * YAML only and fails on unknown fields so plugin manifests cannot drift into a
 * second, implementation-specific contract.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class PluginManifestLoader {

    private final ObjectMapper yamlMapper;
    private final PluginManifestValidator validator;

    /**
     * Creates a strict plugin manifest loader.
     */
    public PluginManifestLoader() {
        this(new PluginManifestValidator());
    }

    /**
     * Creates a strict plugin manifest loader with an explicit validator.
     *
     * @param validator manifest validator
     */
    public PluginManifestLoader(PluginManifestValidator validator) {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
        this.yamlMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        this.yamlMapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, false);
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
    }

    /**
     * Loads and validates a plugin manifest from a file.
     *
     * @param path manifest path
     * @return validated plugin manifest
     */
    public PluginManifest load(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        if (!Files.exists(path)) {
            throw new ManifestLoadException("Plugin manifest file not found: " + path);
        }
        if (!path.getFileName().toString().equals("plugin-manifest.yaml")) {
            throw new ManifestLoadException("Active plugin manifest must be named plugin-manifest.yaml: " + path);
        }
        try (InputStream inputStream = Files.newInputStream(path)) {
            return load(inputStream);
        } catch (IOException e) {
            throw new ManifestLoadException("Failed to load plugin manifest from: " + path, e);
        }
    }

    /**
     * Loads and validates a plugin manifest from YAML.
     *
     * @param inputStream YAML input stream
     * @return validated plugin manifest
     */
    public PluginManifest load(InputStream inputStream) {
        Objects.requireNonNull(inputStream, "inputStream must not be null");
        try {
            PluginManifest manifest = yamlMapper.readValue(inputStream, PluginManifest.class);
            validator.validateOrThrow(manifest);
            return manifest;
        } catch (IOException e) {
            throw new ManifestLoadException("Failed to parse plugin manifest YAML", e);
        }
    }

    /**
     * Loads and validates a plugin manifest from YAML content.
     *
     * @param content YAML content
     * @return validated plugin manifest
     */
    public PluginManifest loadFromString(String content) {
        Objects.requireNonNull(content, "content must not be null");
        try {
            PluginManifest manifest = yamlMapper.readValue(content, PluginManifest.class);
            validator.validateOrThrow(manifest);
            return manifest;
        } catch (IOException e) {
            throw new ManifestLoadException("Failed to parse plugin manifest YAML", e);
        }
    }

    /**
     * Loads the active plugin manifest resource from a class loader.
     *
     * @param classLoader class loader to search
     * @return validated plugin manifest
     */
    public PluginManifest loadActiveFromClasspath(ClassLoader classLoader) {
        ClassLoader effective = classLoader != null ? classLoader : Thread.currentThread().getContextClassLoader();
        try (InputStream inputStream = effective.getResourceAsStream(
                PluginManifestValidator.ACTIVE_MANIFEST_RESOURCE)) {
            if (inputStream == null) {
                throw new ManifestLoadException("Active plugin manifest not found in classpath: "
                    + PluginManifestValidator.ACTIVE_MANIFEST_RESOURCE);
            }
            return load(inputStream);
        } catch (IOException e) {
            throw new ManifestLoadException("Failed to close plugin manifest classpath resource", e);
        }
    }

    /**
     * Loads the active plugin manifest from the same artifact as the supplied
     * anchor type.
     *
     * @param anchorType type whose code source identifies the owning artifact
     * @return validated plugin manifest from the same artifact
     */
    public PluginManifest loadActiveFromClasspath(Class<?> anchorType) {
        Objects.requireNonNull(anchorType, "anchorType must not be null");
        ClassLoader effective = anchorType.getClassLoader() != null
            ? anchorType.getClassLoader()
            : Thread.currentThread().getContextClassLoader();
        URL codeSource = codeSource(anchorType);
        List<URL> matches = resources(effective, PluginManifestValidator.ACTIVE_MANIFEST_RESOURCE).stream()
            .filter(resource -> belongsToCodeSource(resource, codeSource))
            .toList();
        if (matches.isEmpty()) {
            throw new ManifestLoadException("Active plugin manifest not found in same artifact as "
                + anchorType.getName() + ": " + PluginManifestValidator.ACTIVE_MANIFEST_RESOURCE);
        }
        if (matches.size() > 1) {
            throw new ManifestLoadException("Multiple active plugin manifests found in same artifact as "
                + anchorType.getName() + ": " + matches);
        }
        try (InputStream inputStream = matches.get(0).openStream()) {
            return load(inputStream);
        } catch (IOException e) {
            throw new ManifestLoadException("Failed to load plugin manifest from same artifact as "
                + anchorType.getName(), e);
        }
    }

    private static List<URL> resources(ClassLoader classLoader, String resourceName) {
        try {
            Enumeration<URL> urls = classLoader.getResources(resourceName);
            List<URL> result = new ArrayList<>();
            while (urls.hasMoreElements()) {
                result.add(urls.nextElement());
            }
            return result;
        } catch (IOException e) {
            throw new ManifestLoadException("Failed to scan classpath for " + resourceName, e);
        }
    }

    private static URL codeSource(Class<?> anchorType) {
        CodeSource source = anchorType.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            throw new ManifestLoadException("Class has no code source for plugin manifest association: "
                + anchorType.getName());
        }
        return source.getLocation();
    }

    private static boolean belongsToCodeSource(URL manifestUrl, URL codeSource) {
        String manifest = manifestUrl.toExternalForm();
        String source = codeSource.toExternalForm();
        if (manifest.startsWith("jar:")) {
            if (source.startsWith("jar:")) {
                return manifest.startsWith(jarCodeSourcePrefix(source));
            }
            return manifest.startsWith("jar:" + source + "!");
        }
        if (!"file".equals(manifestUrl.getProtocol()) || !"file".equals(codeSource.getProtocol())) {
            return false;
        }
        try {
            URI manifestUri = manifestUrl.toURI();
            URI sourceUri = codeSource.toURI();
            return manifestUri.getPath() != null
                && sourceUri.getPath() != null
                && manifestUri.getPath().startsWith(sourceUri.getPath());
        } catch (URISyntaxException e) {
            return false;
        }
    }

    private static String jarCodeSourcePrefix(String source) {
        if (source.endsWith("!/")) {
            return source;
        }
        if (source.endsWith("!")) {
            return source + "/";
        }
        return source + "!/";
    }
}
