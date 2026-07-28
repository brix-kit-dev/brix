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
package io.runtime.orchestrator.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.runtime.manifest.loader.PluginManifestLoader;
import io.runtime.manifest.model.PluginManifest;
import io.runtime.manifest.validation.PluginManifestValidator;
import io.runtime.sdk.event.EventReliability;
import io.runtime.sdk.plugin.BrixPlugin;

/**
 * Resolves runtime descriptors from classpath plugin manifests.
 *
 * <p>The active v3.0.10 manifest path is
 * {@value PluginManifestValidator#ACTIVE_MANIFEST_RESOURCE}. Legacy JSON at
 * {@code META-INF/plugin-manifest.json} remains a read-only compatibility input
 * only when no active YAML manifest exists for the same plugin artifact.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public final class ClasspathPluginRuntimeDescriptorResolver
        implements PluginRuntimeManager.PluginRuntimeDescriptorResolver {

    private static final List<String> MANIFEST_RESOURCES = List.of(
        PluginManifestValidator.ACTIVE_MANIFEST_RESOURCE
    );

    private static final List<String> LEGACY_YAML_RESOURCES = List.of(
        "META-INF/plugin-manifest.yaml",
        "META-INF/plugin-manifest.yml"
    );

    private static final List<String> LEGACY_JSON_RESOURCES = List.of(
        "META-INF/plugin-manifest.json"
    );

    private final ClassLoader classLoader;
    private final ObjectMapper jsonMapper;
    private final PluginManifestLoader pluginManifestLoader;

    /**
     * Creates a resolver using the current thread context class loader.
     */
    public ClasspathPluginRuntimeDescriptorResolver() {
        this(Thread.currentThread().getContextClassLoader());
    }

    /**
     * Creates a resolver using an explicit class loader.
     *
     * @param classLoader class loader used to locate classpath manifests
     */
    public ClasspathPluginRuntimeDescriptorResolver(ClassLoader classLoader) {
        this.classLoader = classLoader != null ? classLoader : BrixPlugin.class.getClassLoader();
        this.jsonMapper = new ObjectMapper();
        this.pluginManifestLoader = new PluginManifestLoader();
    }

    /**
     * Resolves the descriptor for a discovered provider.
     *
     * @param plugin discovered plugin provider
     * @return descriptor when a same-artifact manifest exists
     */
    @Override
    public Optional<PluginRuntimeDescriptor> resolve(BrixPlugin plugin) {
        URL codeSource = codeSource(plugin);
        List<URL> active = sameArtifactResources(MANIFEST_RESOURCES, codeSource);
        List<URL> legacyYaml = sameArtifactResources(LEGACY_YAML_RESOURCES, codeSource);
        List<URL> legacyJson = sameArtifactResources(LEGACY_JSON_RESOURCES, codeSource);

        if (active.size() > 1) {
            throw new PluginRuntimeException("Plugin provider " + plugin.getClass().getName()
                + " has multiple active " + PluginManifestValidator.ACTIVE_MANIFEST_RESOURCE + " resources");
        }
        if (!active.isEmpty()) {
            if (!legacyYaml.isEmpty() || !legacyJson.isEmpty()) {
                throw new PluginRuntimeException("Plugin provider " + plugin.getClass().getName()
                    + " declares an active " + PluginManifestValidator.ACTIVE_MANIFEST_RESOURCE
                    + " and legacy manifest resources in the same artifact");
            }
            return Optional.of(loadActiveDescriptor(active.get(0)));
        }

        if (!legacyYaml.isEmpty()) {
            throw new PluginRuntimeException("Plugin provider " + plugin.getClass().getName()
                + " declares plugin manifest YAML outside the required path "
                + PluginManifestValidator.ACTIVE_MANIFEST_RESOURCE);
        }
        if (legacyJson.size() > 1) {
            throw new PluginRuntimeException("Plugin provider " + plugin.getClass().getName()
                + " has multiple legacy META-INF/plugin-manifest.json resources");
        }
        if (!legacyJson.isEmpty()) {
            return Optional.of(loadLegacyJsonDescriptor(legacyJson.get(0)));
        }

        return Optional.empty();
    }

    private List<URL> sameArtifactResources(List<String> resourceNames, URL codeSource) {
        List<URL> matches = new ArrayList<>();
        for (String resourceName : resourceNames) {
            for (URL manifestUrl : resources(resourceName)) {
                if (belongsToCodeSource(manifestUrl, codeSource)) {
                    matches.add(manifestUrl);
                }
            }
        }
        return matches;
    }

    private List<URL> resources(String resourceName) {
        try {
            Enumeration<URL> urls = classLoader.getResources(resourceName);
            List<URL> result = new ArrayList<>();
            while (urls.hasMoreElements()) {
                result.add(urls.nextElement());
            }
            return result;
        } catch (IOException e) {
            throw new PluginRuntimeException("Failed to scan classpath for " + resourceName, e);
        }
    }

    private URL codeSource(BrixPlugin plugin) {
        CodeSource source = plugin.getClass().getProtectionDomain().getCodeSource();
        if (source == null || source.getLocation() == null) {
            throw new PluginRuntimeException("Plugin provider " + plugin.getClass().getName()
                + " has no code source; cannot associate it with a manifest");
        }
        return source.getLocation();
    }

    private boolean belongsToCodeSource(URL manifestUrl, URL codeSource) {
        String manifest = manifestUrl.toExternalForm();
        String source = codeSource.toExternalForm();
        if (manifest.startsWith("jar:")) {
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

    private PluginRuntimeDescriptor loadActiveDescriptor(URL manifestUrl) {
        try (InputStream inputStream = manifestUrl.openStream()) {
            PluginManifest manifest = pluginManifestLoader.load(inputStream);
            PluginRuntimeDescriptor.Builder builder = PluginRuntimeDescriptor.builder(manifest.pluginId())
                .requiredCapabilities(capabilityIds(manifest.getCapabilities() != null
                    ? manifest.getCapabilities().getRequired() : List.of()))
                .optionalCapabilities(capabilityIds(manifest.getCapabilities() != null
                    ? manifest.getCapabilities().getOptional() : List.of()))
                .queryHandlers(contractIds(manifest.getQueries() != null
                    ? manifest.getQueries().getProvides() : List.of()))
                .commandHandlers(contractIds(manifest.getCommands() != null
                    ? manifest.getCommands().getProvides() : List.of()));
            if (manifest.getData() != null) {
                builder.data(
                    manifest.getData().getStorageId(),
                    manifest.getData().getOutbox(),
                    manifest.getData().getInbox());
            }
            if (manifest.getEvents() != null) {
                for (PluginManifest.EventPublish publish : manifest.getEvents().getPublishes()) {
                    builder.eventPublication(publish.getId(), publish.getVersion(),
                        EventReliability.valueOf(publish.getReliability()));
                }
                for (PluginManifest.EventSubscribe subscribe : manifest.getEvents().getSubscribes()) {
                    builder.eventSubscription(
                        subscribe.getSubscriptionId(),
                        subscribe.getEventType(),
                        subscribe.getSchemaRange(),
                        subscribe.getHandlerId(),
                        subscribe.getRetryPolicyRef(),
                        subscribe.getIdempotencyPolicyRef());
                }
            }
            for (PluginManifest.Route route : manifest.getRoutes()) {
                if (route != null && route.getId() != null && !route.getId().isBlank()) {
                    builder.endpoint(route.getId(), route.getMethod(), route.getPath(), route.getAccessPolicy());
                }
            }
            return builder.build();
        } catch (IOException e) {
            throw new PluginRuntimeException("Failed to read plugin manifest " + manifestUrl, e);
        }
    }

    private PluginRuntimeDescriptor loadLegacyJsonDescriptor(URL manifestUrl) {
        try (InputStream inputStream = manifestUrl.openStream()) {
            JsonNode root = jsonMapper.readTree(inputStream);
            String pluginId = pluginId(root);
            if (pluginId == null || pluginId.isBlank()) {
                throw new PluginRuntimeException("Plugin manifest " + manifestUrl
                    + " does not declare pluginId, plugin.id, metadata.id, or metadata.pluginId");
            }
            return PluginRuntimeDescriptor.builder(pluginId)
                .requiredCapabilities(stringList(root.path("capabilities").path("required")))
                .optionalCapabilities(stringList(root.path("capabilities").path("optional")))
                .endpoints(ids(root.path("endpoints")))
                .endpoints(ids(root.path("resources").path("endpoints")))
                .queryHandlers(ids(root.path("queries").path("provides")))
                .queryHandlers(ids(root.path("resources").path("queries")))
                .commandHandlers(ids(root.path("commands").path("provides")))
                .commandHandlers(ids(root.path("resources").path("commands")))
                .eventHandlers(ids(root.path("events").path("subscribes")))
                .eventHandlers(ids(root.path("subscriptions")))
                .tasks(ids(root.path("tasks")))
                .build();
        } catch (IOException e) {
            throw new PluginRuntimeException("Failed to read plugin manifest " + manifestUrl, e);
        }
    }

    private String pluginId(JsonNode root) {
        if (root.hasNonNull("pluginId")) {
            return root.path("pluginId").asText();
        }
        if (root.path("plugin").hasNonNull("id")) {
            return root.path("plugin").path("id").asText();
        }
        if (root.path("metadata").hasNonNull("id")) {
            return root.path("metadata").path("id").asText();
        }
        if (root.path("metadata").hasNonNull("pluginId")) {
            return root.path("metadata").path("pluginId").asText();
        }
        return null;
    }

    private List<String> capabilityIds(List<PluginManifest.CapabilityRef> refs) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (PluginManifest.CapabilityRef ref : refs) {
            if (ref != null && ref.getId() != null && !ref.getId().isBlank()) {
                ids.add(ref.getId());
            }
        }
        return ids;
    }

    private List<String> contractIds(List<PluginManifest.ContractRef> refs) {
        if (refs == null || refs.isEmpty()) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        for (PluginManifest.ContractRef ref : refs) {
            if (ref != null && ref.getId() != null && !ref.getId().isBlank()) {
                ids.add(ref.getId());
            }
        }
        return ids;
    }

    private List<String> stringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText());
            }
        }
        return values;
    }

    private List<String> ids(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText());
            } else if (item.isObject() && item.hasNonNull("id") && !item.path("id").asText().isBlank()) {
                values.add(item.path("id").asText());
            } else if (item.isObject() && item.hasNonNull("name") && !item.path("name").asText().isBlank()) {
                values.add(item.path("name").asText());
            }
        }
        return values;
    }
}
