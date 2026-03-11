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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenAPI Configuration Properties Class
 * 
 * <h3>Phase 5: OpenAPI-Driven Frontend-Backend Contract Automation</h3>
 * <p>This properties class defines all configurable options for OpenAPI documentation generation,
 * supporting customization via application.yml or environment variables.</p>
 * 
 * <h3>Configuration Prefix</h3>
 * <pre>brix.openapi.*</pre>
 * 
 * <h3>Configuration Example</h3>
 * <pre>
 * brix:
 *   openapi:
 *     enabled: true
 *     title: Appointment Management System API
 *     version: 3.0.0
 *     description: Complete RESTful API for appointment management
 *     contact-name: Brix Platform Team
 *     contact-email: dev@brix.io
 *     contact-url: https://brix.io
 *     license-name: Commercial License
 *     license-url: https://brix.io/license
 *     terms-of-service: https://brix.io/terms
 *     external-docs-url: https://docs.brix.io
 *     external-docs-description: Complete developer documentation
 *     production-server-url: https://api.brix.io
 * </pre>
 * 
 * <h3>Default Values</h3>
 * <table border="1">
 *   <tr><th>Property</th><th>Default</th><th>Description</th></tr>
 *   <tr><td>enabled</td><td>true</td><td>Whether to enable OpenAPI documentation</td></tr>
 *   <tr><td>version</td><td>3.0.0</td><td>API version number</td></tr>
 *   <tr><td>licenseName</td><td>Apache-2.0</td><td>License name</td></tr>
 * </table>
 * 
 * @author Brix Platform Team
 * @since 3.1.0
 */
@ConfigurationProperties(prefix = "brix.openapi")
public class OpenApiProperties {
    
    /**
     * Whether to enable OpenAPI documentation generation
     * <p>Set to false to disable Swagger UI in production environment</p>
     */
    private boolean enabled = true;
    
    /**
     * API title
     * <p>Title displayed at the top of Swagger UI, defaults to application name</p>
     */
    private String title;
    
    /**
     * API version number
     * <p>Follows Semantic Versioning (SemVer)</p>
     */
    private String version = "3.0.0";
    
    /**
     * API description
     * <p>Detailed explanation of API purpose, functionality and usage</p>
     */
    private String description;
    
    /**
     * Contact name
     * <p>Usually team name or maintainer name</p>
     */
    private String contactName = "Brix Platform Team";
    
    /**
     * Contact email
     */
    private String contactEmail = "dev@brix.io";
    
    /**
     * Contact URL
     */
    private String contactUrl = "https://brix.io";
    
    /**
     * License name
     */
    private String licenseName = "Apache-2.0";
    
    /**
     * License URL
     */
    private String licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0";
    
    /**
     * Terms of service URL
     */
    private String termsOfService;
    
    /**
     * External documentation URL
     * <p>Points to complete developer documentation or Wiki</p>
     */
    private String externalDocsUrl = "https://docs.brix.io";
    
    /**
     * External documentation description
     */
    private String externalDocsDescription = "Brix Platform Developer Documentation";
    
    /**
     * Production server URL
     * <p>Optional. If configured, will be displayed as an optional server in Swagger UI</p>
     */
    private String productionServerUrl;
    
    // ==================== Getter/Setter ====================
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        this.title = title;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getContactName() {
        return contactName;
    }
    
    public void setContactName(String contactName) {
        this.contactName = contactName;
    }
    
    public String getContactEmail() {
        return contactEmail;
    }
    
    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }
    
    public String getContactUrl() {
        return contactUrl;
    }
    
    public void setContactUrl(String contactUrl) {
        this.contactUrl = contactUrl;
    }
    
    public String getLicenseName() {
        return licenseName;
    }
    
    public void setLicenseName(String licenseName) {
        this.licenseName = licenseName;
    }
    
    public String getLicenseUrl() {
        return licenseUrl;
    }
    
    public void setLicenseUrl(String licenseUrl) {
        this.licenseUrl = licenseUrl;
    }
    
    public String getTermsOfService() {
        return termsOfService;
    }
    
    public void setTermsOfService(String termsOfService) {
        this.termsOfService = termsOfService;
    }
    
    public String getExternalDocsUrl() {
        return externalDocsUrl;
    }
    
    public void setExternalDocsUrl(String externalDocsUrl) {
        this.externalDocsUrl = externalDocsUrl;
    }
    
    public String getExternalDocsDescription() {
        return externalDocsDescription;
    }
    
    public void setExternalDocsDescription(String externalDocsDescription) {
        this.externalDocsDescription = externalDocsDescription;
    }
    
    public String getProductionServerUrl() {
        return productionServerUrl;
    }
    
    public void setProductionServerUrl(String productionServerUrl) {
        this.productionServerUrl = productionServerUrl;
    }
}
