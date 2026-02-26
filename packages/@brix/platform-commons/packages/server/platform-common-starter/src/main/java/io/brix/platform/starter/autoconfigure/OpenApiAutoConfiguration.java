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

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenAPI 自动配置类
 * 
 * <h3>Phase 5：OpenAPI 驱动的前后端契约自动化</h3>
 * <p>本配置类实现任务 5.1-2：配置 OpenAPI 全局元数据，包括：</p>
 * <ul>
 *   <li>API 基本信息（标题、版本、描述）</li>
 *   <li>联系人信息</li>
 *   <li>许可证信息</li>
 *   <li>安全方案定义（JWT Bearer Token）</li>
 *   <li>服务器地址配置</li>
 *   <li>外部文档链接</li>
 * </ul>
 * 
 * <h3>配置示例</h3>
 * <pre>
 * brix:
 *   openapi:
 *     enabled: true
 *     title: Brix Platform API
 *     version: 3.0.0
 *     description: Brix 运行壳平台 API 文档
 *     contact-name: Brix Team
 *     contact-email: dev@brix.io
 *     contact-url: https://brix.io
 *     license-name: Apache-2.0
 *     license-url: https://www.apache.org/licenses/LICENSE-2.0
 *     terms-of-service: https://brix.io/terms
 *     external-docs-url: https://docs.brix.io
 *     external-docs-description: 完整开发文档
 * </pre>
 * 
 * <h3>安全方案</h3>
 * <p>默认配置 JWT Bearer Token 认证方案，所有需要认证的 API 端点
 * 可通过 {@code @SecurityRequirement(name = "bearerAuth")} 注解启用。</p>
 * 
 * <h3>架构位置</h3>
 * <p>Layer 3 能力实现层 - platform-commons/packages/server/platform-common-starter</p>
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
     * 服务名称（用于 API 标题）
     * <p>从 spring.application.name 获取，默认为 "Brix Platform"</p>
     */
    @Value("${spring.application.name:Brix Platform}")
    private String applicationName;
    
    /**
     * 服务端口（用于服务器地址配置）
     */
    @Value("${server.port:8080}")
    private int serverPort;
    
    /**
     * 创建 OpenAPI 文档配置
     * 
     * <p>配置 API 文档的全局元数据，包括：</p>
     * <ul>
     *   <li>基本信息：标题、描述、版本、服务条款</li>
     *   <li>联系人：名称、邮箱、网址</li>
     *   <li>许可证：Apache-2.0</li>
     *   <li>安全方案：JWT Bearer Token</li>
     *   <li>服务器：本地开发服务器</li>
     *   <li>外部文档：开发文档链接</li>
     * </ul>
     * 
     * @param properties OpenAPI 配置属性
     * @return OpenAPI 配置对象
     */
    @Bean
    @ConditionalOnMissingBean
    public OpenAPI brixOpenAPI(OpenApiProperties properties) {
        // 构建 API 基本信息
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
        
        // 构建服务器列表
        List<Server> servers = new ArrayList<>();
        servers.add(new Server()
            .url("http://localhost:" + serverPort)
            .description("本地开发服务器"));
        
        // 如果配置了生产服务器地址，添加到列表
        if (properties.getProductionServerUrl() != null 
                && !properties.getProductionServerUrl().isBlank()) {
            servers.add(new Server()
                .url(properties.getProductionServerUrl())
                .description("生产环境服务器"));
        }
        
        // 构建外部文档链接
        ExternalDocumentation externalDocs = new ExternalDocumentation()
            .description(properties.getExternalDocsDescription())
            .url(properties.getExternalDocsUrl());
        
        // 构建安全方案组件
        Components components = new Components()
            .addSecuritySchemes("bearerAuth", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("JWT 认证令牌。格式：Bearer {token}"));
        
        // 构建并返回完整的 OpenAPI 配置
        return new OpenAPI()
            .info(info)
            .servers(servers)
            .externalDocs(externalDocs)
            .components(components)
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
    
    /**
     * 创建公共 API 分组
     * 
     * <p>按路径前缀 /api/** 分组所有公共 API 端点</p>
     * 
     * @return GroupedOpenApi 公共 API 分组
     */
    @Bean
    @ConditionalOnMissingBean(name = "publicApiGroup")
    public GroupedOpenApi publicApiGroup() {
        return GroupedOpenApi.builder()
            .group("public-api")
            .displayName("公共 API")
            .pathsToMatch("/api/**")
            .build();
    }
    
    /**
     * 创建管理 API 分组
     * 
     * <p>按路径前缀 /actuator/** 和 /admin/** 分组管理端点</p>
     * 
     * @return GroupedOpenApi 管理 API 分组
     */
    @Bean
    @ConditionalOnMissingBean(name = "managementApiGroup")
    public GroupedOpenApi managementApiGroup() {
        return GroupedOpenApi.builder()
            .group("management-api")
            .displayName("管理 API")
            .pathsToMatch("/actuator/**", "/admin/**")
            .build();
    }
    
    /**
     * 解析 API 标题
     * <p>优先使用配置的标题，否则使用应用名称</p>
     * 
     * @param properties OpenAPI 配置属性
     * @return API 标题
     */
    private String resolveTitle(OpenApiProperties properties) {
        if (properties.getTitle() != null && !properties.getTitle().isBlank()) {
            return properties.getTitle();
        }
        return applicationName + " API";
    }
    
    /**
     * 解析 API 描述
     * <p>优先使用配置的描述，否则生成默认描述</p>
     * 
     * @param properties OpenAPI 配置属性
     * @return API 描述
     */
    private String resolveDescription(OpenApiProperties properties) {
        if (properties.getDescription() != null && !properties.getDescription().isBlank()) {
            return properties.getDescription();
        }
        return applicationName + " RESTful API 文档。基于 OpenAPI 3.0 规范自动生成。";
    }
}
