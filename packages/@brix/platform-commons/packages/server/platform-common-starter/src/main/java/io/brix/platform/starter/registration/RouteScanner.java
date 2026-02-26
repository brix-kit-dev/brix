package io.brix.platform.starter.registration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.MethodParameter;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import io.brix.platform.starter.config.ServiceProperties;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * v2.1 路由扫描
 * 
 * <p>负责扫描服务中所@RestController 暴露REST 端点</p>
 * 
 * <p>扫描逻辑</p>
 * <ol>
 *   <li>获取 Spring RequestMappingHandlerMapping</li>
 *   <li>遍历所有注册的 HandlerMethod</li>
 *   <li>根据配置basePackages 过滤</li>
 *   <li>根据 excludePatterns 排除</li>
 *   <li>提取路由元信息（路径、方法、参数等</li>
 * </ol>
 * 
 * <p>使用示例</p>
 * <pre>
 * {@code
 * @Autowired
 * private RouteScanner routeScanner;
 * 
 * List<RouteInfo> routes = routeScanner.scanRoutes();
 * }
 * </pre>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 */
public class RouteScanner {
    
    private static final Logger log = LoggerFactory.getLogger(RouteScanner.class);
    
    /** Spring 的请求映射处理器映射 */
    private final RequestMappingHandlerMapping handlerMapping;
    
    /** 服务配置 */
    private final ServiceProperties serviceProperties;
    
    /** 路径匹配器，用于排除模式匹配 */
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    
    /** 默认扫描的基础包（可通过 brix.route-scan.default-base-package 配置覆盖*/
    @Value("${brix.route-scan.default-base-package:io.platform.plugin}")
    private String defaultBasePackage;
    
    /** Actuator 路径前缀 */
    private static final String ACTUATOR_PREFIX = "/actuator";
    
    public RouteScanner(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping, 
                       ServiceProperties serviceProperties) {
        this.handlerMapping = handlerMapping;
        this.serviceProperties = serviceProperties;
    }
    
    /**
     * 扫描所有路
     * 
     * <p>根据配置basePackages excludePatterns 过滤路由</p>
     * 
     * @return 路由信息列表
     */
    public List<RouteInfo> scanRoutes() {
        // 检查是否启用路由扫
        if (!serviceProperties.getRouteScan().isEnabled()) {
            log.info("[RouteScanner] 璺敱鎵弿宸茬");
            return Collections.emptyList();
        }
        
        log.info("[RouteScanner] 开始扫描路..");
        
        // 获取所Handler 映射
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();
        
        List<RouteInfo> routes = new ArrayList<>();
        
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            RequestMappingInfo mappingInfo = entry.getKey();
            HandlerMethod handlerMethod = entry.getValue();
            
            // 过滤：检查是否在扫描范围
            if (!shouldInclude(handlerMethod, mappingInfo)) {
                continue;
            }
            
            // 提取路由信息
            List<RouteInfo> routeInfos = extractRouteInfo(mappingInfo, handlerMethod);
            routes.addAll(routeInfos);
        }
        
        log.info("[RouteScanner] 扫描完成，共发现 {} 个路", routes.size());
        
