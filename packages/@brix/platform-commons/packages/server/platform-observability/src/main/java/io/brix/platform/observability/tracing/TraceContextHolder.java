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
package io.brix.platform.observability.tracing;

/**
 * Trace context holder.
 * <p>
 * Uses ThreadLocal to store trace information for the current request.
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
     * Trace context.
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
