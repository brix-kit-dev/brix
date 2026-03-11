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
package io.brix.platform.starter.autoconfigure;

import java.util.ArrayList;
import java.util.List;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * OpenAPI Auto-Configuration Class
 * 
 * <h3>Phase 5: OpenAPI-Driven Frontend-Backend Contract Automation</h3>
 * <p>This configuration class implements Task 5.1-2: Configure OpenAPI global metadata, including:</p>
 * <ul>
 *   <li>API basic information (title, version, description)</li>
 *   <li>Contact information</li>
 *   <li>License information</li>
 *   <li>Security scheme definition (JWT Bearer Token)</li>
 *   <li>Server address configuration</li>
 *   <li>External documentation link</li>
 * </ul>
 * 
 * <h3>Configuration Example</h3>
 * <pre>
 * brix:
 *   openapi:
 *     enabled: true
 *     title: Brix Platform API
 *     version: 3.0.0
 *     description: Brix Runtime Shell Platform API Documentation
 *     contact-name: Brix Team
 *     contact-email: dev@brix.io
 *     contact-url: https://brix.io
 *     license-name: Apache-2.0
 *     license-url: https://www.apache.org/licenses/LICENSE-2.0
 *     terms-of-service: https://brix.io/terms
 *     external-docs-url: https://docs.brix.io
 *     external-docs-description: Complete development documentation
 * </pre>
 * 
 * <h3>Security Scheme</h3>
 * <p>Default configuration uses JWT Bearer Token authentication scheme. All API endpoints requiring authentication
 * can be enabled via the {@code @SecurityRequirement(name = "bearerAuth")} annotation.</p>
 * 
 * <h3>Architecture Location</h3>
 * <p>Layer 3 Capability Implementation Layer - platform-commons/packages/server/platform-common-starter</p>
 * 
 * @author Brix Platform Team
 * @since 3.1.0
 * @see <a href="https://springdoc.org/">SpringDoc OpenAPI</a>
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(OpenAPI.class)
@ConditionalOnProperty(
    prefix = "brix.openapi",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
@EnableConfigurationProperties(OpenApiProperties.class)
public class OpenApiAutoConfiguration {
    
    /**
     * Service name (used for API title)
     * <p>Retrieved from spring.application.name, defaults to "Brix Platform"</p>
     */
    @Value("${spring.application.name:Brix Platform}")
    private String applicationName;
    
    /**
     * Service port (used for server address configuration)
     */
    @Value("${server.port:8080}")
    private int serverPort;
    
    /**
     * Create OpenAPI documentation configuration
     * 
     * <p>Configures API documentation global metadata, including:</p>
     * <ul>
     *   <li>Basic info: title, description, version, terms of service</li>
     *   <li>Contact: name, email, URL</li>
     *   <li>License: Apache-2.0</li>
     *   <li>Security scheme: JWT Bearer Token</li>
     *   <li>Server: local development server</li>
     *   <li>External documentation: development docs link</li>
     * </ul>
     * 
     * @param properties OpenAPI configuration properties
     * @return OpenAPI configuration object
     */
    @Bean
    @ConditionalOnMissingBean
    public OpenAPI brixOpenAPI(OpenApiProperties properties) {
        // Build API basic info
        Info info = new Info()
            .title(resolveTitle(properties))
            .version(properties.getVersion())
            .description(resolveDescription(properties))
            .termsOfService(properties.getTermsOfService())
            .contact(new Contact()
                .name(properties.getContactName())
                .email(properties.getContactEmail())
                .url(properties.getContactUrl()))
            .license(new License()
                .name(properties.getLicenseName())
                .url(properties.getLicenseUrl()));
        
        // Build server list
        List<Server> servers = new ArrayList<>();
        servers.add(new Server()
            .url("http://localhost:" + serverPort)
            .description("Local Development Server"));
        
        // If production server URL is configured, add to list
        if (properties.getProductionServerUrl() != null 
                && !properties.getProductionServerUrl().isBlank()) {
            servers.add(new Server()
                .url(properties.getProductionServerUrl())
                .description("Production Server"));
        }
        
        // Build external documentation link
        ExternalDocumentation externalDocs = new ExternalDocumentation()
            .description(properties.getExternalDocsDescription())
            .url(properties.getExternalDocsUrl());
        
        // Build security scheme components
        Components components = new Components()
            .addSecuritySchemes("bearerAuth", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("JWT authentication token. Format: Bearer {token}"));
        
        // Build and return complete OpenAPI configuration
        return new OpenAPI()
            .info(info)
            .servers(servers)
            .externalDocs(externalDocs)
            .components(components)
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
    
    /**
     * Create public API group
     * 
     * <p>Group all public API endpoints by path prefix /api/**</p>
     * 
     * @return GroupedOpenApi public API group
     */
    @Bean
    @ConditionalOnMissingBean(name = "publicApiGroup")
    public GroupedOpenApi publicApiGroup() {
        return GroupedOpenApi.builder()
            .group("public-api")
            .displayName("Public API")
            .pathsToMatch("/api/**")
            .build();
    }
    
    /**
     * Create management API group
     * 
     * <p>Group management endpoints by path prefixes /actuator/** and /admin/**</p>
     * 
     * @return GroupedOpenApi management API group
     */
    @Bean
    @ConditionalOnMissingBean(name = "managementApiGroup")
    public GroupedOpenApi managementApiGroup() {
        return GroupedOpenApi.builder()
            .group("management-api")
            .displayName("Management API")
            .pathsToMatch("/actuator/**", "/admin/**")
            .build();
    }
    
    /**
     * Resolve API title
     * <p>Prioritizes configured title, otherwise uses application name</p>
     * 
     * @param properties OpenAPI configuration properties
     * @return API title
     */
    private String resolveTitle(OpenApiProperties properties) {
        if (properties.getTitle() != null && !properties.getTitle().isBlank()) {
            return properties.getTitle();
        }
        return applicationName + " API";
    }
    
    /**
     * Resolve API description
     * <p>Prioritizes configured description, otherwise generates default description</p>
     * 
     * @param properties OpenAPI configuration properties
     * @return API description
     */
    private String resolveDescription(OpenApiProperties properties) {
        if (properties.getDescription() != null && !properties.getDescription().isBlank()) {
            return properties.getDescription();
        }
        return applicationName + " RESTful API Documentation. Auto-generated based on OpenAPI 3.0 specification.";
    }
}