        return routes;
    }
    
    /**
     * 判断是否应该包含Handler
     * 
     * @param handlerMethod Handler 方法
     * @param mappingInfo 映射信息
     * @return 是否包含
     */
    private boolean shouldInclude(HandlerMethod handlerMethod, RequestMappingInfo mappingInfo) {
        // 获取 Controller 
        Class<?> beanType = handlerMethod.getBeanType();
        
        // 如果CGLIB 代理，获取原始类
        if (AopUtils.isCglibProxy(handlerMethod.getBean())) {
            beanType = AopUtils.getTargetClass(handlerMethod.getBean());
        }
        
        String className = beanType.getName();
        
        // 1. 检查是否在 basePackages 
        if (!isInBasePackages(className)) {
            return false;
        }
        
        // 2. 检Actuator 端点
        Set<String> patterns = getPatterns(mappingInfo);
        if (!serviceProperties.getRouteScan().isIncludeActuator()) {
            for (String pattern : patterns) {
                if (pattern.startsWith(ACTUATOR_PREFIX)) {
                    return false;
                }
            }
        }
        
        // 3. 检查排除模
        Set<String> excludePatterns = serviceProperties.getRouteScan().getExcludePatterns();
        if (!excludePatterns.isEmpty()) {
            for (String pattern : patterns) {
                for (String excludePattern : excludePatterns) {
                    if (pathMatcher.match(excludePattern, pattern)) {
                        return false;
                    }
                }
            }
        }
        
        return true;
    }
    
    /**
     * 检查类名是否在配置basePackages 
     * 
     * @param className 类名
     * @return 是否basePackages 
     */
    private boolean isInBasePackages(String className) {
        Set<String> basePackages = serviceProperties.getRouteScan().getBasePackages();
        
        // 如果没有配置 basePackages，使用默认
        if (basePackages == null || basePackages.isEmpty()) {
            return className.startsWith(defaultBasePackage);
        }
        
        // 检查是否匹配任意一basePackage
        for (String basePackage : basePackages) {
            if (className.startsWith(basePackage)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * RequestMappingInfo 中提取路径模
     * 
     * @param mappingInfo 映射信息
     * @return 路径模式集合
     */
    private Set<String> getPatterns(RequestMappingInfo mappingInfo) {
        // Spring 6.x 使用 getPathPatternsCondition
        if (mappingInfo.getPathPatternsCondition() != null) {
            return mappingInfo.getPathPatternsCondition().getPatterns()
                .stream()
                .map(Object::toString)
                .collect(Collectors.toSet());
        }
        
        // 兼容旧版
        if (mappingInfo.getPatternsCondition() != null) {
            return mappingInfo.getPatternsCondition().getPatterns();
        }
        
        return Collections.emptySet();
    }
    
    /**
     * 提取路由信息
     * 
     * @param mappingInfo 映射信息
     * @param handlerMethod Handler 方法
     * @return 路由信息列表（一Handler 可能对应多个路径
     */
    private List<RouteInfo> extractRouteInfo(RequestMappingInfo mappingInfo, 
                                              HandlerMethod handlerMethod) {
        List<RouteInfo> routes = new ArrayList<>();
        
        // 获取所有路径模
        Set<String> patterns = getPatterns(mappingInfo);
        
        // 获取 HTTP 方法
        Set<String> methods = mappingInfo.getMethodsCondition().getMethods()
            .stream()
            .map(Enum::name)
            .collect(Collectors.toSet());
        
        // 如果没有指定方法，默认为所有方
        if (methods.isEmpty()) {
            methods = Set.of("GET", "POST", "PUT", "DELETE", "PATCH");
        }
        
        // 获取 Controller 类信
        Class<?> beanType = handlerMethod.getBeanType();
        if (AopUtils.isCglibProxy(handlerMethod.getBean())) {
            beanType = AopUtils.getTargetClass(handlerMethod.getBean());
        }
        String controllerClass = beanType.getName();
        
        // 获取方法信息
        Method method = handlerMethod.getMethod();
        String methodName = method.getName();
        
        // 提取参数信息
        List<RouteInfo.ParameterInfo> parameters = extractParameters(handlerMethod);
        
        // 获取返回类型
        String responseType = method.getGenericReturnType().getTypeName();
        
        // 检查是否废
        boolean deprecated = method.isAnnotationPresent(Deprecated.class) ||
                            beanType.isAnnotationPresent(Deprecated.class);
        
        // 提取标签（从类名推断
        Set<String> tags = extractTags(beanType);
        
        // 提取描述
        String description = extractDescription(method);
        
        // 为每个路径创RouteInfo
        for (String pattern : patterns) {
            routes.add(new RouteInfo(
                pattern,
                methods,
                controllerClass,
                methodName,
                parameters,
                responseType,
                deprecated,
                tags,
                description
            ));
        }
        
        return routes;
    }
    
    /**
     * 提取参数信息
     * 
     * @param handlerMethod Handler 方法
     * @return 参数信息列表
     */
    private List<RouteInfo.ParameterInfo> extractParameters(HandlerMethod handlerMethod) {
        List<RouteInfo.ParameterInfo> parameters = new ArrayList<>();
        
        MethodParameter[] methodParameters = handlerMethod.getMethodParameters();
        
        for (MethodParameter mp : methodParameters) {
            Parameter parameter = mp.getParameter();
            String paramName = parameter.getName();
            String paramType = parameter.getType().getSimpleName();
            
            // 判断参数来源
            RouteInfo.ParameterSource source = determineParameterSource(mp);
            
            // 跳过非请求参数（HttpServletRequest, Model 等）
            if (source == null) {
                continue;
            }
            
            // 判断是否必需
            boolean required = isParameterRequired(mp, source);
            
            // 获取默认
            String defaultValue = getParameterDefaultValue(mp);
            
            // 尝试从注解获取参数名
            paramName = getParameterName(mp, paramName);
            
            parameters.add(new RouteInfo.ParameterInfo(
                paramName,
                paramType,
                source,
                required,
                defaultValue
            ));
        }
        
        return parameters;
    }
    
    /**
     * 判断参数来源
     * 
     * @param mp 方法参数
     * @return 参数来源，如果不是请求参数则返回 null
     */
    private RouteInfo.ParameterSource determineParameterSource(MethodParameter mp) {
        if (mp.hasParameterAnnotation(PathVariable.class)) {
            return RouteInfo.ParameterSource.PATH;
        }
        if (mp.hasParameterAnnotation(RequestParam.class)) {
            return RouteInfo.ParameterSource.QUERY;
        }
        if (mp.hasParameterAnnotation(RequestBody.class)) {
            return RouteInfo.ParameterSource.BODY;
        }
        if (mp.hasParameterAnnotation(RequestHeader.class)) {
            return RouteInfo.ParameterSource.HEADER;
        }
        if (mp.hasParameterAnnotation(CookieValue.class)) {
            return RouteInfo.ParameterSource.COOKIE;
        }
        
        // 没有注解的参数，检查是否是简单类
        Class<?> type = mp.getParameterType();
        if (isSimpleType(type)) {
            return RouteInfo.ParameterSource.QUERY;  // 简单类型默认为查询参数
        }
        
        return null;  // 复杂类型（如 HttpServletRequest）不是请求参
    }
    
    /**
     * 判断是否是简单类
     */
    private boolean isSimpleType(Class<?> type) {
        return type.isPrimitive() ||
               type == String.class ||
               Number.class.isAssignableFrom(type) ||
               type == Boolean.class ||
               type.isEnum();
    }
    
    /**
     * 判断参数是否必需
     */
    private boolean isParameterRequired(MethodParameter mp, RouteInfo.ParameterSource source) {
        switch (source) {
            case PATH:
                // PathVariable 默认必需
                PathVariable pv = mp.getParameterAnnotation(PathVariable.class);
                return pv == null || pv.required();
                
            case QUERY:
                RequestParam rp = mp.getParameterAnnotation(RequestParam.class);
                return rp != null && rp.required();
                
            case BODY:
                RequestBody rb = mp.getParameterAnnotation(RequestBody.class);
                return rb == null || rb.required();
                
            case HEADER:
                RequestHeader rh = mp.getParameterAnnotation(RequestHeader.class);
                return rh == null || rh.required();
                
            case COOKIE:
                CookieValue cv = mp.getParameterAnnotation(CookieValue.class);
                return cv == null || cv.required();
                
            default:
                return false;
        }
    }
    
    /**
     * 获取参数默认
     */
    private String getParameterDefaultValue(MethodParameter mp) {
        RequestParam rp = mp.getParameterAnnotation(RequestParam.class);
        if (rp != null && !rp.defaultValue().equals(ValueConstants.DEFAULT_NONE)) {
            return rp.defaultValue();
        }
        
        RequestHeader rh = mp.getParameterAnnotation(RequestHeader.class);
        if (rh != null && !rh.defaultValue().equals(ValueConstants.DEFAULT_NONE)) {
            return rh.defaultValue();
        }
        
        CookieValue cv = mp.getParameterAnnotation(CookieValue.class);
        if (cv != null && !cv.defaultValue().equals(ValueConstants.DEFAULT_NONE)) {
            return cv.defaultValue();
        }
        
        return null;
    }
    
    /**
     * 获取参数
     */
    private String getParameterName(MethodParameter mp, String defaultName) {
        PathVariable pv = mp.getParameterAnnotation(PathVariable.class);
        if (pv != null && !pv.value().isEmpty()) {
            return pv.value();
        }
        
        RequestParam rp = mp.getParameterAnnotation(RequestParam.class);
        if (rp != null && !rp.value().isEmpty()) {
            return rp.value();
        }
        
        RequestHeader rh = mp.getParameterAnnotation(RequestHeader.class);
        if (rh != null && !rh.value().isEmpty()) {
            return rh.value();
        }
        
        return defaultName;
    }
    
    /**
     * 从类名提取标
     * 
     * <p>例如：UserController -> ["user"]</p>
     */
    private Set<String> extractTags(Class<?> beanType) {
        Set<String> tags = new HashSet<>();
        
        String simpleName = beanType.getSimpleName();
        
        // 移除 Controller 后缀
        if (simpleName.endsWith("Controller")) {
            String tag = simpleName.substring(0, simpleName.length() - 10).toLowerCase();
            tags.add(tag);
        }
        
        return tags;
    }
    
    /**
     * 提取方法描述
     * 
     * <p>可以Swagger/OpenAPI 注解或其他文档注解中提取</p>
     */
    @SuppressWarnings("unchecked")
    private String extractDescription(Method method) {
        // 尝试@Operation 注解获取（Swagger 3.x
        try {
            Class<? extends java.lang.annotation.Annotation> operationClass = 
                (Class<? extends java.lang.annotation.Annotation>) Class.forName("io.swagger.v3.oas.annotations.Operation");
            java.lang.annotation.Annotation operation = method.getAnnotation(operationClass);
            if (operation != null) {
                Method summaryMethod = operationClass.getMethod("summary");
                return (String) summaryMethod.invoke(operation);
            }
        } catch (Exception ignored) {
            // Swagger 注解不存在，忽略
        }
        
        // 返回空描
        return "";
    }
}
