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
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Instant;
import java.util.*;

/**
 * 增强审计切面
 * 
 * <p>v2.1 阶段4 审计日志增强实现</p>
 * 
 * <p>功能说明</p>
 * <p>拦截标注@Auditable 注解的方法，记录详细的审计日志</p>
 * 
 * <p>AuditAspect 的区别：</p>
 * <ul>
 *   <li>AuditAspect：记录所有 REST 接口的基础审计</li>
 *   <li>AuditableAspect：记录标注了 @Auditable 的方法的详细审计</li>
 * </ul>
 * 
 * <p>审计日志包含</p>
 * <ul>
 *   <li>操作类型和资源类</li>
 *   <li>请求参数（可配置脱敏</li>
 *   <li>操作结果（成失败</li>
 *   <li>执行耗时</li>
 *   <li>用户信息和客户端信息</li>
 * </ul>
 * 
 * <p>配置项：</p>
 * <pre>
 * shinwa:
 *   audit:
 *     enhanced:
 *       enabled: true  # 是否启用增强审计
 * </pre>
 * 
 * @author Brix Platform Authors Platform Team
 * @since v2.1
 * @see Auditable
 */
@Aspect
@Component
@Order(2)  // 在基础审计切面之后执行
@ConditionalOnProperty(
    prefix = "shinwa.audit.enhanced",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class AuditableAspect {
    
    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT.ENHANCED");
    private static final Logger log = LoggerFactory.getLogger(AuditableAspect.class);
    
    /** 用户 ID 头名*/
    private static final String USER_ID_HEADER = "X-User-ID";
    /** 租户 ID 头名*/
    private static final String TENANT_ID_HEADER = "X-Tenant-ID";
    /** 请求 ID 头名*/
    private static final String REQUEST_ID_HEADER = "X-Request-ID";
    
    /** 脱敏占位*/
    private static final String MASKED_VALUE = "****";
    
    /** 参数值最大长*/
    private static final int MAX_PARAM_VALUE_LENGTH = 200;
    
    /**
     * 构造函数
     */
    public AuditableAspect() {
        log.info("[AuditableAspect] 增强审计切面已启");
    }
    
    /**
     * 拦截 @Auditable 注解的方
     * 
     * @param joinPoint 鍒囩偣
     * @param auditable 审计注解
     * @return 方法返回
     * @throws Throwable 方法执行异常
     */
    @Around("@annotation(auditable)")
    public Object auditMethod(ProceedingJoinPoint joinPoint, Auditable auditable) throws Throwable {
        long startTime = System.currentTimeMillis();
        Instant timestamp = Instant.now();
        
        // 提取上下文信
        AuditContext context = buildAuditContext(joinPoint, auditable);
        
        String status = "SUCCESS";
        String errorMessage = null;
        Object result = null;
        
        try {
            // 执行目标方法
            result = joinPoint.proceed();
            return result;
        } catch (Exception e) {
            status = "FAILURE";
            errorMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            throw e;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            
            // 记录审计日志
            logEnhancedAudit(context, status, errorMessage, duration, timestamp, 
                auditable.recordResult() ? result : null);
        }
    }
    
    /**
     * 构建审计上下
     */
    private AuditContext buildAuditContext(ProceedingJoinPoint joinPoint, Auditable auditable) {
        AuditContext context = new AuditContext();
        context.action = auditable.action();
        context.resource = auditable.resource();
        context.description = auditable.description();
        
        // 提取方法信息
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        context.methodName = signature.getDeclaringType().getSimpleName() + "." + signature.getName();
        
        // 提取请求信息
        ServletRequestAttributes attributes = 
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            context.userId = request.getHeader(USER_ID_HEADER);
            context.tenantId = request.getHeader(TENANT_ID_HEADER);
            context.requestId = request.getHeader(REQUEST_ID_HEADER);
            context.clientIp = getClientIp(request);
            context.path = request.getRequestURI();
            context.httpMethod = request.getMethod();
        }
        
        // 提取参数（如果配置了记录参数
        if (auditable.recordParams()) {
            context.params = extractParams(joinPoint, auditable.sensitiveParams());
        }
        
        return context;
    }
    
    /**
     * 提取方法参数
     */
    private Map<String, String> extractParams(ProceedingJoinPoint joinPoint, String[] sensitiveParams) {
        Map<String, String> params = new LinkedHashMap<>();
        Set<String> sensitiveSet = new HashSet<>(Arrays.asList(sensitiveParams));
        
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();
        
        for (int i = 0; i < parameters.length && i < args.length; i++) {
            String paramName = parameters[i].getName();
            Object paramValue = args[i];
            
            // 检查是否为敏感参数
            if (sensitiveSet.contains(paramName.toLowerCase())) {
                params.put(paramName, MASKED_VALUE);
                continue;
            }
            
            // 转换参数
            String valueStr = convertParamValue(paramValue);
            params.put(paramName, valueStr);
        }
        
        return params;
    }
    
    /**
     * 转换参数值为字符
     */
    private String convertParamValue(Object value) {
        if (value == null) {
            return "null";
        }
        
        String str;
        if (value instanceof String) {
            str = (String) value;
        } else if (value.getClass().isPrimitive() || 
                   value instanceof Number || 
                   value instanceof Boolean) {
            str = value.toString();
        } else if (value.getClass().isArray()) {
            str = "[array:" + java.lang.reflect.Array.getLength(value) + "]";
        } else if (value instanceof Collection) {
            str = "[collection:" + ((Collection<?>) value).size() + "]";
        } else {
            str = "[" + value.getClass().getSimpleName() + "]";
        }
        
        // 截断过长的
        if (str.length() > MAX_PARAM_VALUE_LENGTH) {
            str = str.substring(0, MAX_PARAM_VALUE_LENGTH) + "...";
        }
        
        return str;
    }
    
    /**
     * 获取客户IP
     */
    private String getClientIp(HttpServletRequest request) {
        String[] headers = {"X-Forwarded-For", "X-Real-IP", "Proxy-Client-IP"};
        
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                if (ip.contains(",")) {
                    ip = ip.split(",")[0].trim();
                }
                return ip;
            }
        }
        
        return request.getRemoteAddr();
    }
    
    /**
     * 记录增强审计日志
     */
    private void logEnhancedAudit(AuditContext context, String status, String errorMessage,
                                   long duration, Instant timestamp, Object result) {
        StringBuilder sb = new StringBuilder();
        sb.append("[AUDIT] ");
        sb.append("action=").append(context.action);
        sb.append(", resource=").append(context.resource);
        
        if (context.requestId != null) {
            sb.append(", requestId=").append(context.requestId);
        }
        
        if (context.tenantId != null) {
            sb.append(", tenantId=").append(context.tenantId);
        }
        
        if (context.userId != null) {
            sb.append(", userId=").append(context.userId);
        }
        
        sb.append(", method=").append(context.httpMethod);
        sb.append(", path=").append(context.path);
        sb.append(", handler=").append(context.methodName);
        
        if (context.params != null && !context.params.isEmpty()) {
            sb.append(", params=").append(context.params);
        }
        
        sb.append(", ip=").append(context.clientIp);
        sb.append(", status=").append(status);
        sb.append(", duration=").append(duration).append("ms");
        
        if (errorMessage != null) {
            sb.append(", error=").append(errorMessage);
        }
        
        if (result != null) {
            sb.append(", result=").append(convertParamValue(result));
        }
        
        if (context.description != null && !context.description.isEmpty()) {
            sb.append(", desc=").append(context.description);
        }
        
        sb.append(", timestamp=").append(timestamp);
        
        if ("SUCCESS".equals(status)) {
            auditLog.info(sb.toString());
        } else {
            auditLog.warn(sb.toString());
        }
    }
    
    /**
     * 审计上下
     */
    private static class AuditContext {
        String action;
        String resource;
        String description;
        String methodName;
        String userId;
        String tenantId;
        String requestId;
        String clientIp;
        String path;
        String httpMethod;
        Map<String, String> params;
    }
}
