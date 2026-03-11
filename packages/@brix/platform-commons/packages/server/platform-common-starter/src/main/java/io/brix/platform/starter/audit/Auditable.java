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
package io.brix.platform.starter.audit;

import java.lang.annotation.*;

/**
 * Audit Annotation
 * 
 * <p>v2.1 Phase 4 - Audit Logging Enhancement</p>
 * 
 * <p>Functionality</p>
 * <p>Marks methods that require detailed audit logging with customizable audit configuration.</p>
 * 
 * <p>Usage Example</p>
 * <pre>{@code
 * @Auditable(
 *     action = "FILE_DOWNLOAD",
 *     resource = "FileCenter",
 *     recordParams = true,
 *     recordResult = false
 * )
 * public InputStream downloadFile(Long fileId) {
 *     // ...
 * }
 * }</pre>
 * 
 * <p>Audit Log Output Format</p>
 * <pre>
 * [AUDIT] action=FILE_DOWNLOAD, resource=FileCenter, userId=xxx, 
 *         params={fileId=123}, ip=127.0.0.1, status=SUCCESS, duration=50ms
 * </pre>
 * 
 * @author Brix Platform Authors Platform Team
 * @since v2.1
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Auditable {
    
    /**
     * Action Type
     * 
     * <p>Describes the operation performed by the method, e.g.: CREATE, READ, UPDATE, DELETE,
     * FILE_UPLOAD, FILE_DOWNLOAD, LOGIN, etc.</p>
     * 
     * @return Action type
     */
    String action();
    
    /**
     * Resource Type
     * 
     * <p>Describes the resource being operated on, e.g.: User, File, Case, Contract, etc.</p>
     * 
     * @return Resource type
     */
    String resource();
    
    /**
     * Whether to record request parameters
     * 
     * <p>Default is true. Note: For sensitive information, set to false or use @SensitiveParam annotation.</p>
     * 
     * @return Whether to record parameters
     */
    boolean recordParams() default true;
    
    /**
     * Whether to record return result
     * 
     * <p>Default is false. For large objects or streaming responses, keep this as false.</p>
     * 
     * @return Whether to record result
     */
    boolean recordResult() default false;
    
    /**
     * Sensitive parameter names
     * 
     * <p>These parameters will be masked (displayed as ****)</p>
     * 
     * @return Array of sensitive parameter names
     */
    String[] sensitiveParams() default {"password", "token", "secret"};
    
    /**
     * Operation description template
     * 
     * <p>Supports SpEL expressions, e.g.: "User #{#userId} downloaded file #{#fileId}"</p>
     * 
     * @return Description template
     */
    String description() default "";
}
