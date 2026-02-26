package io.brix.platform.starter.header;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StringUtils;

import io.brix.platform.starter.config.ServiceProperties;

/**
 * 平台 Headers 拦截器（出站请求
 * 
 * <p>自动为所有出HTTP 请求添加平台标准 Headers
 * 确保服务间调用时上下文信息能够正确传递</p>
 * 
 * <p>设计目的</p>
 * <ul>
 *   <li>解决问题3：HTTP Headers 定义分散</li>
 *   <li>解决问题6：X-Tenant-Id 请求头经常遗</li>
 *   <li>自动传递租户、追踪等上下文信</li>
 * </ul>
 * 
 * <p>添加Headers</p>
 * <ul>
 *   <li>X-Tenant-Id：从 TenantContextHolder 获取</li>
 *   <li>X-Trace-Id：从 TenantContextHolder 获取</li>
 *   <li>X-User-Id：从 TenantContextHolder 获取（如果存在）</li>
 *   <li>X-Shinwa-Client：标识为服务调用</li>
 *   <li>X-Shinwa-Client-Version：服务版本（如果配置了）</li>
 * </ul>
 * 
 * <p>使用方式</p>
 * <pre>
 * // 配置 RestTemplate
 * RestTemplate restTemplate = new RestTemplate();
 * restTemplate.getInterceptors().add(new PlatformHeadersInterceptor(serviceProperties));
 * 
 * // 配置 WebClient（参WebClientAutoConfiguration
 * </pre>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 * @see TenantContextHolder
 * @see PlatformHeaders
 */
public class PlatformHeadersInterceptor implements ClientHttpRequestInterceptor {
    
    private static final Logger log = LoggerFactory.getLogger(PlatformHeadersInterceptor.class);
    
    /**
     * 服务配置
     */
    private final ServiceProperties serviceProperties;
    
    /**
     * 构造函数
     * 
     * @param serviceProperties 服务配置，用于获取服务名和版
     */
    public PlatformHeadersInterceptor(ServiceProperties serviceProperties) {
        this.serviceProperties = serviceProperties;
    }
    
    /**
     * 拦截出站请求并添加平Headers
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
        
        // 添加平台标准 Headers
        addPlatformHeaders(request);
        
        log.debug("[PlatformHeadersInterceptor] 出站请求: {} {}, Headers: {}",
            request.getMethod(), request.getURI(), request.getHeaders().keySet());
        
        return execution.execute(request, body);
    }
    
    /**
     * 添加平台标准 Headers
     * 
     * @param request HTTP 请求
     */
    private void addPlatformHeaders(HttpRequest request) {
        var headers = request.getHeaders();
        
        // 1. 添加租户 ID（必须）
        if (!headers.containsKey(PlatformHeaders.TENANT_ID)) {
            String tenantId = TenantContextHolder.getTenantId();
            headers.add(PlatformHeaders.TENANT_ID, tenantId);
        }
        
        // 2. 添加追踪 ID
        String traceId = TenantContextHolder.getTraceId();
        if (StringUtils.hasText(traceId) && !headers.containsKey(PlatformHeaders.TRACE_ID)) {
            headers.add(PlatformHeaders.TRACE_ID, traceId);
        }
        
        // 3. 添加用户 ID（如果存在）
        String userId = TenantContextHolder.getUserId();
        if (StringUtils.hasText(userId) && !headers.containsKey(PlatformHeaders.USER_ID)) {
            headers.add(PlatformHeaders.USER_ID, userId);
        }
        
        // 4. 添加客户端标
        if (!headers.containsKey(PlatformHeaders.CLIENT)) {
            String clientName = serviceProperties != null && StringUtils.hasText(serviceProperties.getName())
                ? serviceProperties.getName()
                : PlatformHeaders.DEFAULT_CLIENT;
            headers.add(PlatformHeaders.CLIENT, clientName);
        }
        
        // 5. 添加平台环境（如果配置了
        // 可以从配置中读取当前环境
    }
}
