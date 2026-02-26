package io.brix.platform.starter.header;

import java.io.IOException;
import java.net.URI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StringUtils;

import io.brix.platform.starter.config.ServiceProperties;

/**
 * API Key 认证拦截器（出站请求
 * 
 * <p>自动为向基座的请求添API Key API Secret 认证头，
 * 确保服务间调用的安全认证</p>
 * 
 * <p>设计目的</p>
 * <ul>
 *   <li>解决问题5：API_KEY/API_SECRET 参数经常缺失</li>
 *   <li>自动为符合条件的请求添加认证</li>
 *   <li>避免每次请求都手动设置认证信</li>
 * </ul>
 * 
 * <p>认证头：</p>
 * <ul>
 *   <li>X-API-Key：服务的 API Key</li>
 *   <li>X-API-Secret：服务的 API Secret</li>
 * </ul>
 * 
 * <p>工作原理</p>
 * <ol>
 *   <li>检查请求 URI 是否指向基座（根据 baseUrl 判断</li>
 *   <li>如果是基座请求，添加认证</li>
 *   <li>如果配置了认证信息，则添</li>
 * </ol>
 * 
 * <p>配置示例</p>
 * <pre>
 * shinwa:
 *   service:
 *     api-key: ${SHINWA_SERVICE_API_KEY:platform-service-key}
 *     api-secret: ${SHINWA_SERVICE_API_SECRET:platform-service-secret}
 *     base-url: http://platform-host-web:8080
 * </pre>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 * @see PlatformHeaders
 * @see ServiceProperties
 */
public class ApiKeyAuthInterceptor implements ClientHttpRequestInterceptor {
    
    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthInterceptor.class);
    
    /**
     * 服务配置
     */
    private final ServiceProperties serviceProperties;
    
    /**
     * 是否仅对基座请求添加认证
     * 
     * <p>true 时，只对指向 baseUrl 的请求添加认证头</p>
     * <p>false 时，对所有请求添加认证头</p>
     */
    private final boolean hostOnly;
    
    /**
     * 构造函数
     * 
     * @param serviceProperties 服务配置
     * @param hostOnly          是否仅对基座请求添加认证
     */
    public ApiKeyAuthInterceptor(ServiceProperties serviceProperties, boolean hostOnly) {
        this.serviceProperties = serviceProperties;
        this.hostOnly = hostOnly;
    }
    
    /**
     * 构造函数（默认仅对基座请求添加认证
     * 
     * @param serviceProperties 服务配置
     */
    public ApiKeyAuthInterceptor(ServiceProperties serviceProperties) {
        this(serviceProperties, true);
    }
    
    /**
     * 拦截出站请求并添加认Headers
     * 
     * @param request   HTTP 请求
     * @param body      请求
     * @param execution 执行
     * @return HTTP 响应
     * @throws IOException 如果请求执行失败
     */
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, 
                                        ClientHttpRequestExecution execution) throws IOException {
        
        // 判断是否需要添加认证头
        if (shouldAddAuthHeaders(request.getURI())) {
            addAuthHeaders(request);
        }
        
        return execution.execute(request, body);
    }
    
    /**
     * 判断是否应该为该请求添加认证
     * 
     * @param uri 请求 URI
     * @return 是否需要添加认证头
     */
    private boolean shouldAddAuthHeaders(URI uri) {
        // 检查是否配置了认证信息
        if (!hasAuthConfig()) {
            return false;
        }
        
        // 如果不限制仅基座请求，则对所有请求添
        if (!hostOnly) {
            return true;
        }
        
        // 检查是否是基座请求
        return isHostRequest(uri);
    }
    
    /**
     * 检查是否配置了认证信息
     * 
     * @return 是否配置API Key API Secret
     */
    private boolean hasAuthConfig() {
        return serviceProperties != null
            && StringUtils.hasText(serviceProperties.getApiKey())
            && StringUtils.hasText(serviceProperties.getApiSecret());
    }
    
    /**
     * 判断请求是否指向基座
     * 
     * @param uri 请求 URI
     * @return 是否是基座请
     */
    private boolean isHostRequest(URI uri) {
        if (serviceProperties == null || !StringUtils.hasText(serviceProperties.getBaseUrl())) {
            return false;
        }
        
        String requestUrl = uri.toString();
        String baseUrl = serviceProperties.getBaseUrl();
        
        return requestUrl.startsWith(baseUrl);
    }
    
    /**
     * 添加认证 Headers
     * 
     * @param request HTTP 请求
     */
    private void addAuthHeaders(HttpRequest request) {
        var headers = request.getHeaders();
        
        // 添加 API Key
        if (!headers.containsKey(PlatformHeaders.API_KEY)) {
            headers.add(PlatformHeaders.API_KEY, serviceProperties.getApiKey());
        }
        
        // 添加 API Secret
        if (!headers.containsKey(PlatformHeaders.API_SECRET)) {
            headers.add(PlatformHeaders.API_SECRET, serviceProperties.getApiSecret());
        }
        
        log.debug("[ApiKeyAuthInterceptor] 添加认证头到请求: {}", request.getURI());
    }
}
