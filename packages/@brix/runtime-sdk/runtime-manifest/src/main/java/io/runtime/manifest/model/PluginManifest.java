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
package io.runtime.manifest.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * v3.0.10 plugin manifest model.
 *
 * <p>The plugin manifest is the declarative source of truth for backend plugin
 * metadata, runtime compatibility, capability dependencies, integration
 * contracts, data ownership, permissions, configuration, and UI entry points.
 * Java plugin SPI implementations must not duplicate these values.</p>
 *
 * @author Runtime SDK Team
 * @since 3.0.10
 */
public class PluginManifest {

    private String apiVersion;
    private String kind;
    private Metadata metadata;
    private Runtime runtime;
    private List<Module> modules = new ArrayList<>();
    private List<Route> routes = new ArrayList<>();
    private CapabilitySection capabilities;
    private ContractSection queries;
    private ContractSection commands;
    private EventSection events;
    private List<ExternalService> externalServices = new ArrayList<>();
    private DataSection data;
    private List<Permission> permissions = new ArrayList<>();
    private List<ConfigEntry> config = new ArrayList<>();
    private UiSection ui;

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public Runtime getRuntime() {
        return runtime;
    }

    public void setRuntime(Runtime runtime) {
        this.runtime = runtime;
    }

    public List<Module> getModules() {
        return modules;
    }

    public void setModules(List<Module> modules) {
        this.modules = modules != null ? modules : new ArrayList<>();
    }

    public List<Route> getRoutes() {
        return routes;
    }

    public void setRoutes(List<Route> routes) {
        this.routes = routes != null ? routes : new ArrayList<>();
    }

    public CapabilitySection getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(CapabilitySection capabilities) {
        this.capabilities = capabilities;
    }

    public ContractSection getQueries() {
        return queries;
    }

    public void setQueries(ContractSection queries) {
        this.queries = queries;
    }

    public ContractSection getCommands() {
        return commands;
    }

    public void setCommands(ContractSection commands) {
        this.commands = commands;
    }

    public EventSection getEvents() {
        return events;
    }

    public void setEvents(EventSection events) {
        this.events = events;
    }

    public List<ExternalService> getExternalServices() {
        return externalServices;
    }

    public void setExternalServices(List<ExternalService> externalServices) {
        this.externalServices = externalServices != null ? externalServices : new ArrayList<>();
    }

    public DataSection getData() {
        return data;
    }

    public void setData(DataSection data) {
        this.data = data;
    }

