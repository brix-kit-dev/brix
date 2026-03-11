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
 * MDC key constants.
 * <p>
 * Unified MDC key names to ensure log format consistency.
 * </p>
 *
 * @author Brix Platform Authors Platform Team
 * @version 1.0.0
 */
public final class MdcConstants {

    private MdcConstants() {
        // Instantiation not allowed
    }

    /** Trace ID */
    public static final String TRACE_ID = "traceId";

    /** Tenant ID */
    public static final String TENANT_ID = "tenantId";

    /** User ID */
    public static final String USER_ID = "userId";

    /** Correlation ID (Saga transaction) */
    public static final String CORRELATION_ID = "correlationId";

    /** Request path */
    public static final String REQUEST_PATH = "requestPath";

    /** Request method */
    public static final String REQUEST_METHOD = "requestMethod";

    /** Service name */
    public static final String SERVICE_NAME = "serviceName";

    /** Plugin name */
    public static final String PLUGIN_NAME = "pluginName";
}
