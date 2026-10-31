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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;

import io.runtime.manifest.loader.ManifestLoadException;
import io.runtime.manifest.loader.PluginManifestLoader;
import io.runtime.manifest.model.PluginManifest;
import io.runtime.manifest.validation.PluginManifestValidator;
import io.runtime.sdk.capability.EventBusCapability;
import io.runtime.sdk.capability.PersistenceCapability;

/**
 * Resolves data owners from the active plugin manifests on the classpath.
 *
 * <p>A class belongs to the owner whose manifest is packaged in the same artifact, which is the same
 * code-source rule the runtime uses to associate providers with their descriptors (root blueprint
 * BRX-RS-HOST-021). The api and core artifacts of a three-artifact plugin carry no manifest (root
 * BRX-RS-MANIFEST-005); their classes belong to the owner whose manifest lists the artifact under
 * {@code modules}. Owner identity is therefore never self-reported by plugin code.</p>
 */
final class OwnerDirectory {

    private final ClassLoader classLoader;
    private final PluginManifestLoader manifestLoader;
    private final ConcurrentMap<String, Optional<OwnerDeclaration>> ownersByOrigin = new ConcurrentHashMap<>();
    private volatile Scan scan;

    OwnerDirectory(ClassLoader classLoader, PluginManifestLoader manifestLoader) {
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader must not be null");
        this.manifestLoader = Objects.requireNonNull(manifestLoader, "manifestLoader must not be null");
    }

