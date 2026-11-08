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
import java.util.Map;
import java.util.Optional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import io.runtime.manifest.loader.PluginManifestLoader;
import io.runtime.manifest.model.PluginManifest;
import io.runtime.manifest.validation.PluginManifestValidator;
import io.runtime.orchestrator.endpoint.EndpointAccessPolicy;
import io.runtime.orchestrator.endpoint.TenantGrantTable;
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
        this.jsonMapper = JsonMapper.builder().configureForJackson2().build();
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

    private PluginRuntimeDescriptor loadActiveDescriptor(URL manifestUrl) {
        try (InputStream inputStream = manifestUrl.openStream()) {
            PluginManifest manifest = pluginManifestLoader.load(inputStream);
            PluginRuntimeDescriptor.Builder builder = PluginRuntimeDescriptor.builder(manifest.pluginId())
                .version(manifest.version())
                .requiredCapabilities(capabilityIds(manifest.getCapabilities() != null
                    ? manifest.getCapabilities().getRequired() : List.of()))
                .optionalCapabilities(capabilityIds(manifest.getCapabilities() != null
                    ? manifest.getCapabilities().getOptional() : List.of()));
            if (manifest.getQueries() != null) {
                for (PluginManifest.ContractRef provided : manifest.getQueries().getProvides()) {
                    builder.providedQuery(new PluginRuntimeDescriptor.ProvidedQuery(provided.getQueryType(),
                        schemaRef(provided.getRequest()), schemaRef(provided.getResponse()), provided.getHandlerId(),
                        provided.getScopePolicyRef(), provided.getAccessPolicyRef(), provided.getTimeoutPolicyRef(),
                        provided.getConcurrencyPolicyRef()));
                }
                for (PluginManifest.ContractRef consumed : manifest.getQueries().getConsumes()) {
                    builder.consumedQuery(new PluginRuntimeDescriptor.ConsumedQuery(consumed.getQueryType(),
                        consumed.getVersionRange(), !Boolean.FALSE.equals(consumed.getRequired()),
                        consumed.getTimeoutPolicyRef(), consumed.getRetryPolicyRef()));
                }
            }
            if (manifest.getCommands() != null) {
                for (PluginManifest.ContractRef provided : manifest.getCommands().getProvides()) {
                    builder.providedCommand(new PluginRuntimeDescriptor.ProvidedCommand(provided.getCommandType(),
                        provided.getHandlerId(), schemaVersion(provided.getRequest()), provided.getRetryPolicyRef(),
                        provided.getTimeoutPolicyRef(), provided.getConcurrencyPolicyRef(),
                        provided.getScopePolicyRef(), provided.getAccessPolicyRef(),
                        provided.getIdempotencyPolicyRef()));
                }
                for (PluginManifest.ContractRef consumed : manifest.getCommands().getConsumes()) {
                    builder.consumedCommand(new PluginRuntimeDescriptor.ConsumedCommand(consumed.getCommandType(),
                        consumed.getVersionRange(), !Boolean.FALSE.equals(consumed.getRequired()),
                        consumed.getTimeoutPolicyRef(), consumed.getRetryPolicyRef(),
                        consumed.getIdempotencyPolicyRef()));
                }
            }
            if (manifest.getTasks() != null) {
                for (PluginManifest.Task task : manifest.getTasks().getProvides()) {
                    builder.providedTask(new PluginRuntimeDescriptor.ProvidedTask(task.getTaskId(), task.getHandlerId(),
                        task.getScopePolicyRef(), task.getExecutionIdentityPolicyRef(), task.getTriggerPolicyRef(),
                        task.getTimeoutPolicyRef(), task.getConcurrencyPolicyRef(), task.getRetryPolicyRef(),
                        task.getIdempotencyPolicyRef(), task.getTransaction()));
                }
            }
            if (manifest.getData() != null) {
                builder.data(
                    manifest.getData().getStorageId(),
                    manifest.getData().getOutbox(),
                    manifest.getData().getInbox());
            }
            if (manifest.getEvents() != null) {
                for (PluginManifest.EventPublish publish : manifest.getEvents().getPublishes()) {
                    builder.eventPublication(publish.getEventType(), publish.getVersion(),
                        EventReliability.valueOf(publish.getReliability()));
                    builder.publicationSchema(publish.getEventType(), publish.getSchemaRef());
                }
                for (PluginManifest.EventSubscribe subscribe : manifest.getEvents().getSubscribes()) {
                    builder.eventSubscription(
                        subscribe.getSubscriptionId(),
                        subscribe.getEventType(),
                        subscribe.getSchemaRange(),
                        subscribe.getHandlerId(),
                        subscribe.getRetryPolicyRef(),
                        subscribe.getIdempotencyPolicyRef(),
                        subscribe.getScopePolicyRef());
                    builder.subscriptionPolicies(subscribe.getHandlerId(), subscribe.getTimeoutPolicyRef(),
                        subscribe.getConcurrencyPolicyRef(), subscribe.getPublisherPolicyRef());
                }
            }
            if (manifest.getInternalContracts() != null) {
                for (PluginManifest.ProvidedInternalContract contract
                        : manifest.getInternalContracts().getProvides()) {
                    builder.providedInternalContract(
                        contract.getContractId(),
                        contract.getContractType(),
                        contract.getContractVersion(),
                        contract.getProviderId(),
                        contract.getOwner(),
                        contract.getReplayableWrites());
                }
            }
            for (PluginManifest.Route route : manifest.endpointDeclarations()) {
                if (route != null && route.getEndpointId() != null && !route.getEndpointId().isBlank()) {
                    builder.endpoint(
                        route.getEndpointId(),
                        route.getMethod(),
                        route.getPath(),
                        accessPolicy(manifest.pluginId(), route),
                        contract(manifest.pluginId(), route));
                    builder.endpointHandler(route.getHandlerId(), route.getEndpointId());
                }
            }
            for (PluginManifest.ExternalService service : manifest.getExternalServices()) {
                if (service != null) {
                    builder.externalService(externalService(manifest.pluginId(), service));
                }
            }
            for (PluginManifest.Permission permission : manifest.getPermissions()) {
                if (permission != null && permission.getId() != null && !permission.getId().isBlank()) {
                    builder.permission(permission.getId(), grants(permission));
                }
            }
            return builder.build();
        } catch (IOException e) {
            throw new PluginRuntimeException("Failed to read plugin manifest " + manifestUrl, e);
        }
    }

    private static io.runtime.orchestrator.endpoint.EndpointContract contract(String pluginId,
            PluginManifest.Route route) {
        Object timeout = route.getTimeout() == null ? null : route.getTimeout().get("policyRef");
        try {
            return new io.runtime.orchestrator.endpoint.EndpointContract(schemaRef(route.getRequest()),
                schemaRef(route.getResponse()), status(route.getResponse()),
                timeout instanceof String policy ? policy : null, replay(route));
        } catch (IllegalArgumentException e) {
            throw new PluginRuntimeException("Plugin '" + pluginId + "' endpoint '" + route.getEndpointId()
                + "' declares an invalid contract: " + e.getMessage(), e);
        }
    }

    /** Root BRX-RS-MANIFEST-018; standard-link plan point 10a: how the runtime replays the endpoint's results. */
    private static io.runtime.orchestrator.endpoint.EndpointContract.Replay replay(PluginManifest.Route route) {
        if (!route.replaysResults()) {
            return null;
        }
        Object retention = route.getIdempotency().get("retentionPolicyRef");
        return new io.runtime.orchestrator.endpoint.EndpointContract.Replay(route.getHandlerId(),
            io.runtime.orchestrator.endpoint.EndpointPolicies.retention(retention instanceof String policy ? policy : null));
    }

    /** Root BRX-RS-MANIFEST-026; standard-link plan L8.2: a policy the runtime does not know stops the plugin. */
    private static io.runtime.orchestrator.external.ExternalServiceDeclaration externalService(String pluginId,
            PluginManifest.ExternalService service) {
        try {
            return new io.runtime.orchestrator.external.ExternalServiceDeclaration(service.getId(),
                service.getBaseUrlRef(), service.getAuthPolicyRef(), service.getTimeoutPolicyRef(),
                service.getRetryPolicyRef(), service.getConcurrencyPolicyRef());
        } catch (IllegalArgumentException e) {
            throw new PluginRuntimeException("Plugin '" + pluginId + "' declares an invalid external service: "
                + e.getMessage(), e);
        }
    }

    /** The status a successful endpoint answers with (standard-link plan L7.3): 200 unless it names another. */
    private static int status(Map<String, Object> response) {
        Object status = response == null ? null : response.get("status");
        return status instanceof Number number ? number.intValue() : 200;
    }

    private static EndpointAccessPolicy accessPolicy(String pluginId, PluginManifest.Route route) {
        EndpointAccessPolicy policy;
        try {
            policy = EndpointAccessPolicy.fromDeclaration(
                route.getAuthentication(),
                route.getAuthorization(),
                route.getTenantContext());
        } catch (IllegalArgumentException e) {
            throw new PluginRuntimeException("Plugin '" + pluginId + "' endpoint '" + route.getEndpointId()
                + "' declares an invalid access policy: " + e.getMessage(), e);
        }
        if (policy.tokenPolicy() == EndpointAccessPolicy.TokenPolicy.BOOTSTRAP_SETUP) {
            // super-admin 4.0.1 SA-7: the bootstrap session is the platform's, for its bootstrap entries only.
            throw new PluginRuntimeException("Plugin '" + pluginId + "' endpoint '" + route.getEndpointId()
                + "' declares authentication.policyRef 'bootstrap-setup', which only a platform operational module "
                + "may declare");
        }
        return policy;
    }

    private static List<TenantGrantTable.Grant> grants(PluginManifest.Permission permission) {
        List<TenantGrantTable.Grant> grants = new ArrayList<>();
        for (PluginManifest.Grant grant : permission.getGrants()) {
            if (grant != null) {
                grants.add(new TenantGrantTable.Grant(grant.getRole(), grant.getScope()));
            }
        }
        return grants;
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
        } catch (IOException | JacksonException e) {
            throw new PluginRuntimeException("Failed to read plugin manifest " + manifestUrl, e);
        }
    }

    private String pluginId(JsonNode root) {
        if (root.hasNonNull("pluginId")) {
            return root.path("pluginId").asString();
        }
        if (root.path("plugin").hasNonNull("id")) {
            return root.path("plugin").path("id").asString();
        }
        if (root.path("metadata").hasNonNull("id")) {
            return root.path("metadata").path("id").asString();
        }
        if (root.path("metadata").hasNonNull("pluginId")) {
            return root.path("metadata").path("pluginId").asString();
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

    /** The version a {@code brix://schemas/.../<version>} reference ends with. */
    private static String schemaRef(Map<String, Object> schema) {
        Object ref = schema == null ? null : schema.get("schemaRef");
        return ref instanceof String text ? text : "";
    }

    private static String schemaVersion(Map<String, Object> schema) {
        Object ref = schema == null ? null : schema.get("schemaRef");
        if (!(ref instanceof String text) || text.lastIndexOf('/') < 0) {
            return "";
        }
        return text.substring(text.lastIndexOf('/') + 1);
    }

    private List<String> stringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isString() && !item.asString().isBlank()) {
                values.add(item.asString());
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
            if (item.isString() && !item.asString().isBlank()) {
                values.add(item.asString());
            } else if (item.isObject() && item.hasNonNull("id") && !item.path("id").asString().isBlank()) {
                values.add(item.path("id").asString());
            } else if (item.isObject() && item.hasNonNull("name") && !item.path("name").asString().isBlank()) {
                values.add(item.path("name").asString());
            }
        }
        return values;
    }
}
