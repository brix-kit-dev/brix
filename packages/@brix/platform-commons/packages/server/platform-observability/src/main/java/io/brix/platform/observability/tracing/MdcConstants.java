package io.brix.platform.observability.tracing;

/**
 * MDC 键常
 * <p>
 * 统一 MDC 键名，确保日志格式一致性
 * </p>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 */
public final class MdcConstants {

    private MdcConstants() {
        // 不允许实例化
    }

    /** 杩借釜ID */
    public static final String TRACE_ID = "traceId";

    /** 绉熸埛ID */
    public static final String TENANT_ID = "tenantId";

    /** 用户ID */
    public static final String USER_ID = "userId";

    /** 关联ID（Saga 事务*/
    public static final String CORRELATION_ID = "correlationId";

    /** 请求路径 */
    public static final String REQUEST_PATH = "requestPath";

    /** 请求方法 */
    public static final String REQUEST_METHOD = "requestMethod";

    /** 服务名称 */
    public static final String SERVICE_NAME = "serviceName";

    /** 插件名称 */
    public static final String PLUGIN_NAME = "pluginName";
}