    public List<Permission> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<Permission> permissions) {
        this.permissions = permissions != null ? permissions : new ArrayList<>();
    }

    public List<ConfigEntry> getConfig() {
        return config;
    }

    public void setConfig(List<ConfigEntry> config) {
        this.config = config != null ? config : new ArrayList<>();
    }

    public UiSection getUi() {
        return ui;
    }

    public void setUi(UiSection ui) {
        this.ui = ui;
    }

    public String pluginId() {
        return metadata != null ? metadata.getPluginId() : null;
    }

    public String version() {
        return metadata != null ? metadata.getVersion() : null;
    }

    /**
     * Manifest metadata.
     */
    public static class Metadata {
        private String pluginId;
        private String name;
        private String version;
        private String displayName;
        private String vendor;
        private String license;
        private String attestationIdentity;

        public String getPluginId() {
            return pluginId;
        }

        public void setPluginId(String pluginId) {
            this.pluginId = pluginId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getVendor() {
            return vendor;
        }

        public void setVendor(String vendor) {
            this.vendor = vendor;
        }

        public String getLicense() {
            return license;
        }

        public void setLicense(String license) {
            this.license = license;
        }

        public String getAttestationIdentity() {
            return attestationIdentity;
        }

        public void setAttestationIdentity(String attestationIdentity) {
            this.attestationIdentity = attestationIdentity;
        }
    }

    /**
     * Runtime compatibility declaration.
     */
    public static class Runtime {
        private String compiledAgainst;
        private String supportedRange;

        public String getCompiledAgainst() {
            return compiledAgainst;
        }

        public void setCompiledAgainst(String compiledAgainst) {
            this.compiledAgainst = compiledAgainst;
        }

        public String getSupportedRange() {
            return supportedRange;
        }

        public void setSupportedRange(String supportedRange) {
            this.supportedRange = supportedRange;
        }
    }

    /**
     * Artifact module declaration.
     */
    public static class Module {
        private String artifactId;
        private String groupId;
        private String version;
        private String moduleKind;

        public String getArtifactId() {
            return artifactId;
        }

        public void setArtifactId(String artifactId) {
            this.artifactId = artifactId;
        }

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getModuleKind() {
            return moduleKind;
        }

        public void setModuleKind(String moduleKind) {
            this.moduleKind = moduleKind;
        }
    }

    /**
     * Public route declaration.
     */
    public static class Route {
        private String id;
        private String path;
        private String method;
        private String handler;
        private String accessPolicy;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public String getHandler() {
            return handler;
        }

        public void setHandler(String handler) {
            this.handler = handler;
        }

        public String getAccessPolicy() {
            return accessPolicy;
        }

        public void setAccessPolicy(String accessPolicy) {
            this.accessPolicy = accessPolicy;
        }
    }

    /**
     * Capability dependencies.
     */
    public static class CapabilitySection {
        private List<CapabilityRef> required = new ArrayList<>();
        private List<CapabilityRef> optional = new ArrayList<>();

        public List<CapabilityRef> getRequired() {
            return required;
        }

        public void setRequired(List<CapabilityRef> required) {
            this.required = required != null ? required : new ArrayList<>();
        }

        public List<CapabilityRef> getOptional() {
            return optional;
        }

        public void setOptional(List<CapabilityRef> optional) {
            this.optional = optional != null ? optional : new ArrayList<>();
        }
    }

    /**
     * Capability reference.
     */
    public static class CapabilityRef {
        private String id;
        private String version;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }

    /**
     * Query or command contract declarations.
     */
    public static class ContractSection {
        private List<ContractRef> provides = new ArrayList<>();
        private List<ContractRef> consumes = new ArrayList<>();

        public List<ContractRef> getProvides() {
            return provides;
        }

        public void setProvides(List<ContractRef> provides) {
            this.provides = provides != null ? provides : new ArrayList<>();
        }

        public List<ContractRef> getConsumes() {
            return consumes;
        }

        public void setConsumes(List<ContractRef> consumes) {
            this.consumes = consumes != null ? consumes : new ArrayList<>();
        }
    }

    /**
     * Query, command, or event contract reference.
     */
    public static class ContractRef {
        private String id;
        private String schema;
        private String version;
        private String handler;
        private String availability;
        private String idempotency;
        private String authorization;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getSchema() {
            return schema;
        }

        public void setSchema(String schema) {
            this.schema = schema;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getHandler() {
            return handler;
        }

        public void setHandler(String handler) {
            this.handler = handler;
        }

        public String getAvailability() {
            return availability;
        }

        public void setAvailability(String availability) {
            this.availability = availability;
        }

        public String getIdempotency() {
            return idempotency;
        }

        public void setIdempotency(String idempotency) {
            this.idempotency = idempotency;
        }

        public String getAuthorization() {
            return authorization;
        }

        public void setAuthorization(String authorization) {
            this.authorization = authorization;
        }
    }

    /**
     * Event declarations.
     */
    public static class EventSection {
        private List<ContractRef> publishes = new ArrayList<>();
        private List<ContractRef> subscribes = new ArrayList<>();

        public List<ContractRef> getPublishes() {
            return publishes;
        }

        public void setPublishes(List<ContractRef> publishes) {
            this.publishes = publishes != null ? publishes : new ArrayList<>();
        }

        public List<ContractRef> getSubscribes() {
            return subscribes;
        }

        public void setSubscribes(List<ContractRef> subscribes) {
            this.subscribes = subscribes != null ? subscribes : new ArrayList<>();
        }
    }

    /**
     * External HTTP service allowlist entry.
     */
    public static class ExternalService {
        private String id;
        private String baseUrlRef;
        private String authPolicyRef;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getBaseUrlRef() {
            return baseUrlRef;
        }

        public void setBaseUrlRef(String baseUrlRef) {
            this.baseUrlRef = baseUrlRef;
        }

        public String getAuthPolicyRef() {
            return authPolicyRef;
        }

        public void setAuthPolicyRef(String authPolicyRef) {
            this.authPolicyRef = authPolicyRef;
        }
    }

    /**
     * Plugin data ownership declaration.
     */
    public static class DataSection {
        private String storageId;
        private String schema;
        private String migrationLocation;
        private String outbox;
        private String inbox;

        public String getStorageId() {
            return storageId;
        }

        public void setStorageId(String storageId) {
            this.storageId = storageId;
        }

        public String getSchema() {
            return schema;
        }

        public void setSchema(String schema) {
            this.schema = schema;
        }

        public String getMigrationLocation() {
            return migrationLocation;
        }

        public void setMigrationLocation(String migrationLocation) {
            this.migrationLocation = migrationLocation;
        }

        public String getOutbox() {
            return outbox;
        }

        public void setOutbox(String outbox) {
            this.outbox = outbox;
        }

        public String getInbox() {
            return inbox;
        }

        public void setInbox(String inbox) {
            this.inbox = inbox;
        }
    }

    /**
     * Permission catalog entry.
     */
    public static class Permission {
        private String id;
        private String name;
        private String description;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    /**
     * Configuration key declaration.
     */
    public static class ConfigEntry {
        private String key;
        private String type;
        private String description;
        @JsonProperty("default")
        private Object defaultValue;
        private String sensitivity;

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        @JsonProperty("default")
        public Object getDefaultValue() {
            return defaultValue;
        }

        @JsonProperty("default")
        public void setDefaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
        }

        public String getSensitivity() {
            return sensitivity;
        }

        public void setSensitivity(String sensitivity) {
            this.sensitivity = sensitivity;
        }
    }

    /**
     * UI entry declaration.
     */
    public static class UiSection {
        private UiPlatform web;
        private UiPlatform mobile;

        public UiPlatform getWeb() {
            return web;
        }

        public void setWeb(UiPlatform web) {
            this.web = web;
        }

        public UiPlatform getMobile() {
            return mobile;
        }

        public void setMobile(UiPlatform mobile) {
            this.mobile = mobile;
        }
    }

    /**
     * Single UI platform declaration.
     */
    public static class UiPlatform {
        private boolean enabled;
        private String manifestUrl;
        private String runtimeRange;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getManifestUrl() {
            return manifestUrl;
        }

        public void setManifestUrl(String manifestUrl) {
            this.manifestUrl = manifestUrl;
        }

        public String getRuntimeRange() {
            return runtimeRange;
        }

        public void setRuntimeRange(String runtimeRange) {
            this.runtimeRange = runtimeRange;
        }
    }
}
