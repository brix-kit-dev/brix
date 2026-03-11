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
 * v2.1 Route Scanner
 * 
 * <p>Responsible for scanning REST endpoints exposed by all @RestController in the service</p>
 * 
 * <p>Scanning logic</p>
 * <ol>
 *   <li>Get Spring RequestMappingHandlerMapping</li>
 *   <li>Iterate through all registered HandlerMethods</li>
 *   <li>Filter by configured basePackages</li>
 *   <li>Exclude by excludePatterns</li>
 *   <li>Extract route metadata (path, method, parameters, etc.)</li>
 * </ol>
 * 
 * <p>Usage example</p>
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
    
    /** Spring request mapping handler mapping */
    private final RequestMappingHandlerMapping handlerMapping;
    
    /** Service configuration */
    private final ServiceProperties serviceProperties;
    
    /** Path matcher for exclude pattern matching */
    private final AntPathMatcher pathMatcher = new AntPathMatcher();
    
    /** Default base package for scanning (can be overridden via brix.route-scan.default-base-package) */
    @Value("${brix.route-scan.default-base-package:io.platform.plugin}")
    private String defaultBasePackage;
    
    /** Actuator path prefix */
    private static final String ACTUATOR_PREFIX = "/actuator";
    
    public RouteScanner(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping, 
                       ServiceProperties serviceProperties) {
        this.handlerMapping = handlerMapping;
        this.serviceProperties = serviceProperties;
    }
    
    /**
     * Scan all routes
     * 
     * <p>Filter routes by configured basePackages and excludePatterns</p>
     * 
     * @return List of route information
     */
    public List<RouteInfo> scanRoutes() {
        // Check if route scanning is enabled
        if (!serviceProperties.getRouteScan().isEnabled()) {
            log.info("[RouteScanner] Route scanning disabled");
            return Collections.emptyList();
        }
        
        log.info("[RouteScanner] Starting route scanning...");
        
        // Get all handler mappings
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = handlerMapping.getHandlerMethods();
        
        List<RouteInfo> routes = new ArrayList<>();
        
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMethods.entrySet()) {
            RequestMappingInfo mappingInfo = entry.getKey();
            HandlerMethod handlerMethod = entry.getValue();
            
            // Filter: check if within scan scope
            if (!shouldInclude(handlerMethod, mappingInfo)) {
                continue;
            }
            
            // Extract route info
            List<RouteInfo> routeInfos = extractRouteInfo(mappingInfo, handlerMethod);
            routes.addAll(routeInfos);
        }
        
        log.info("[RouteScanner] Scanning complete, found {} routes", routes.size());
        
        return routes;
    }
    
    /**
     * Determine if handler should be included
     * 
     * @param handlerMethod Handler method
     * @param mappingInfo Mapping info
     * @return Whether to include
     */
    private boolean shouldInclude(HandlerMethod handlerMethod, RequestMappingInfo mappingInfo) {
        // Get Controller class
        Class<?> beanType = handlerMethod.getBeanType();
        
        // If CGLIB proxy, get original class
        if (AopUtils.isCglibProxy(handlerMethod.getBean())) {
            beanType = AopUtils.getTargetClass(handlerMethod.getBean());
        }
        
        String className = beanType.getName();
        
        // 1. Check if in basePackages
        if (!isInBasePackages(className)) {
            return false;
        }
        
        // 2. Check Actuator endpoints
        Set<String> patterns = getPatterns(mappingInfo);
        if (!serviceProperties.getRouteScan().isIncludeActuator()) {
            for (String pattern : patterns) {
                if (pattern.startsWith(ACTUATOR_PREFIX)) {
                    return false;
                }
            }
        }
        
        // 3. Check exclude patterns
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
     * Check if class name is in configured basePackages
     * 
     * @param className Class name
     * @return Whether in basePackages
     */
    private boolean isInBasePackages(String className) {
        Set<String> basePackages = serviceProperties.getRouteScan().getBasePackages();
        
        // If basePackages not configured, use default
        if (basePackages == null || basePackages.isEmpty()) {
            return className.startsWith(defaultBasePackage);
        }
        
        // Check if matches any basePackage
        for (String basePackage : basePackages) {
            if (className.startsWith(basePackage)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Extract path patterns from RequestMappingInfo
     * 
     * @param mappingInfo Mapping info
     * @return Set of path patterns
     */
    private Set<String> getPatterns(RequestMappingInfo mappingInfo) {
        // Spring 6.x uses getPathPatternsCondition
        if (mappingInfo.getPathPatternsCondition() != null) {
            return mappingInfo.getPathPatternsCondition().getPatterns()
                .stream()
                .map(Object::toString)
                .collect(Collectors.toSet());
        }
        
        // Backward compatible with older versions
        if (mappingInfo.getPatternsCondition() != null) {
            return mappingInfo.getPatternsCondition().getPatterns();
        }
        
        return Collections.emptySet();
    }
    
    /**
     * Extract route info
     * 
     * @param mappingInfo Mapping info
     * @param handlerMethod Handler method
     * @return List of route info (one handler may correspond to multiple paths)
     */
    private List<RouteInfo> extractRouteInfo(RequestMappingInfo mappingInfo, 
                                              HandlerMethod handlerMethod) {
        List<RouteInfo> routes = new ArrayList<>();
        
        // Get all path patterns
        Set<String> patterns = getPatterns(mappingInfo);
        
        // Get HTTP methods
        Set<String> methods = mappingInfo.getMethodsCondition().getMethods()
            .stream()
            .map(Enum::name)
            .collect(Collectors.toSet());
        
        // If no methods specified, default to all methods
        if (methods.isEmpty()) {
            methods = Set.of("GET", "POST", "PUT", "DELETE", "PATCH");
        }
        
        // Get Controller class info
        Class<?> beanType = handlerMethod.getBeanType();
        if (AopUtils.isCglibProxy(handlerMethod.getBean())) {
            beanType = AopUtils.getTargetClass(handlerMethod.getBean());
        }
        String controllerClass = beanType.getName();
        
        // Get method info
        Method method = handlerMethod.getMethod();
        String methodName = method.getName();
        
        // Extract parameter info
        List<RouteInfo.ParameterInfo> parameters = extractParameters(handlerMethod);
        
        // Get return type
        String responseType = method.getGenericReturnType().getTypeName();
        
        // Check if deprecated
        boolean deprecated = method.isAnnotationPresent(Deprecated.class) ||
                            beanType.isAnnotationPresent(Deprecated.class);
        
        // Extract tags (inferred from class name)
        Set<String> tags = extractTags(beanType);
        
        // Extract description
        String description = extractDescription(method);
        
        // Create RouteInfo for each path
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
     * Extract parameter info
     * 
     * @param handlerMethod Handler method
     * @return List of parameter info
     */
    private List<RouteInfo.ParameterInfo> extractParameters(HandlerMethod handlerMethod) {
        List<RouteInfo.ParameterInfo> parameters = new ArrayList<>();
        
        MethodParameter[] methodParameters = handlerMethod.getMethodParameters();
        
        for (MethodParameter mp : methodParameters) {
            Parameter parameter = mp.getParameter();
            String paramName = parameter.getName();
            String paramType = parameter.getType().getSimpleName();
            
            // Determine parameter source
            RouteInfo.ParameterSource source = determineParameterSource(mp);
            
            // Skip non-request parameters (HttpServletRequest, Model, etc.)
            if (source == null) {
                continue;
            }
            
            // Determine if required
            boolean required = isParameterRequired(mp, source);
            
            // Get default value
            String defaultValue = getParameterDefaultValue(mp);
            
            // Try to get parameter name from annotation
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
     * Determine parameter source
     * 
     * @param mp Method parameter
     * @return Parameter source, returns null if not a request parameter
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
        
        // Parameter without annotation, check if simple type
        Class<?> type = mp.getParameterType();
        if (isSimpleType(type)) {
            return RouteInfo.ParameterSource.QUERY;  // Simple types default to query parameter
        }
        
        return null;  // Complex types (e.g., HttpServletRequest) are not request parameters
    }
    
    /**
     * Check if simple type
     */
    private boolean isSimpleType(Class<?> type) {
        return type.isPrimitive() ||
               type == String.class ||
               Number.class.isAssignableFrom(type) ||
               type == Boolean.class ||
               type.isEnum();
    }
    
    /**
     * Determine if parameter is required
     */
    private boolean isParameterRequired(MethodParameter mp, RouteInfo.ParameterSource source) {
        switch (source) {
            case PATH:
                // PathVariable is required by default
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
     * Get parameter default value
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
     * Get parameter name
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
     * Extract tags from class name
     * 
     * <p>Example: UserController -> ["user"]</p>
     */
    private Set<String> extractTags(Class<?> beanType) {
        Set<String> tags = new HashSet<>();
        
        String simpleName = beanType.getSimpleName();
        
        // Remove Controller suffix
        if (simpleName.endsWith("Controller")) {
            String tag = simpleName.substring(0, simpleName.length() - 10).toLowerCase();
            tags.add(tag);
        }
        
        return tags;
    }
    
    /**
     * Extract method description
     * 
     * <p>Can extract from Swagger/OpenAPI annotations or other documentation annotations</p>
     */
    @SuppressWarnings("unchecked")
    private String extractDescription(Method method) {
        // Try to get from @Operation annotation (Swagger 3.x)
        try {
            Class<? extends java.lang.annotation.Annotation> operationClass = 
                (Class<? extends java.lang.annotation.Annotation>) Class.forName("io.swagger.v3.oas.annotations.Operation");
            java.lang.annotation.Annotation operation = method.getAnnotation(operationClass);
            if (operation != null) {
                Method summaryMethod = operationClass.getMethod("summary");
                return (String) summaryMethod.invoke(operation);
            }
        } catch (Exception ignored) {
            // Swagger annotation doesn't exist, ignore
        }
        
        // Return empty description
        return "";
    }
}
