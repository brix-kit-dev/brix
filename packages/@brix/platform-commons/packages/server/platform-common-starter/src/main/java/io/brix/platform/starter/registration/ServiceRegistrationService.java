/*
 * Copyright 2026 Brix Platform Authors
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
package io.brix.platform.starter.registration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import io.brix.platform.starter.config.PlatformApiProperties;
import io.brix.platform.starter.config.ServiceProperties;
import io.brix.platform.starter.header.PlatformHeaders;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * v2.1 Service Registration Service
 * 
 * <p>Responsible for registering service information with the host Plugin Engine at startup</p>
 * 
 * <p>Registration Flow:</p>
 * <ol>
 *   <li>Triggered after application startup completes</li>
 *   <li>Calls RouteScanner to scan all routes</li>
 *   <li>Builds registration request in Plugin Engine format</li>
 *   <li>Sends registration request to host via HTTP</li>
 *   <li>Retries on failure according to configuration</li>
 * </ol>
 * 
 * <p>Registration Endpoint (Plugin Engine compatible):</p>
 * <ul>
 *   <li>Register: POST {baseUrl}/api/plugin-engine/register</li>
 * </ul>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 */
@Service
public class ServiceRegistrationService implements ApplicationListener<ApplicationReadyEvent> {
    
    private static final Logger log = LoggerFactory.getLogger(ServiceRegistrationService.class);
    
    /** Service configuration */
    private final ServiceProperties serviceProperties;
    
    /** API configuration */
    private final PlatformApiProperties apiProperties;
    
    /** Route scanner */
    private final RouteScanner routeScanner;
    
    /** Plugin manifest scanner */
    private final PluginManifestScanner manifestScanner;
    
    /** HTTP client */
    private final WebClient webClient;
    
    /** Environment configuration */
    private final Environment environment;
    
    /** JSON serializer */
    private final ObjectMapper objectMapper;
    
    /** Service instance ID */
    private final String instanceId;
    
    /** Registration status */
    private volatile boolean registered = false;
    
    /** 
     * Registration endpoint path - Plugin Engine compatible 
     * See PluginRegistrationController using /api/plugin-engine/register
     */
    private static final String REGISTRY_PATH = "/api/plugin-engine/register";
    
    public ServiceRegistrationService(ServiceProperties serviceProperties,
                                      PlatformApiProperties apiProperties,
                                      RouteScanner routeScanner,
                                      PluginManifestScanner manifestScanner,
                                      Environment environment,
                                      ObjectMapper objectMapper) {
        this.serviceProperties = serviceProperties;
        this.apiProperties = apiProperties;
        this.routeScanner = routeScanner;
        this.manifestScanner = manifestScanner;
        this.environment = environment;
        this.objectMapper = objectMapper;
        
        // Build WebClient
        this.webClient = WebClient.builder()
            .baseUrl(serviceProperties.getBaseUrl())
            .build();
        
        // Generate instance ID
        this.instanceId = generateInstanceId();
        
        log.info("[ServiceRegistration] Initialization complete, instance ID: {}", instanceId);
    }
    
    /**
     * Automatically register after application startup
     * 
     * @param event Application ready event
     */
    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // Check if registration is enabled
        if (!serviceProperties.isRegistrationEnabled()) {
            log.info("[ServiceRegistration] Service registration is disabled");
            return;
        }
        
        log.info("[ServiceRegistration] Application startup complete, starting registration with host...");
        
