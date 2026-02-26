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
 * OpenAPI 配置属性类
 * 
 * <h3>Phase 5：OpenAPI 驱动的前后端契约自动化</h3>
 * <p>本属性类定义 OpenAPI 文档生成的所有可配置项，支持通过
 * application.yml 或环境变量进行自定义。</p>
 * 
 * <h3>配置前缀</h3>
 * <pre>brix.openapi.*</pre>
 * 
 * <h3>配置示例</h3>
 * <pre>
 * brix:
 *   openapi:
 *     enabled: true
 *     title: 预约管理系统 API
 *     version: 3.0.0
 *     description: 提供预约管理的完整 RESTful API
 *     contact-name: Shinwa Team
 *     contact-email: dev@shinwa.com
 *     contact-url: https://shinwa.com
 *     license-name: Commercial License
 *     license-url: https://shinwa.com/license
 *     terms-of-service: https://shinwa.com/terms
 *     external-docs-url: https://docs.shinwa.com
 *     external-docs-description: 完整开发者文档
 *     production-server-url: https://api.shinwa.com
 * </pre>
 * 
 * <h3>默认值说明</h3>
 * <table border="1">
 *   <tr><th>属性</th><th>默认值</th><th>说明</th></tr>
 *   <tr><td>enabled</td><td>true</td><td>是否启用 OpenAPI 文档</td></tr>
 *   <tr><td>version</td><td>3.0.0</td><td>API 版本号</td></tr>
 *   <tr><td>licenseName</td><td>Apache-2.0</td><td>许可证名称</td></tr>
 * </table>
 * 
 * @author Brix Platform Team
 * @since 3.1.0
 */
@ConfigurationProperties(prefix = "brix.openapi")
public class OpenApiProperties {
    
    /**
     * 是否启用 OpenAPI 文档生成
     * <p>设置为 false 可在生产环境禁用 Swagger UI</p>
     */
    private boolean enabled = true;
    
    /**
     * API 标题
     * <p>显示在 Swagger UI 顶部的标题，默认使用应用名称</p>
     */
    private String title;
    
    /**
     * API 版本号
     * <p>遵循语义化版本规范（SemVer）</p>
     */
    private String version = "3.0.0";
    
    /**
     * API 描述
     * <p>详细说明 API 的用途、功能和使用方式</p>
     */
    private String description;
    
    /**
     * 联系人名称
     * <p>通常为团队名称或维护者名称</p>
     */
    private String contactName = "Brix Platform Team";
    
    /**
     * 联系人邮箱
     */
    private String contactEmail = "dev@brix.io";
    
    /**
     * 联系人网址
     */
    private String contactUrl = "https://brix.io";
    
    /**
     * 许可证名称
     */
    private String licenseName = "Apache-2.0";
    
    /**
     * 许可证 URL
     */
    private String licenseUrl = "https://www.apache.org/licenses/LICENSE-2.0";
    
    /**
     * 服务条款 URL
     */
    private String termsOfService;
    
    /**
     * 外部文档 URL
     * <p>指向完整的开发者文档或 Wiki</p>
     */
    private String externalDocsUrl = "https://docs.brix.io";
    
    /**
     * 外部文档描述
     */
    private String externalDocsDescription = "Brix Platform 开发者文档";
    
    /**
     * 生产环境服务器 URL
     * <p>可选。如果配置，会在 Swagger UI 中显示为可选服务器</p>
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
