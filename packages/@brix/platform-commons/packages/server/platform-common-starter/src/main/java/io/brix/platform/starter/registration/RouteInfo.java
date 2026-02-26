package io.brix.platform.starter.registration;

import java.util.List;
import java.util.Set;

/**
 * v2.1 路由信息 DTO
 * 
 * <p>描述服务暴露REST 端点，用于向基座注册</p>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 */
public record RouteInfo(
    /**
     * 路由路径
     * 
     * <p>例如api/v1/users, /api/v1/users/{id}</p>
     */
    String path,
    
    /**
     * HTTP 方法
     * 
     * <p>例如：GET, POST, PUT, DELETE</p>
     */
    Set<String> methods,
    
    /**
     * Controller 类名
     * 
     * <p>完整类名，例如：com.shinwa.plugin.user.controller.UserController</p>
     */
    String controllerClass,
    
    /**
     * 处理方法
     * 
     * <p>例如：listUsers, getUserById, createUser</p>
     */
    String methodName,
    
    /**
     * 请求参数信息
     * 
     * <p>包括 @RequestParam, @PathVariable, @RequestBody </p>
     */
    List<ParameterInfo> parameters,
    
    /**
     * 响应类型
     * 
     * <p>方法返回值类型，例如：ApiResponse&lt;User&gt;</p>
     */
    String responseType,
    
    /**
     * 是否已废
     * 
     * <p>标记@Deprecated 的接</p>
     */
    boolean deprecated,
    
    /**
     * 路由标签
     * 
     * <p>用于分类和搜索，例如：["user", "auth"]</p>
     */
    Set<String> tags,
    
    /**
     * 接口描述
     * 
     * <p>@ApiOperation 或其他文档注解提</p>
     */
    String description
) {
    /**
     * 参数信息
     */
    public record ParameterInfo(
        /**
         * 参数
         */
        String name,
        
        /**
         * 参数类型
         * 
         * <p>例如：String, Long, CreateUserRequest</p>
         */
        String type,
        
        /**
         * 参数来源
         * 
         * <p>例如：PATH, QUERY, BODY, HEADER</p>
         */
        ParameterSource source,
        
        /**
         * 是否必需
         */
        boolean required,
        
        /**
         * 默认
         */
        String defaultValue
    ) {}
    
    /**
     * 参数来源枚举
     */
    public enum ParameterSource {
        /** URL 路径参数，@PathVariable */
        PATH,
        
        /** URL 查询参数，@RequestParam */
        QUERY,
        
        /** 请求体，@RequestBody */
        BODY,
        
        /** 请求头，@RequestHeader */
        HEADER,
        
        /** Cookie，@CookieValue */
        COOKIE
    }
}
