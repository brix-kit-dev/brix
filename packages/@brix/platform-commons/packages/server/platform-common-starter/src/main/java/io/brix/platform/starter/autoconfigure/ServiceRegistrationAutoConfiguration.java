package io.brix.platform.starter.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import io.brix.platform.starter.config.PlatformApiProperties;
import io.brix.platform.starter.config.ServiceProperties;
import io.brix.platform.starter.registration.PluginManifestScanner;
import io.brix.platform.starter.registration.RouteScanner;
import io.brix.platform.starter.registration.ServiceHeartbeatService;
import io.brix.platform.starter.registration.ServiceRegistrationService;

/**
 * v2.1 服务注册自动配置
 * 
 * <p>自动配置服务注册、心跳和路由扫描功能</p>
 * 
 * <p>启用条件</p>
 * <ul>
 *   <li>Web 应用环境</li>
 *   <li>shinwa.service.registration-enabled=true（默认）</li>
 *   <li>配置shinwa.service.name shinwa.service.base-url</li>
 * </ul>
 * 
 * <p>提供Bean</p>
 * <ul>
 *   <li>RouteScanner - 路由扫描</li>
 *   <li>ServiceRegistrationService - 服务注册服务</li>
 *   <li>ServiceHeartbeatService - 服务心跳服务</li>
 * </ul>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass({RequestMappingHandlerMapping.class})
@ConditionalOnProperty(
    prefix = "shinwa.service",
    name = "registration-enabled",
    havingValue = "true",
    matchIfMissing = true
)
@EnableConfigurationProperties(ServiceProperties.class)
@ComponentScan(basePackages = "io.brix.platform.starter")
public class ServiceRegistrationAutoConfiguration {
    
    /**
     * 路由扫描
     * 
     * <p>扫描服务中所@RestController 暴露REST 端点</p>
     * 
     * @param handlerMapping Spring 的请求映射处理器映射
     * @param serviceProperties 服务配置
     * @return 路由扫描
     */
    @Bean
    @ConditionalOnMissingBean
    public RouteScanner routeScanner(
            @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
            ServiceProperties serviceProperties) {
        return new RouteScanner(handlerMapping, serviceProperties);
    }
    
    /**
     * 插件清单扫描
     * 
     * <p>扫描 classpath 中所有插件的 META-INF/plugin-manifest.json</p>
     * 
     * @param objectMapper JSON 序列化器
     * @return 插件清单扫描
     */
    @Bean
    @ConditionalOnMissingBean
    public PluginManifestScanner pluginManifestScanner(ObjectMapper objectMapper) {
        return new PluginManifestScanner(objectMapper);
    }
    
    /**
     * 服务注册服务
     * 
     * <p>负责向基座注册服务信息，包括路由清单和聚合的 UI 契约</p>
     * 
     * @param serviceProperties 服务配置
     * @param platformApiProperties 平台 API 配置
     * @param routeScanner 路由扫描
     * @param pluginManifestScanner 插件清单扫描
     * @param environment 环境配置
     * @param objectMapper JSON 序列化器
     * @return 服务注册服务
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "shinwa.service",
        name = {"name", "base-url"}
    )
    public ServiceRegistrationService serviceRegistrationService(
            ServiceProperties serviceProperties,
            PlatformApiProperties platformApiProperties,
            RouteScanner routeScanner,
            PluginManifestScanner pluginManifestScanner,
            Environment environment,
            ObjectMapper objectMapper) {
        return new ServiceRegistrationService(
            serviceProperties, platformApiProperties, routeScanner, pluginManifestScanner, environment, objectMapper);
    }
    
    /**
     * 服务心跳服务
     * 
     * <p>负责定时向基座发送心</p>
     * 
     * @param serviceProperties 服务配置
     * @param registrationService 服务注册服务
     * @param healthEndpoint 鍋ュ悍绔偣
     * @return 服务心跳服务
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        prefix = "shinwa.service",
        name = {"name", "base-url"}
    )
    public ServiceHeartbeatService serviceHeartbeatService(
            ServiceProperties serviceProperties,
            ServiceRegistrationService registrationService,
            HealthEndpoint healthEndpoint) {
        return new ServiceHeartbeatService(
            serviceProperties, registrationService, healthEndpoint);
    }
}
