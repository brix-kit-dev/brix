package io.brix.platform.starter.header;

/**
 * 平台统一 HTTP 请求头常
 * 
 * <p>@shinwa/platform-headers (TypeScript) 保持完全一致，
 * 确保 Java 后端和 TypeScript 前端使用相同的 Header 定义</p>
 * 
 * <p>设计目的</p>
 * <ul>
 *   <li>解决问题3：HTTP Headers 定义分散，Java/TS 不统一</li>
 *   <li>集中管理所有平台级别的 HTTP 请求</li>
 *   <li>避免 Header 名称拼写错误</li>
 * </ul>
 * 
 * <p>使用示例</p>
 * <pre>
 * // Controller 中获取请求头
 * &#64;RequestHeader(PlatformHeaders.TENANT_ID) String tenantId
 * 
 * // RestTemplate 中设置请求头
 * headers.add(PlatformHeaders.TENANT_ID, tenantId);
 * headers.add(PlatformHeaders.API_KEY, apiKey);
 * </pre>
 * 
 * <p>Header 分类</p>
 * <ul>
 *   <li>客户端标识：CLIENT, CLIENT_VERSION</li>
 *   <li>平台信息：PLATFORM_VERSION, PLATFORM_ENV, PLATFORM_TYPE</li>
 *   <li>租户与认证：TENANT_ID, API_KEY, API_SECRET</li>
 *   <li>用户身份：USER_ID, USER_ROLE, AUTHORIZATION</li>
 *   <li>追踪与调试：TRACE_ID, REQUEST_ID, SPAN_ID</li>
 *   <li>鍥介檯鍖栵細LANGUAGE, TIMEZONE</li>
 *   <li>设备信息：DEVICE_ID, DEVICE_MODEL, OS_VERSION</li>
 * </ul>
 * 
 * @author Brix Platform Authors Team
 * @since v2.1
 * @see TenantContextHolder
 * @see PlatformHeadersInterceptor
 */
public final class PlatformHeaders {
    
    /**
     * 私有构造函数，防止实例
     */
    private PlatformHeaders() {
        throw new UnsupportedOperationException("常量类不允许实例");
    }
    
    // ==================== 客户端标====================
    
    /**
     * 客户端标
     * 
     * <p>标识请求来源的客户端类型</p>
     * <p>可选值：web, mobile-ios, mobile-android, admin, service</p>
     */
    public static final String CLIENT = "X-Shinwa-Client";
    
    /**
     * 客户端版
     * 
     * <p>客户端应用的版本</p>
     * <p>格式：x.y.z (1.0.0)</p>
     */
    public static final String CLIENT_VERSION = "X-Shinwa-Client-Version";
    
    // ==================== 平台信息 ====================
    
    /**
     * 平台版本
     * 
     * <p>Shinwa 平台的版本号</p>
     */
    public static final String PLATFORM_VERSION = "X-Platform-Version";
    
    /**
     * 骞冲彴鐜
     * 
     * <p>当前运行环境</p>
     * <p>可选值：dev, test, staging, prod</p>
     */
    public static final String PLATFORM_ENV = "X-Platform-Env";
    
    /**
     * 平台类型
     * 
     * <p>平台类型标识</p>
     * <p>可选值：saas, private</p>
     */
    public static final String PLATFORM_TYPE = "X-Platform-Type";
    
    // ==================== 租户与认====================
    
    /**
     * 绉熸埛 ID
     * 
     * <p>多租户必须的请求头，用于数据隔离</p>
     * <p>所API 请求必须携带Header</p>
     * <p>默认租户：default</p>
     */
    public static final String TENANT_ID = "X-Tenant-Id";
    
    /**
     * API Key
     * 
     * <p>服务间调用的认证凭证</p>
     * <p>用于Plugin Engine 注册时的认证</p>
     */
    public static final String API_KEY = "X-API-Key";
    
    /**
     * API Secret
     * 
     * <p>服务间调用的认证密钥</p>
     * <p>API_KEY 配合使用</p>
     */
    public static final String API_SECRET = "X-API-Secret";
    
    // ==================== 用户身份 ====================
    
    /**
     * 用户 ID
     * 
     * <p>当前登录用户的唯一标识</p>
     */
    public static final String USER_ID = "X-User-Id";
    
    /**
     * 用户角色
     * 
     * <p>当前用户的角色编</p>
     * <p>多个角色用逗号分隔</p>
     */
    public static final String USER_ROLE = "X-User-Role";
    
    /**
     * 鎺堟潈浠ょ墝
     * 
     * <p>标准Authorization 请求</p>
     * <p>格式：Bearer {token}</p>
     */
    public static final String AUTHORIZATION = "Authorization";
    
    // ==================== 追踪与调====================
    
    /**
     * 杩借釜 ID
     * 
     * <p>分布式追踪的唯一标识</p>
     * <p>用于串联整个请求链路的日</p>
     */
    public static final String TRACE_ID = "X-Trace-Id";
    
    /**
     * 请求 ID
     * 
     * <p>单次请求的唯一标识</p>
     * <p>用于日志关联和问题排</p>
     */
    public static final String REQUEST_ID = "X-Request-Id";
    
    /**
     * 璺ㄥ害 ID
     * 
     * <p>分布式追踪的 Span 标识</p>
     * <p>用于标识请求链路中的具体节点</p>
     */
    public static final String SPAN_ID = "X-Span-Id";
    
    // ==================== 国际====================
    
    /**
     * 璇█鍋忓ソ
     * 
     * <p>标准Accept-Language 请求</p>
     * <p>格式：zh-CN, en-US </p>
     */
    public static final String LANGUAGE = "Accept-Language";
    
    /**
     * 时区
     * 
     * <p>客户端时区标</p>
     * <p>格式：Asia/Tokyo, UTC+8 </p>
     */
    public static final String TIMEZONE = "X-Timezone";
    
    // ==================== 设备信息 ====================
    
    /**
     * 设备 ID
     * 
     * <p>设备的唯一标识</p>
     * <p>用于设备绑定和安全审</p>
     */
    public static final String DEVICE_ID = "X-Device-Id";
    
    /**
     * 设备型号
     * 
     * <p>设备的型号信</p>
     * <p>如：iPhone 14 Pro, Pixel 7</p>
     */
    public static final String DEVICE_MODEL = "X-Device-Model";
    
    /**
     * 操作系统版本
     * 
     * <p>设备操作系统版本</p>
     * <p>如：iOS 17.0, Android 14</p>
     */
    public static final String OS_VERSION = "X-OS-Version";
    
    // ==================== 默认值常====================
    
    /**
     * 默认租户 ID
     * 
     * <p>当请求未携带租户 ID 时使用的默认</p>
     */
    public static final String DEFAULT_TENANT_ID = "default";
    
    /**
     * 默认客户端类
     */
    public static final String DEFAULT_CLIENT = "service";
}
