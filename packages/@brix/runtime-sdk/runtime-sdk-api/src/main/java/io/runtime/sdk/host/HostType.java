/*
 * Copyright 2026 Runtime SDK Authors
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
package io.runtime.sdk.host;

/**
 * Host Type Enumeration
 * 
 * <p>Defines different types of Host implementations, each with different capability support and use cases.</p>
 * 
 * <h3>Type Description</h3>
 * <table border="1">
 *   <tr><th>Type</th><th>Capability Level</th><th>Typical Usage</th></tr>
 *   <tr><td>FULL_PRODUCT</td><td>Complete</td><td>Standalone deployed complete product</td></tr>
 *   <tr><td>EMBEDDED</td><td>Streamlined</td><td>Embedded in customer systems</td></tr>
 *   <tr><td>STANDALONE</td><td>Minimal</td><td>Local development and testing</td></tr>
 *   <tr><td>TEST</td><td>Mock</td><td>Unit testing</td></tr>
 * </table>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see Host#getType()
 */
public enum HostType {

    /**
     * Full Product Host
     * 
     * <p>Provides complete implementation of all capabilities, including:</p>
     * <ul>
     *   <li>Kafka EventBus</li>
     *   <li>Redis StateStore</li>
     *   <li>Complete Observability (OpenTelemetry)</li>
     *   <li>JWT Authentication</li>
     *   <li>All optional capabilities</li>
     * </ul>
     */
    FULL_PRODUCT("full-product", "Full Product Mode"),

    /**
     * Embedded Host
     * 
     * <p>Streamlined capability implementation, suitable for embedding in customer systems:</p>
     * <ul>
     *   <li>HTTP Webhook EventBus</li>
     *   <li>Local memory StateStore</li>
     *   <li>Delegated Auth</li>
     *   <li>Basic Observability</li>
     * </ul>
     */
    EMBEDDED("embedded", "Embedded Mode"),

    /**
     * Standalone Host
     * 
     * <p>Minimal implementation for local development:</p>
     * <ul>
     *   <li>In-memory EventBus</li>
     *   <li>In-memory StateStore</li>
     *   <li>Simple Authentication</li>
     *   <li>Console logging</li>
     * </ul>
     */
    STANDALONE("standalone", "Standalone Mode"),

    /**
     * Test Host
     * 
     * <p>Mock implementation for unit testing:</p>
     * <ul>
     *   <li>All capabilities are Mock implementations</li>
     *   <li>Supports assertions and verification</li>
     *   <li>Configurable behavior</li>
     * </ul>
     */
    TEST("test", "Test Mode");

    /**
     * Type identifier
     */
    private final String code;

    /**
     * Type description
     */
    private final String description;

    HostType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * Get type identifier
     * 
     * @return type identifier
     */
    public String getCode() {
        return code;
    }

    /**
     * Get type description
     * 
     * @return type description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Find type by identifier
     * 
     * @param code type identifier
     * @return corresponding HostType, or null if not found
     */
    public static HostType fromCode(String code) {
        for (HostType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}
