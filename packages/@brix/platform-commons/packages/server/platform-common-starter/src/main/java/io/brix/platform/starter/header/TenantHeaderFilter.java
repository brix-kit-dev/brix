package io.brix.platform.starter.header;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.util.StringUtils;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 租户 Header 过滤
 * 
 * <p>从入HTTP 请求中提取平Headers 并存ThreadLocal 上下文，
 * 确保在整个请求处理链路中可以获取到这些信息</p>
 * 
 * <p>设计目的</p>
 * <ul>
 *   <li>解决问题6：X-Tenant-Id 请求头经常遗漏导400 错误</li>
 *   <li>自动从请求中提取租户信息</li>
 *   <li>为服务间调用提供上下文传递基础</li>
 * </ul>
 * 
 * <p>提取Headers</p>
 * <ul>
 *   <li>X-Tenant-Id：租户标</li>
 *   <li>X-User-Id：用户标</li>
 *   <li>X-Trace-Id：追踪标识（如果缺失则自动生成）</li>
 * </ul>
 * 
 * <p>执行顺序</p>
 * <ul>
 *   <li>使用 Ordered.HIGHEST_PRECEDENCE 确保最先执</li>
 *   <li>在其他业Filter 之前设置好上下文</li>
 * </ul>
 * 
 * <p>生命周期</p>
 * <ol>
 *   <li>请求进入时：提取 Headers，设置到 TenantContextHolder</li>
 *   <li>请求处理中：业务代码可通过 TenantContextHolder 获取上下</li>
 *   <li>请求结束时：清理 TenantContextHolder，防止内存泄</li>
 * </ol>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 * @see TenantContextHolder
 * @see PlatformHeaders
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantHeaderFilter implements Filter {
    
    private static final Logger log = LoggerFactory.getLogger(TenantHeaderFilter.class);
    
    /**
     * 是否启用租户验证
     * 
     * <p>true 时，如果请求缺少租户 ID 会使用默认</p>
     * <p>false 时，缺少租户 ID 可能导致后续处理失败</p>
     */
    private final boolean requireTenant;
    
    /**
     * 构造函数
     * 
     * @param requireTenant 是否强制要求租户 ID
     */
    public TenantHeaderFilter(boolean requireTenant) {
        this.requireTenant = requireTenant;
    }
    
    /**
     * 默认构造函数
     * 
     * <p>默认不强制要求租户ID，使用默认</p>
     */
    public TenantHeaderFilter() {
        this(false);
    }
    
    /**
     * 过滤器核心逻辑
     * 
     * <p>提取请求头并设置到上下文，确保在 finally 块中清理上下</p>
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                         FilterChain chain) throws IOException, ServletException {
        
        try {
            // 只处HTTP 请求
            if (request instanceof HttpServletRequest httpRequest) {
                extractAndSetContext(httpRequest);
            }
            
            // 继续处理请求
            chain.doFilter(request, response);
            
        } finally {
            // 清理上下文，防止内存泄漏
            // 必须finally 块中执行，确保异常情况下也能清理
            TenantContextHolder.clear();
        }
    }
    
    /**
     * HTTP 请求中提取平Headers 并设置到上下
     * 
     * @param request HTTP 请求
     */
    private void extractAndSetContext(HttpServletRequest request) {
        // 1. 提取租户 ID
        String tenantId = extractTenantId(request);
        if (StringUtils.hasText(tenantId)) {
            TenantContextHolder.setTenantId(tenantId);
            log.debug("[TenantHeaderFilter] 设置租户ID: {}", tenantId);
        } else if (requireTenant) {
            // 强制要求租户时使用默认
            TenantContextHolder.setTenantId(PlatformHeaders.DEFAULT_TENANT_ID);
            log.debug("[TenantHeaderFilter] 使用默认租户ID: {}", PlatformHeaders.DEFAULT_TENANT_ID);
        } else {
            // 非强制时也设置默认值，确保上下文总有
            TenantContextHolder.setTenantId(PlatformHeaders.DEFAULT_TENANT_ID);
        }
        
        // 2. 提取用户 ID
        String userId = request.getHeader(PlatformHeaders.USER_ID);
        if (StringUtils.hasText(userId)) {
            TenantContextHolder.setUserId(userId);
            log.debug("[TenantHeaderFilter] 设置用户ID: {}", userId);
        }
        
        // 3. 提取或生成追ID
        String traceId = request.getHeader(PlatformHeaders.TRACE_ID);
        if (!StringUtils.hasText(traceId)) {
            // 如果请求中没有追ID，自动生成一
            traceId = generateTraceId();
            log.debug("[TenantHeaderFilter] 生成追踪ID: {}", traceId);
        }
        TenantContextHolder.setTraceId(traceId);
    }
    
    /**
     * 从请求中提取租户 ID
     * 
     * <p>优先Header 获取，如果不存在则尝试从请求参数获取</p>
     * 
     * @param request HTTP 请求
     * @return 租户 ID，可能为 null
     */
    private String extractTenantId(HttpServletRequest request) {
        // 优先Header 获取
        String tenantId = request.getHeader(PlatformHeaders.TENANT_ID);
        
        // 如果 Header 中没有，尝试从请求参数获
        if (!StringUtils.hasText(tenantId)) {
            tenantId = request.getParameter("tenantId");
        }
        
        return tenantId;
    }
    
    /**
     * 生成追踪 ID
     * 
     * <p>使用 UUID 生成唯一的追踪标</p>
     * 
     * @return 杩借釜 ID
     */
    private String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
