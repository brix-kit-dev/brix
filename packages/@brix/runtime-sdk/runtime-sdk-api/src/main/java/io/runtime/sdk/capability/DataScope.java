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
package io.runtime.sdk.capability;

import java.io.Serializable;
import java.util.Objects;

/**
 * Data Permission Scope
 * 
 * <p>Defines the data boundaries accessible by users for row-level data permission control.
 * DataScope is an immutable object containing scope type and scope value.</p>
 * 
 * <h3>Common Scope Types</h3>
 * <ul>
 *   <li><b>DEPARTMENT</b>: Department scope</li>
 *   <li><b>ORGANIZATION</b>: Organization scope</li>
 *   <li><b>REGION</b>: Region scope</li>
 *   <li><b>SELF</b>: Only own data</li>
 *   <li><b>ALL</b>: All data</li>
 * </ul>
 * 
 * <h3>Usage Example</h3>
 * <pre>{@code
 * // Filter by data scope in service layer
 * Set<DataScope> scopes = authContext.getAuthorizedScopes();
 * 
 * for (DataScope scope : scopes) {
 *     if ("DEPARTMENT".equals(scope.getType())) {
 *         // Add department filter condition
 *         query.where("department_id", scope.getValue());
 *     }
 * }
 * }</pre>
 * 
 * @author Runtime SDK Team
 * @since 3.0.0
 * @see AuthContextCapability#getAuthorizedScopes()
 */
public final class DataScope implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Scope type
     * 
     * <p>e.g., "DEPARTMENT", "ORGANIZATION", "REGION"</p>
     */
    private final String type;

    /**
     * Scope value
     * 
     * <p>The specific scope identifier, such as department ID, organization ID, region code</p>
     */
    private final String value;

    /**
     * Creates a data permission scope
     * 
     * @param type  the scope type, cannot be null or blank
     * @param value the scope value, cannot be null or blank
     * @throws IllegalArgumentException if parameters are null or blank
     */
    public DataScope(String type, String value) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("DataScope type cannot be null or blank");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("DataScope value cannot be null or blank");
        }
        this.type = type;
        this.value = value;
    }

    /**
     * Gets the scope type
     * 
     * @return the scope type
     */
    public String getType() {
        return type;
    }

    /**
     * Gets the scope value
     * 
     * @return the scope value
     */
    public String getValue() {
        return value;
    }

    /**
     * Creates a department scope
     * 
     * @param departmentId the department ID
     * @return a DataScope instance
     */
    public static DataScope department(String departmentId) {
        return new DataScope("DEPARTMENT", departmentId);
    }

    /**
     * Creates an organization scope
     * 
     * @param organizationId the organization ID
     * @return a DataScope instance
     */
    public static DataScope organization(String organizationId) {
        return new DataScope("ORGANIZATION", organizationId);
    }

    /**
     * Creates a region scope
     * 
     * @param regionCode the region code
     * @return a DataScope instance
     */
    public static DataScope region(String regionCode) {
        return new DataScope("REGION", regionCode);
    }

    /**
     * Creates a self data scope
     * 
     * @param userId the user ID
     * @return a DataScope instance
     */
    public static DataScope self(String userId) {
        return new DataScope("SELF", userId);
    }

    /**
     * Creates an all data scope
     * 
     * @return a DataScope instance
     */
    public static DataScope all() {
        return new DataScope("ALL", "*");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataScope dataScope = (DataScope) o;
        return Objects.equals(type, dataScope.type) && Objects.equals(value, dataScope.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, value);
    }

    @Override
    public String toString() {
        return String.format("DataScope[type=%s, value=%s]", type, value);
    }
}
