package io.brix.platform.observability.tracing;

/**
 * 追踪上下文持有
 * <p>
 * 使用 ThreadLocal 存储当前请求的追踪信息
 * </p>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 */
public class TraceContextHolder {

    private static final ThreadLocal<TraceContext> CONTEXT = ThreadLocal.withInitial(TraceContext::new);

    public String getTraceId() {
        return CONTEXT.get().getTraceId();
    }

    public void setTraceId(String traceId) {
        CONTEXT.get().setTraceId(traceId);
    }

    public String getTenantId() {
        return CONTEXT.get().getTenantId();
    }

    public void setTenantId(String tenantId) {
        CONTEXT.get().setTenantId(tenantId);
    }

    public String getUserId() {
        return CONTEXT.get().getUserId();
    }

    public void setUserId(String userId) {
        CONTEXT.get().setUserId(userId);
    }

    public TraceContext get() {
        return CONTEXT.get();
    }

    public void clear() {
        CONTEXT.remove();
    }

    /**
     * 追踪上下
     */
    public static class TraceContext {
        private String traceId;
        private String tenantId;
        private String userId;

        public String getTraceId() {
            return traceId;
        }

        public void setTraceId(String traceId) {
            this.traceId = traceId;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }
    }
}
