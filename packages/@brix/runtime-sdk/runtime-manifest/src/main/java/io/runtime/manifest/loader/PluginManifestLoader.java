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
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

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
        this.yamlMapper = YAMLMapper.builder()
            .configureForJackson2()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, false)
            .build();
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
        } catch (JacksonException e) {
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
        } catch (JacksonException e) {
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

    /**
     * Finds the active plugin manifest packaged in the same artifact as the
     * supplied type.
     *
     * <p>Unlike {@link #loadActiveFromClasspath(Class)}, an artifact without an
     * active manifest (a library, or a type without a code source) is not an
     * error. An artifact with more than one active manifest, or with an invalid
     * one, still fails.</p>
     *
     * @param anchorType type whose code source identifies the artifact
     * @return the validated manifest of that artifact, or empty when it has none
     */
    public Optional<PluginManifest> findActiveFromClasspath(Class<?> anchorType) {
        Objects.requireNonNull(anchorType, "anchorType must not be null");
        CodeSource source = anchorType.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            return Optional.empty();
        }
        ClassLoader effective = anchorType.getClassLoader() != null
            ? anchorType.getClassLoader()
            : Thread.currentThread().getContextClassLoader();
        List<URL> matches = resources(effective, PluginManifestValidator.ACTIVE_MANIFEST_RESOURCE).stream()
            .filter(resource -> belongsToCodeSource(resource, source.getLocation()))
            .toList();
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        if (matches.size() > 1) {
            throw new ManifestLoadException("Multiple active plugin manifests found in same artifact as "
                + anchorType.getName() + ": " + matches);
        }
        try (InputStream inputStream = matches.get(0).openStream()) {
            return Optional.of(load(inputStream));
        } catch (IOException e) {
            throw new ManifestLoadException("Failed to load plugin manifest from same artifact as "
                + anchorType.getName(), e);
        }
    }

    /**
     * Finds the active manifest that lists, under {@code modules}, the artifact at {@code codeSource}.
     *
     * <p>An official plugin's {@code api} and {@code core} artifacts carry no descriptor of their own
     * (root BRX-RS-MANIFEST-005); its {@code server} artifact's manifest lists them. An artifact is
     * recognised by the Maven descriptor {@code META-INF/maven/{groupId}/{artifactId}/pom.properties}
     * its build packages, so a class outside a packaged artifact (a build output directory) belongs to
     * no listed module. The descriptor's version must equal the listed version.</p>
     *
     * @param codeSource code source location of the artifact
     * @param classLoader loader that sees the artifact
     * @param manifests active manifests to search
     * @return the manifest listing the artifact, or empty when none does
     * @throws ManifestLoadException when several manifests list it or the versions differ
     */
    public Optional<PluginManifest> findListingManifest(
            URL codeSource, ClassLoader classLoader, Collection<PluginManifest> manifests) {
        Objects.requireNonNull(codeSource, "codeSource must not be null");
        Objects.requireNonNull(classLoader, "classLoader must not be null");
        Objects.requireNonNull(manifests, "manifests must not be null");
        List<PluginManifest> listing = new ArrayList<>();
        for (PluginManifest manifest : manifests) {
            for (PluginManifest.Module module : manifest.getModules()) {
                if (module.getGroupId() == null || module.getArtifactId() == null) {
                    continue;
                }
                String descriptor = "META-INF/maven/" + module.getGroupId() + "/" + module.getArtifactId() + "/pom.properties";
                for (URL resource : resources(classLoader, descriptor)) {
                    if (!belongsToCodeSource(resource, codeSource)) {
                        continue;
                    }
                    String version = packagedVersion(resource);
                    if (module.getVersion() != null && !module.getVersion().equals(version)) {
                        throw new ManifestLoadException("Plugin " + manifest.getMetadata().getPluginId()
                            + " lists " + module.getArtifactId() + " " + module.getVersion()
                            + " but " + codeSource + " packages version " + version);
                    }
                    if (!listing.contains(manifest)) {
                        listing.add(manifest);
                    }
                }
            }
        }
        if (listing.size() > 1) {
            throw new ManifestLoadException("Artifact " + codeSource + " is listed by more than one plugin: "
                + listing.stream().map(manifest -> manifest.getMetadata().getPluginId()).toList());
        }
        return listing.stream().findFirst();
    }

    private static String packagedVersion(URL pomProperties) {
        Properties properties = new Properties();
        try (InputStream input = pomProperties.openStream()) {
            properties.load(input);
        } catch (IOException e) {
            throw new ManifestLoadException("Failed to read " + pomProperties, e);
        }
        return properties.getProperty("version");
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
