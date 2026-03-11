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

import java.util.List;
import java.util.Set;

/**
 * v2.1 Route Info DTO
 * 
 * <p>Describes REST endpoints exposed by the service, used for registration with the shell</p>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 */
public record RouteInfo(
    /**
     * Route path
     * 
     * <p>Example: /api/v1/users, /api/v1/users/{id}</p>
     */
    String path,
    
    /**
     * HTTP methods
     * 
     * <p>Example: GET, POST, PUT, DELETE</p>
     */
    Set<String> methods,
    
    /**
     * Controller class name
     * 
     * <p>Fully qualified class name, e.g., io.brix.plugin.user.controller.UserController</p>
     */
    String controllerClass,
    
    /**
     * Handler method name
     * 
     * <p>Example: listUsers, getUserById, createUser</p>
     */
    String methodName,
    
    /**
     * Request parameter info
     * 
     * <p>Including @RequestParam, @PathVariable, @RequestBody, etc.</p>
     */
    List<ParameterInfo> parameters,
    
    /**
     * Response type
     * 
     * <p>Method return type, e.g., ApiResponse&lt;User&gt;</p>
     */
    String responseType,
    
    /**
     * Whether deprecated
     * 
     * <p>Interfaces marked with @Deprecated</p>
     */
    boolean deprecated,
    
    /**
     * Route tags
     * 
     * <p>Used for classification and search, e.g., ["user", "auth"]</p>
     */
    Set<String> tags,
    
    /**
     * Interface description
     * 
     * <p>Provided by @ApiOperation or other documentation annotations</p>
     */
    String description
) {
    /**
     * Parameter info
     */
    public record ParameterInfo(
        /**
         * Parameter name
         */
        String name,
        
        /**
         * Parameter type
         * 
         * <p>Example: String, Long, CreateUserRequest</p>
         */
        String type,
        
        /**
         * Parameter source
         * 
         * <p>Example: PATH, QUERY, BODY, HEADER</p>
         */
        ParameterSource source,
        
        /**
         * Whether required
         */
        boolean required,
        
        /**
         * Default value
         */
        String defaultValue
    ) {}
    
    /**
     * Parameter source enum
     */
    public enum ParameterSource {
        /** URL path parameter, @PathVariable */
        PATH,
        
        /** URL query parameter, @RequestParam */
        QUERY,
        
        /** Request body, @RequestBody */
        BODY,
        
        /** Request header, @RequestHeader */
        HEADER,
        
        /** Cookie, @CookieValue */
        COOKIE
    }
}