        // Async registration to avoid blocking startup
        register()
            .subscribe(
                success -> {
                    if (success) {
                        registered = true;
                        log.info("[ServiceRegistration] Service registration successful");
                    } else {
                        log.warn("[ServiceRegistration] Service registration failed, but application will continue running");
                    }
                },
                error -> log.error("[ServiceRegistration] Service registration exception: {}", error.getMessage())
            );
    }
    
    /**
     * Deregister on application shutdown
     * 
     * @param event Context closed event
     */
    @EventListener(ContextClosedEvent.class)
    public void onApplicationShutdown(ContextClosedEvent event) {
        if (!registered) {
            return;
        }
        
        log.info("[ServiceRegistration] Application shutting down, starting service deregistration...");
        
        // Synchronous deregistration to ensure completion before shutdown
        try {
            deregister().block(Duration.ofSeconds(5));
            log.info("[ServiceRegistration] Service deregistration successful");
        } catch (Exception e) {
            log.warn("[ServiceRegistration] Service deregistration failed: {}", e.getMessage());
        }
    }
    
    /**
     * Register service
     * 
     * <p>Uses Plugin Engine compatible API format</p>
     * <p>Uses PlatformHeaders constants for unified header definitions</p>
     * 
     * @return Registration result
     */
    public Mono<Boolean> register() {
        // 1. Scan routes
        List<RouteInfo> routes = routeScanner.scanRoutes();
        log.info("[ServiceRegistration] Scanned {} routes", routes.size());
        
        // 2. Build Plugin Engine compatible registration request
        Map<String, Object> request = buildPluginEngineRequest(routes);
        
        // 3. Send registration request (using PlatformHeaders constants)
        WebClient.RequestBodySpec requestSpec = webClient.post()
            .uri(REGISTRY_PATH)
            .contentType(MediaType.APPLICATION_JSON)
            .header(PlatformHeaders.TENANT_ID, "default");  // Plugin Engine requires tenant ID
        
        // Add API Key authentication headers (if configured) - using PlatformHeaders constants
        if (StringUtils.hasText(serviceProperties.getApiKey()) 
            && StringUtils.hasText(serviceProperties.getApiSecret())) {
            requestSpec = (WebClient.RequestBodySpec) requestSpec
                .header(PlatformHeaders.API_KEY, serviceProperties.getApiKey())
                .header(PlatformHeaders.API_SECRET, serviceProperties.getApiSecret());
            log.debug("[ServiceRegistration] Added API Key authentication");
        }
        
        return requestSpec
            .bodyValue(request)
            .exchangeToMono(response -> {
                if (response.statusCode().is2xxSuccessful()) {
                    log.info("[ServiceRegistration] Registration successful");
                    return Mono.just(true);
                } else {
                    return response.bodyToMono(String.class)
                        .doOnNext(body -> log.error("[ServiceRegistration] Registration failed - status: {}, response: {}", 
                            response.statusCode().value(), body))
                        .thenReturn(false);
                }
            })
            .retryWhen(Retry.backoff(
                serviceProperties.getRegistrationRetryCount(),
                serviceProperties.getRegistrationRetryInterval()
            ).doBeforeRetry(signal -> 
                log.warn("[ServiceRegistration] Registration failed, retry attempt {}", signal.totalRetries() + 1)
            ))
            .onErrorResume(error -> {
                log.error("[ServiceRegistration] Registration failed: {}", error.getMessage());
                return Mono.just(false);
            });
    }
    
    /**
     * Deregister service
     * 
     * <p>Plugin Engine does not support explicit deregistration, relies on heartbeat timeout for auto-cleanup</p>
     * 
     * @return Deregistration result (always returns true)
     */
    public Mono<Boolean> deregister() {
        log.info("[ServiceRegistration] Service will be automatically deregistered on heartbeat timeout");
        return Mono.just(true);
    }
    
    /**
     * Build Plugin Engine compatible registration request
     * 
     * <p>Format reference: Plugin Engine PluginRegistration model</p>
     * <pre>
     * {
     *   "name": "Service name",
     *   "version": "Version",
     *   "displayName": "Display name",
     *   "serviceUrl": "Service URL",
     *   "apis": { "basePath": "/api/xxx", "endpoints": [...] },
     *   "events": null,
     *   "ui": null
     * }
     * </pre>
     * 
     * @param routes Route list
     * @return Plugin Engine compatible registration request
     */
    private Map<String, Object> buildPluginEngineRequest(List<RouteInfo> routes) {
        Map<String, Object> request = new HashMap<>();
        
        // Basic information
        request.put("name", serviceProperties.getName());
        request.put("version", getServiceVersion());
        // v2.1.2 fix: Prefer displayName from plugin manifest
        request.put("displayName", getPluginDisplayName());
        request.put("serviceUrl", getServiceUrl());
        
        // API contract - using Plugin Engine expected format
        // basePath is required, endpoints is optional
        String basePath = serviceProperties.getApiBasePath();
        if (!StringUtils.hasText(basePath)) {
            // If apiBasePath is not configured, build default path from service name
            // Example: brix-service-case -> /api/case
            String serviceName = serviceProperties.getName();
            if (serviceName != null && serviceName.startsWith("brix-service-")) {
                basePath = "/api/" + serviceName.substring("brix-service-".length());
            } else {
                basePath = "/api/" + (serviceName != null ? serviceName : "unknown");
            }
            log.info("[ServiceRegistration] Using default API basePath: {}", basePath);
        }
        
        Map<String, Object> apis = new HashMap<>();
        apis.put("basePath", basePath);
        
        // Convert routes to endpoints format
        List<Map<String, Object>> endpoints = new ArrayList<>();
        for (RouteInfo route : routes) {
            // Generate one endpoint for each HTTP method
            for (String method : route.methods()) {
                Map<String, Object> endpoint = new HashMap<>();
                endpoint.put("path", route.path());
                endpoint.put("method", method);
                endpoint.put("summary", route.description());
                endpoint.put("tags", route.tags());
                endpoints.add(endpoint);
            }
        }
        apis.put("endpoints", endpoints);
        request.put("apis", apis);
        
        // Event contract not used yet
        request.put("events", null);
        
        // UI contract - aggregated from plugin manifests
        Map<String, Object> aggregatedUi = manifestScanner.aggregateUiContracts();
        request.put("ui", aggregatedUi);
        
        if (aggregatedUi != null) {
            log.info("[ServiceRegistration] UI contract aggregated from plugin manifests");
        }
        
        return request;
    }
    
    /**
     * Build registration request (kept for compatibility)
     * 
     * @param routes Route list
     * @return Registration request
     * @deprecated Use {@link #buildPluginEngineRequest(List)} instead
     */
    @Deprecated
    private ServiceRegistrationRequest buildRegistrationRequest(List<RouteInfo> routes) {
        return new ServiceRegistrationRequest(
            serviceProperties.getName(),
            instanceId,
            getServiceUrl(),
            getServiceVersion(),
            getServiceDescription(),
            routes,
            scanPlugins(),
            buildMetadata(),
            Instant.now()
        );
    }
    
    /**
     * Generate service instance ID
     * 
     * <p>Format: {serviceName}-{hostname}-{port}-{uuid}</p>
     */
    private String generateInstanceId() {
        String hostname = getHostname();
        String port = environment.getProperty("server.port", "8080");
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        
        return String.format("%s-%s-%s-%s", 
            serviceProperties.getName(), hostname, port, uuid);
    }
    
    /**
     * Get hostname
     */
    private String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
    
    /**
     * Get service URL
     */
    private String getServiceUrl() {
        String port = environment.getProperty("server.port", "8080");
        String contextPath = environment.getProperty("server.servlet.context-path", "");
        
        // Prefer configured URL
        String configuredUrl = environment.getProperty("brix.service.url");
        if (configuredUrl != null && !configuredUrl.isEmpty()) {
            return configuredUrl;
        }
        
        // Otherwise use local address
        String host = getHostAddress();
        return String.format("http://%s:%s%s", host, port, contextPath);
    }
    
    /**
     * Get host IP address
     */
    private String getHostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            return "localhost";
        }
    }
    
    /**
     * Get service version
     */
    private String getServiceVersion() {
        // Get from Maven build info
        String version = environment.getProperty("info.app.version");
        if (version != null) {
            return version;
        }
        
        // Get from package info
        Package pkg = getClass().getPackage();
        if (pkg != null && pkg.getImplementationVersion() != null) {
            return pkg.getImplementationVersion();
        }
        
        return "0.1.0-SNAPSHOT";
    }
    
    /**
     * Get service description
     */
    private String getServiceDescription() {
        return environment.getProperty("info.app.description", 
            "Brix Service: " + serviceProperties.getName());
    }
    
    /**
     * Get plugin display name
     * 
     * <p>v2.1.2 fix: Prefer displayName from plugin manifest
     * This way parent menu will show Chinese name (like "User Management") instead of "Brix Service: xxx"</p>
     * 
     * <p>Priority:</p>
     * <ol>
     *   <li>displayName from plugin manifest</li>
     *   <li>Environment config info.app.description</li>
     *   <li>Default "Brix Service: {name}"</li>
     * </ol>
     * 
     * @return Plugin display name
     */
    private String getPluginDisplayName() {
        // 1. Prefer displayName from plugin manifest
        List<PluginManifest> manifests = manifestScanner.scanManifests();
        if (!manifests.isEmpty()) {
            // If multiple manifests exist, try to find one matching the service name
            String serviceName = serviceProperties.getName();
            for (PluginManifest manifest : manifests) {
                // Check if manifest name is related to service name
                // Example: service=brix-service-user, plugin=plugin-user
                String manifestName = manifest.getName();
                if (manifestName != null && manifest.getDisplayName() != null) {
                    // Case where service name contains plugin name: brix-service-user contains user
                    // Or plugin name corresponds to service: plugin-user -> service-user
                    String pluginCore = manifestName.replace("plugin-", "");
                    if (serviceName != null && 
                        (serviceName.contains(pluginCore) || serviceName.endsWith("-" + pluginCore))) {
                        log.debug("[ServiceRegistration] Using matching plugin {} displayName: {}", 
                            manifestName, manifest.getDisplayName());
                        return manifest.getDisplayName();
                    }
                }
            }
            
            // If no exact match, use the first manifest with displayName
            for (PluginManifest manifest : manifests) {
                if (manifest.getDisplayName() != null && !manifest.getDisplayName().isEmpty()) {
                    log.debug("[ServiceRegistration] Using plugin {} displayName: {}", 
                        manifest.getName(), manifest.getDisplayName());
                    return manifest.getDisplayName();
                }
            }
        }
        
        // 2. Fallback to config file
        String configuredName = environment.getProperty("info.app.description");
        if (configuredName != null && !configuredName.isEmpty() 
            && !configuredName.startsWith("Brix Service:")) {
            return configuredName;
        }
        
        // 3. Final fallback to default
        return "Brix Service: " + serviceProperties.getName();
    }

    /**
     * Scan assembled plugins
     * 
     * <p>Identify plugins by scanning plugin-xxx-core.jar in classpath</p>
     */
    private List<PluginInfo> scanPlugins() {
        // TODO: Implement plugin scanning logic
        // Can scan META-INF/plugin.properties or specific marker interfaces to identify plugins
        return Collections.emptyList();
    }
    
    /**
     * Build service metadata
     */
    private Map<String, Object> buildMetadata() {
        Map<String, Object> metadata = new HashMap<>();
        
        // Runtime environment
        String[] profiles = environment.getActiveProfiles();
        if (profiles.length > 0) {
            metadata.put("profiles", Arrays.asList(profiles));
        }
        
        // Java version
        metadata.put("javaVersion", System.getProperty("java.version"));
        
        // Spring Boot version
        String bootVersion = environment.getProperty("spring.boot.version");
        if (bootVersion != null) {
            metadata.put("springBootVersion", bootVersion);
        }
        
        // Startup time
        metadata.put("startTime", Instant.now().toString());
        
        return metadata;
    }
    
    /**
     * Get instance ID
     * 
     * @return Instance ID
     */
    public String getInstanceId() {
        return instanceId;
    }
    
    /**
     * Check if registered
     * 
     * @return Whether registered
     */
    public boolean isRegistered() {
        return registered;
    }
}
