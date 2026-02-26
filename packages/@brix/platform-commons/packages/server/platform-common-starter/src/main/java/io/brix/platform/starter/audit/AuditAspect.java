package io.brix.platform.starter.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;

/**
 * v2.1 审计日志切面
 * 
 * <p>记录所有 REST API 调用的审计日志，包括</p>
 * <ul>
 *   <li>请求信息（方法、路径、参数）</li>
 *   <li>用户信息（用户ID、IP 地址</li>
 *   <li>响应信息（耗时、状态）</li>
 * </ul>
 * 
 * <p>启用条件</p>
 * <pre>
 * shinwa:
 *   audit:
 *     enabled: true  # 默认启用
 * </pre>
 * 
 * <p>日志格式</p>
 * <pre>
 * [AUDIT] requestId=xxx, method=POST, path=/api/v1/users, 
 *         userId=xxx, ip=127.0.0.1, duration=123ms, status=SUCCESS
 * </pre>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 */
@Aspect
@Component
@Order(1)  // 优先级最高，确保首先执行
@ConditionalOnProperty(
    prefix = "shinwa.audit",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class AuditAspect {
    
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");
    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);
    
    /** 请求 ID 头名*/
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    
    /** 用户 ID 头名*/
    private static final String USER_ID_HEADER = "X-User-ID";
    
    /**
     * 拦截所@RestController 中的请求处理方法
     * 
     * @param joinPoint 鍒囩偣
     * @return 方法返回
     * @throws Throwable 方法执行异常
     */
    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object auditRequest(ProceedingJoinPoint joinPoint) throws Throwable {
        // 获取请求上下
        ServletRequestAttributes attributes = 
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        
        if (attributes == null) {
            // Web 请求，直接执
            return joinPoint.proceed();
        }
        
        HttpServletRequest request = attributes.getRequest();
        
        // 获取或生成请ID
        String requestId = getOrGenerateRequestId(request);
        
        // 记录开始时
        long startTime = System.currentTimeMillis();
        Instant startInstant = Instant.now();
        
        // 提取请求信息
        String httpMethod = request.getMethod();
        String path = request.getRequestURI();
        String userId = request.getHeader(USER_ID_HEADER);
        String clientIp = getClientIp(request);
        String methodName = getMethodName(joinPoint);
        
        // 执行状
        String status = "SUCCESS";
        String errorMessage = null;
        
        try {
            // 执行目标方法
            Object result = joinPoint.proceed();
            return result;
        } catch (Exception e) {
            status = "FAILURE";
            errorMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            throw e;
        } finally {
            // 计算耗时
            long duration = System.currentTimeMillis() - startTime;
            
            // 记录审计日志
            logAudit(requestId, httpMethod, path, methodName, userId, clientIp, 
                    duration, status, errorMessage, startInstant);
        }
    }
    
    /**
     * 获取或生成请ID
     * 
     * @param request HTTP 请求
     * @return 请求 ID
     */
    private String getOrGenerateRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isEmpty()) {
            requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        return requestId;
    }
    
    /**
     * 获取客户IP 地址
     * 
     * @param request HTTP 请求
     * @return IP 鍦板潃
     */
    private String getClientIp(HttpServletRequest request) {
        // 按优先级检查各种代理头
        String[] headers = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP"
        };
        
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For 可能包含多个 IP，取第一
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * 获取方法
     * 
     * @param joinPoint 鍒囩偣
     * @return 类名.方法
     */
    private String getMethodName(ProceedingJoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return signature.getDeclaringType().getSimpleName() + "." + signature.getName();
    }
    
    /**
     * 记录审计日志
     */
    private void logAudit(String requestId, String httpMethod, String path, 
                          String methodName, String userId, String clientIp,
                          long duration, String status, String errorMessage,
                          Instant timestamp) {
        
        StringBuilder sb = new StringBuilder();
        sb.append("[AUDIT] ");
        sb.append("requestId=").append(requestId);
        sb.append(", method=").append(httpMethod);
        sb.append(", path=").append(path);
        sb.append(", handler=").append(methodName);
        
        if (userId != null) {
            sb.append(", userId=").append(userId);
        }
        
        sb.append(", ip=").append(clientIp);
        sb.append(", duration=").append(duration).append("ms");
        sb.append(", status=").append(status);
        
        if (errorMessage != null) {
            sb.append(", error=").append(errorMessage);
        }
        
        sb.append(", timestamp=").append(timestamp);
        
        if ("SUCCESS".equals(status)) {
            auditLog.info(sb.toString());
        } else {
            auditLog.warn(sb.toString());
        }
    }
}
