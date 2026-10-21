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
package io.infra.adapter.persistence.jpa;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.CodeSource;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.runtime.manifest.loader.ManifestLoadException;
import io.runtime.manifest.loader.PluginManifestLoader;
import io.runtime.manifest.model.PluginManifest;
import io.runtime.manifest.validation.PluginManifestValidator;
import io.runtime.sdk.capability.PersistenceCapability;

/**
 * Resolves data owners from the active plugin manifests on the classpath.
 *
 * <p>A class belongs to the owner whose manifest is packaged in the same artifact, which is the same
 * code-source rule the runtime uses to associate providers with their descriptors (root blueprint
 * BRX-RS-HOST-021). Owner identity is therefore never self-reported by plugin code.</p>
 */
final class OwnerDirectory {

    private final ClassLoader classLoader;
    private final PluginManifestLoader manifestLoader;
    private final ConcurrentMap<String, Optional<OwnerDeclaration>> ownersByOrigin = new ConcurrentHashMap<>();
    private volatile Map<String, OwnerDeclaration> ownersById;

    OwnerDirectory(ClassLoader classLoader, PluginManifestLoader manifestLoader) {
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader must not be null");
        this.manifestLoader = Objects.requireNonNull(manifestLoader, "manifestLoader must not be null");
    }

    /**
     * Returns the owner declared by the manifest packaged with {@code type}.
     */
    Optional<OwnerDeclaration> ownerOf(Class<?> type) {
        Objects.requireNonNull(type, "type must not be null");
        CodeSource source = type.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            return Optional.empty();
        }
        return ownersByOrigin.computeIfAbsent(
            source.getLocation().toExternalForm(),
            origin -> manifestLoader.findActiveFromClasspath(type).map(OwnerDeclaration::of));
    }

    /**
     * Returns the owner with the given plugin id.
     */
    Optional<OwnerDeclaration> owner(String ownerId) {
        return Optional.ofNullable(ownersById().get(ownerId));
    }

    private Map<String, OwnerDeclaration> ownersById() {
        Map<String, OwnerDeclaration> snapshot = ownersById;
        if (snapshot == null) {
            synchronized (this) {
                snapshot = ownersById;
                if (snapshot == null) {
                    snapshot = scanManifests();
                    ownersById = snapshot;
                }
            }
        }
        return snapshot;
    }

    private Map<String, OwnerDeclaration> scanManifests() {
        Map<String, OwnerDeclaration> owners = new LinkedHashMap<>();
        for (URL resource : manifestResources()) {
            OwnerDeclaration owner;
            try (InputStream input = resource.openStream()) {
                owner = OwnerDeclaration.of(manifestLoader.load(input));
            } catch (IOException e) {
                throw new ManifestLoadException("Failed to read plugin manifest " + resource, e);
            }
            if (owners.putIfAbsent(owner.ownerId(), owner) != null) {
                throw new ManifestLoadException("Duplicate plugin id in active manifests: " + owner.ownerId());
            }
        }
        return Collections.unmodifiableMap(owners);
    }

    private List<URL> manifestResources() {
        try {
            Enumeration<URL> resources = classLoader.getResources(PluginManifestValidator.ACTIVE_MANIFEST_RESOURCE);
            return Collections.list(resources);
        } catch (IOException e) {
            throw new ManifestLoadException(
                "Failed to scan classpath for " + PluginManifestValidator.ACTIVE_MANIFEST_RESOURCE, e);
        }
    }

    /**
     * Data owner facts taken from one validated plugin manifest.
     *
     * @param ownerId plugin id
     * @param storageId manifest {@code data.storageId}, or {@code null} when the plugin owns no data
     * @param declaresPersistence whether the manifest declares {@link PersistenceCapability}
     */
    record OwnerDeclaration(String ownerId, String storageId, boolean declaresPersistence) {

        static OwnerDeclaration of(PluginManifest manifest) {
            String storageId = manifest.getData() == null ? null : manifest.getData().getStorageId();
            return new OwnerDeclaration(
                manifest.getMetadata().getPluginId(),
                storageId,
                declaresPersistence(manifest));
        }

        private static boolean declaresPersistence(PluginManifest manifest) {
            if (manifest.getCapabilities() == null) {
                return false;
            }
            return declares(manifest.getCapabilities().getRequired())
                || declares(manifest.getCapabilities().getOptional());
        }

        private static boolean declares(List<PluginManifest.CapabilityRef> references) {
            if (references == null) {
                return false;
            }
            return references.stream()
                .map(PluginManifest.CapabilityRef::getId)
                .anyMatch(id -> PersistenceCapability.class.getName().equals(id)
                    || PersistenceCapability.class.getSimpleName().equals(id));
        }
    }
}