    /**
     * Returns the owner declared by the manifest packaged with {@code type}, or by the manifest
     * listing {@code type}'s artifact under {@code modules}.
     */
    Optional<OwnerDeclaration> ownerOf(Class<?> type) {
        Objects.requireNonNull(type, "type must not be null");
        CodeSource source = type.getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            return Optional.empty();
        }
        URL location = source.getLocation();
        ClassLoader loader = type.getClassLoader() != null ? type.getClassLoader() : classLoader;
        return ownersByOrigin.computeIfAbsent(
            location.toExternalForm(),
            origin -> manifestLoader.findActiveFromClasspath(type)
                .or(() -> manifestLoader.findListingManifest(location, loader, scan().manifests()))
                .map(OwnerDeclaration::of));
    }

    /**
     * Returns the inbox table the owner's manifest declares under {@code data.inbox}.
     */
    Optional<String> inboxOf(String ownerId) {
        return scan().manifests().stream()
            .filter(manifest -> ownerId.equals(manifest.getMetadata().getPluginId()))
            .map(PluginManifest::getData)
            .filter(java.util.Objects::nonNull)
            .map(PluginManifest.DataSection::getInbox)
            .filter(inbox -> inbox != null && !inbox.isBlank())
            .findFirst();
    }

    /**
     * Returns the outbox table the owner's manifest declares under {@code data.outbox}.
     */
    Optional<String> outboxOf(String ownerId) {
        return manifestOf(ownerId)
            .map(PluginManifest::getData)
            .map(PluginManifest.DataSection::getOutbox)
            .filter(outbox -> !outbox.isBlank());
    }

    /**
     * Returns the events the owner's manifest declares under {@code events.publishes}.
     */
    List<PluginManifest.EventPublish> publicationsOf(String ownerId) {
        return manifestOf(ownerId)
            .map(PluginManifest::getEvents)
            .map(PluginManifest.EventSection::getPublishes)
            .map(List::copyOf)
            .orElse(List.of());
    }

    /**
     * Returns the commands the owner's manifest declares under {@code commands.consumes}, by command type.
     */
    Map<String, PluginManifest.ContractRef> consumedCommandsOf(String ownerId) {
        Map<String, PluginManifest.ContractRef> consumed = new LinkedHashMap<>();
        manifestOf(ownerId)
            .map(PluginManifest::getCommands)
            .map(PluginManifest.ContractSection::getConsumes)
            .orElse(List.of())
            .stream()
            .filter(ref -> ref != null && ref.getCommandType() != null)
            .forEach(ref -> consumed.put(ref.getCommandType(), ref));
        return consumed;
    }

    /**
     * Returns the work of the active owners that holds a pooled connection while it runs: one relay per outbox, and
     * one consumer per event subscription and per provided command.
     */
    ConnectionPoolBudget.Demand connectionDemand() {
        int relays = 0;
        int consumers = 0;
        for (String ownerId : ownerIds()) {
            if (outboxOf(ownerId).isPresent()) {
                relays++;
            }
            Optional<PluginManifest> manifest = manifestOf(ownerId);
            consumers += manifest.map(PluginManifest::getEvents)
                .map(PluginManifest.EventSection::getSubscribes)
                .map(List::size)
                .orElse(0);
            consumers += manifest.map(PluginManifest::getCommands)
                .map(PluginManifest.ContractSection::getProvides)
                .map(List::size)
                .orElse(0);
        }
        return new ConnectionPoolBudget.Demand(relays, consumers);
    }

    /**
     * Returns whether the owner's manifest provides a command.
     */
    boolean providesCommands(String ownerId) {
        return manifestOf(ownerId)
            .map(PluginManifest::getCommands)
            .map(commands -> !commands.getProvides().isEmpty())
            .orElse(false);
    }

    /**
     * Returns whether the owner's manifest provides a task.
     */
    boolean providesTasks(String ownerId) {
        return manifestOf(ownerId)
            .map(PluginManifest::getTasks)
            .map(tasks -> !tasks.getProvides().isEmpty())
            .orElse(false);
    }

    /**
     * Returns whether the owner's manifest declares {@link io.runtime.sdk.capability.CommandCapability}.
     */
    boolean declaresCommandBus(String ownerId) {
        return manifestOf(ownerId)
            .map(PluginManifest::getCapabilities)
            .map(capabilities -> OwnerDeclaration.declares(capabilities.getRequired(),
                    io.runtime.sdk.capability.CommandCapability.class)
                || OwnerDeclaration.declares(capabilities.getOptional(), io.runtime.sdk.capability.CommandCapability.class))
            .orElse(false);
    }

    /**
     * Returns the owner's {@code data.storageId}.
     */
    Optional<String> storageIdOf(String ownerId) {
        return manifestOf(ownerId).map(PluginManifest::getData).map(PluginManifest.DataSection::getStorageId)
            .filter(storageId -> !storageId.isBlank());
    }

    /**
     * Returns whether the owner's manifest declares {@link EventBusCapability}.
     */
    boolean declaresEventBus(String ownerId) {
        return manifestOf(ownerId)
            .map(PluginManifest::getCapabilities)
            .map(capabilities -> OwnerDeclaration.declares(capabilities.getRequired(), EventBusCapability.class)
                || OwnerDeclaration.declares(capabilities.getOptional(), EventBusCapability.class))
            .orElse(false);
    }

    private Optional<PluginManifest> manifestOf(String ownerId) {
        return scan().manifests().stream()
            .filter(manifest -> ownerId.equals(manifest.getMetadata().getPluginId()))
            .findFirst();
    }

    /**
     * Returns the plugin ids of the owners the active manifests declare, sorted.
     */
    List<String> ownerIds() {
        return scan().owners().keySet().stream().sorted().toList();
    }

    /**
     * Returns the owner with the given plugin id.
     */
    Optional<OwnerDeclaration> owner(String ownerId) {
        return Optional.ofNullable(scan().owners().get(ownerId));
    }

    /**
     * Returns the custodial writes the active manifests register for a custodian (root BRX-RS-DATA-045).
     */
    List<CustodialRegistration> custodialWrites(String custodian) {
        List<CustodialRegistration> found = new ArrayList<>();
        for (PluginManifest manifest : scan().manifests()) {
            if (manifest.getData() == null) {
                continue;
            }
            for (PluginManifest.CustodialWrite write : manifest.getData().getCustodialWrites()) {
                if (custodian.equals(write.getCustodian())) {
                    found.add(new CustodialRegistration(manifest.getMetadata().getPluginId(), write));
                }
            }
        }
        return found;
    }

    /** Class loader that sees every plugin manifest and the artifacts they list. */
    ClassLoader classLoader() {
        return classLoader;
    }

    /**
     * A custodial write one owner registers.
     *
     * @param ownerId owner that holds the table
     * @param write the registration
     */
    record CustodialRegistration(String ownerId, PluginManifest.CustodialWrite write) {
    }

    private Scan scan() {
        Scan snapshot = scan;
        if (snapshot == null) {
            synchronized (this) {
                snapshot = scan;
                if (snapshot == null) {
                    snapshot = scanManifests();
                    scan = snapshot;
                }
            }
        }
        return snapshot;
    }

    private Scan scanManifests() {
        Map<String, OwnerDeclaration> owners = new LinkedHashMap<>();
        List<PluginManifest> manifests = new ArrayList<>();
        for (URL resource : manifestResources()) {
            PluginManifest manifest;
            try (InputStream input = resource.openStream()) {
                manifest = manifestLoader.load(input);
            } catch (IOException e) {
                throw new ManifestLoadException("Failed to read plugin manifest " + resource, e);
            }
            OwnerDeclaration owner = OwnerDeclaration.of(manifest);
            if (owners.putIfAbsent(owner.ownerId(), owner) != null) {
                throw new ManifestLoadException("Duplicate plugin id in active manifests: " + owner.ownerId());
            }
            manifests.add(manifest);
        }
        return new Scan(Collections.unmodifiableMap(owners), List.copyOf(manifests));
    }

    /** Active manifests on the classpath and the owners they declare. */
    private record Scan(Map<String, OwnerDeclaration> owners, List<PluginManifest> manifests) {
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
     * @param custodianTables manifest {@code data.outbox} and {@code data.inbox} tables
     */
    record OwnerDeclaration(
            String ownerId, String storageId, boolean declaresPersistence, Set<String> custodianTables) {

        OwnerDeclaration {
            custodianTables = Set.copyOf(custodianTables);
        }

        static OwnerDeclaration of(PluginManifest manifest) {
            PluginManifest.DataSection data = manifest.getData();
            Set<String> custodianTables = new LinkedHashSet<>();
            if (data != null) {
                Stream.of(data.getOutbox(), data.getInbox())
                    .filter(table -> table != null && !table.isBlank())
                    .map(OwnerTenantTables::normalize)
                    .forEach(custodianTables::add);
            }
            return new OwnerDeclaration(
                manifest.getMetadata().getPluginId(),
                data == null ? null : data.getStorageId(),
                declaresPersistence(manifest),
                custodianTables);
        }

        private static boolean declaresPersistence(PluginManifest manifest) {
            if (manifest.getCapabilities() == null) {
                return false;
            }
            return declares(manifest.getCapabilities().getRequired(), PersistenceCapability.class)
                || declares(manifest.getCapabilities().getOptional(), PersistenceCapability.class);
        }

        static boolean declares(List<PluginManifest.CapabilityRef> references, Class<?> capability) {
            if (references == null) {
                return false;
            }
            return references.stream()
                .map(PluginManifest.CapabilityRef::getId)
                .anyMatch(id -> capability.getName().equals(id) || capability.getSimpleName().equals(id));
        }
    }
}
