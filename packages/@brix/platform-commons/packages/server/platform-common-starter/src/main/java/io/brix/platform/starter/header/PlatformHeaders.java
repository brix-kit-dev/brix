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
package io.brix.platform.starter.header;

/**
 * Platform Unified HTTP Request Header Constants
 * 
 * <p>Maintains complete consistency with @brix/platform-headers (TypeScript),
 * ensuring Java backend and TypeScript frontend use the same Header definitions.</p>
 * 
 * <p>Design Purpose:</p>
 * <ul>
 *   <li>Resolve Issue 3: HTTP Headers definitions scattered, Java/TS inconsistent</li>
 *   <li>Centralized management of all platform-level HTTP request headers</li>
 *   <li>Avoid Header name spelling errors</li>
 * </ul>
 * 
 * <p>Usage Example:</p>
 * <pre>
 * // Get request header in Controller
 * &#64;RequestHeader(PlatformHeaders.TENANT_ID) String tenantId
 * 
 * // Set request header in RestTemplate
 * headers.add(PlatformHeaders.TENANT_ID, tenantId);
 * headers.add(PlatformHeaders.API_KEY, apiKey);
 * </pre>
 * 
 * <p>Header Categories:</p>
 * <ul>
 *   <li>Client Identification: CLIENT, CLIENT_VERSION</li>
 *   <li>Platform Info: PLATFORM_VERSION, PLATFORM_ENV, PLATFORM_TYPE</li>
 *   <li>Tenant & Auth: TENANT_ID, API_KEY, API_SECRET</li>
 *   <li>User Identity: USER_ID, USER_ROLE, AUTHORIZATION</li>
 *   <li>Tracing & Debug: TRACE_ID, REQUEST_ID, SPAN_ID</li>
 *   <li>Internationalization: LANGUAGE, TIMEZONE</li>
 *   <li>Device Info: DEVICE_ID, DEVICE_MODEL, OS_VERSION</li>
 * </ul>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 * @see TenantContextHolder
 * @see PlatformHeadersInterceptor
 */
public final class PlatformHeaders {
    
    /**
     * Private constructor to prevent instantiation
     */
    private PlatformHeaders() {
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }
    
    // ==================== Client Identification ====================
    
    /**
     * Client Identifier
     * 
     * <p>Identifies the client type from which the request originated</p>
     * <p>Possible values: web, mobile-ios, mobile-android, admin, service</p>
     */
    public static final String CLIENT = "X-Brix-Client";
    
    /**
     * Client Version
     * 
     * <p>Version of the client application</p>
     * <p>Format: x.y.z (e.g., 1.0.0)</p>
     */
    public static final String CLIENT_VERSION = "X-Brix-Client-Version";
    
    // ==================== Platform Info ====================
    
    /**
     * Platform Version
     * 
     * <p>Version number of Brix Platform</p>
     */
    public static final String PLATFORM_VERSION = "X-Platform-Version";
    
    /**
     * Platform Environment
     * 
     * <p>Current runtime environment</p>
     * <p>Possible values: dev, test, staging, prod</p>
     */
    public static final String PLATFORM_ENV = "X-Platform-Env";
    
    /**
     * Platform Type
     * 
     * <p>Platform type identifier</p>
     * <p>Possible values: saas, private</p>
     */
    public static final String PLATFORM_TYPE = "X-Platform-Type";
    
    // ==================== Tenant & Authentication ====================
    
    /**
     * Tenant ID
     * 
     * <p>Required request header for multi-tenancy, used for data isolation</p>
     * <p>All API requests must carry this header</p>
     * <p>Default tenant: default</p>
     */
    public static final String TENANT_ID = "X-Tenant-Id";
    
    /**
     * API Key
     * 
     * <p>Authentication credential for inter-service calls</p>
     * <p>Used for authentication during Plugin Engine registration</p>
     */
    public static final String API_KEY = "X-API-Key";
    
    /**
     * API Secret
     * 
     * <p>Authentication secret for inter-service calls</p>
     * <p>Used in conjunction with API_KEY</p>
     */
    public static final String API_SECRET = "X-API-Secret";
    
    // ==================== User Identity ====================
    
    /**
     * User ID
     * 
     * <p>Unique identifier of the currently logged-in user</p>
     */
    public static final String USER_ID = "X-User-Id";
    
    /**
     * User Role
     * 
     * <p>Role code of the current user</p>
     * <p>Multiple roles are separated by commas</p>
     */
    public static final String USER_ROLE = "X-User-Role";
    
    /**
     * Authorization Token
     * 
     * <p>Standard Authorization request header</p>
     * <p>Format: Bearer {token}</p>
     */
    public static final String AUTHORIZATION = "Authorization";
    
    // ==================== Tracing & Debugging ====================
    
    /**
     * Trace ID
     * 
     * <p>Unique identifier for distributed tracing</p>
     * <p>Used to correlate logs across the entire request chain</p>
     */
    public static final String TRACE_ID = "X-Trace-Id";
    
    /**
     * Request ID
     * 
     * <p>Unique identifier for a single request</p>
     * <p>Used for log correlation and troubleshooting</p>
     */
    public static final String REQUEST_ID = "X-Request-Id";
    
    /**
     * Span ID
     * 
     * <p>Span identifier for distributed tracing</p>
     * <p>Used to identify specific nodes in the request chain</p>
     */
    public static final String SPAN_ID = "X-Span-Id";
    
    // ==================== Internationalization ====================
    
    /**
     * Language Preference
     * 
     * <p>Standard Accept-Language request header</p>
     * <p>Format: zh-CN, en-US, etc.</p>
     */
    public static final String LANGUAGE = "Accept-Language";
    
    /**
     * Timezone
     * 
     * <p>Client timezone identifier</p>
     * <p>Format: Asia/Tokyo, UTC+8, etc.</p>
     */
    public static final String TIMEZONE = "X-Timezone";
    
    // ==================== Device Info ====================
    
    /**
     * Device ID
     * 
     * <p>Unique identifier of the device</p>
     * <p>Used for device binding and security auditing</p>
     */
    public static final String DEVICE_ID = "X-Device-Id";
    
    /**
     * Device Model
     * 
     * <p>Device model information</p>
     * <p>e.g., iPhone 14 Pro, Pixel 7</p>
     */
    public static final String DEVICE_MODEL = "X-Device-Model";
    
    /**
     * Operating System Version
     * 
     * <p>Device operating system version</p>
     * <p>e.g., iOS 17.0, Android 14</p>
     */
    public static final String OS_VERSION = "X-OS-Version";
    
    // ==================== Default Value Constants ====================
    
    /**
     * Default Tenant ID
     * 
     * <p>Default value used when request does not carry a tenant ID</p>
     */
    public static final String DEFAULT_TENANT_ID = "default";
    
    /**
     * Default client type
     */
    public static final String DEFAULT_CLIENT = "service";
}
