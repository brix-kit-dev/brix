package io.brix.platform.common.exception;

import java.util.Arrays;
import java.util.Optional;

/**
 * 平台级错误码定义 - 标准v1.0
 * <p>
 * 统一错误码规范，便于前后端协同与审计
 * </p>
 * 
 * <h3>错误码格式规</h3>
 * <pre>
 * 格式: {模块}-{分类}-{序号}
 * 
 * 模块:
 *   PLATFORM  - 鍩哄骇鍏叡
 *   AUTH      - 认证中心
 *   GATEWAY   - 网关
 *   ENGINE    - 插件引擎
 *   PLUGIN    - 插件前缀（如 PLUGIN-USER
 * 
 * 分类:
 *   A - 认证授权(4xx)
 *   B - 业务逻辑(4xx)
 *   S - 系统错误(5xx)
 *   V - 参数校验(4xx)
 * </pre>
 * 
 * <h3>统一响应格式</h3>
 * <pre>
 * // 成功响应
 * {
 *   "success": true,
 *   "code": "OK",
 *   "data": { ... },
 *   "timestamp": "2026-01-04T10:00:00Z",
 *   "traceId": "abc123"
 * }
 * 
 * // 错误响应
 * {
 *   "success": false,
 *   "code": "AUTH-A-002",
 *   "message": "Token has expired",
 *   "details": { ... },
 *   "timestamp": "2026-01-04T10:00:00Z",
 *   "traceId": "abc123"
 * }
 * </pre>
 *
 * @author Brix Platform Authors Platform Team
 * @version 2.0.0 (Standardization v1.0)
 */
public enum PlatformErrorCode {
    
    // ========== 通用成功 ==========
    SUCCESS("OK", "操作成功", 200),
    
    // ========== PLATFORM 鍏叡閿欒 (PLATFORM-*-*) ==========
    INVALID_PARAMETER("PLATFORM-V-001", "请求参数不合", 400),
    RESOURCE_NOT_FOUND("PLATFORM-B-001", "目标资源不存", 404),
    DUPLICATED_OPERATION("PLATFORM-B-002", "重复操作", 409),
    INTERNAL_ERROR("PLATFORM-S-001", "系统内部异常", 500),
    REMOTE_SERVICE_ERROR("PLATFORM-S-002", "下游服务调用失败", 502),
    DATA_INCONSISTENCY("PLATFORM-S-003", "数据一致性校验失", 500),
    CONCURRENT_MODIFICATION("PLATFORM-B-003", "数据已被其他用户修改，请刷新后重", 409),
    
    // ========== AUTH 认证授权错误 (AUTH-A-*) ==========
    UNAUTHORIZED("AUTH-A-001", "鏈巿鏉冭", 401),
    AUTH_FAILED("AUTH-A-002", "认证失败", 401),
    TOKEN_EXPIRED("AUTH-A-003", "Token 宸茶繃", 401),
    TOKEN_INVALID("AUTH-A-004", "Token 鏃犳晥", 401),
    TOKEN_REVOKED("AUTH-A-005", "Token 宸茶鍚婇攢", 401),
    FORBIDDEN("AUTH-A-006", "鏃犺闂潈", 403),
    ACCOUNT_LOCKED("AUTH-A-007", "账号已被锁定", 403),
    ACCOUNT_DISABLED("AUTH-A-008", "账号已被禁用", 403),
    REFRESH_TOKEN_EXPIRED("AUTH-A-009", "Refresh Token 宸茶繃", 401),
    OAUTH_ERROR("AUTH-A-010", "OAuth 认证失败", 401),
    
    // ========== ENGINE 插件引擎错误 (ENGINE-B-*) ==========
    PLUGIN_NOT_FOUND("ENGINE-B-001", "插件未注", 404),
    PLUGIN_ALREADY_REGISTERED("ENGINE-B-002", "插件已注", 409),
    PLUGIN_NOT_TRUSTED("ENGINE-A-001", "插件未授", 403),
    PLUGIN_CREDENTIAL_INVALID("ENGINE-A-002", "插件凭证无效", 401),
    ROUTE_QUOTA_EXCEEDED("ENGINE-B-003", "路由配额超限", 429),
    GLOBAL_ROUTE_QUOTA_EXCEEDED("ENGINE-B-004", "全局路由配额超限，最多允500 条路", 429),
    PLUGIN_ROUTE_QUOTA_EXCEEDED("ENGINE-B-005", "插件路由配额超限，每插件最多允50 条路", 429),
    MENU_QUOTA_EXCEEDED("ENGINE-B-006", "菜单配额超限", 429),
    MENU_DEPTH_EXCEEDED("ENGINE-B-007", "菜单深度超限，最大允3 级嵌", 429),
    PLUGIN_MENU_QUOTA_EXCEEDED("ENGINE-B-008", "插件菜单配额超限，每插件最多允30 个菜", 429),
    GLOBAL_MENU_QUOTA_EXCEEDED("ENGINE-B-009", "全局菜单配额超限", 429),
    
    // ========== GATEWAY 网关错误 (GATEWAY-*-*) ==========
    ROUTE_NOT_FOUND("GATEWAY-B-001", "璺敱鏈壘", 404),
    RATE_LIMIT_EXCEEDED("GATEWAY-B-002", "请求频率超限", 429),
    CIRCUIT_BREAKER_OPEN("GATEWAY-S-001", "服务熔断", 503);

    private final String code;
    private final String message;
    private final int httpStatus;

    PlatformErrorCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    /**
     * 根据 code 查找枚举，便于从日志或外部输入恢复错误语义
     *
     * @param code 错误
     * @return 匹配的错误码枚举
     */
    public static Optional<PlatformErrorCode> fromCode(String code) {
        return Arrays.stream(values()).filter(item -> item.code.equals(code)).findFirst();
    }
    
    /**
     * 判断是否为成功码
     */
    public boolean isSuccess() {
        return this == SUCCESS;
    }
    
    /**
     * 判断是否为客户端错误 (4xx)
     */
    public boolean isClientError() {
        return httpStatus >= 400 && httpStatus < 500;
    }
    
    /**
     * 判断是否为服务端错误 (5xx)
     */
    public boolean isServerError() {
        return httpStatus >= 500;
    }
}
