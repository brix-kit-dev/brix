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
package io.brix.platform.tenant.exception;

/**
 * Exception thrown when a cross-tenant reference is detected.
 * 
 * <p>This exception indicates that an entity is attempting to reference
 * another entity that belongs to a different tenant. Cross-tenant references
 * violate data isolation principles and must be prevented.</p>
 * 
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Implementation Layer (platform-tenant module)</p>
 * 
 * <h3>Security Model</h3>
 * <p>Cross-tenant references are a critical security violation:</p>
 * <ul>
 *   <li>They can expose data from other tenants</li>
 *   <li>They can allow unauthorized modifications</li>
 *   <li>They indicate potential SQL injection or API misuse</li>
 * </ul>
 * 
 * <h3>HTTP Response</h3>
 * <p>This exception should be mapped to HTTP 400 (Bad Request) by the
 * global exception handler. The response should indicate that the reference
 * is invalid but NOT reveal tenant-specific details.</p>
 * 
 * <h3>Example Scenarios</h3>
 * <pre>{@code
 * // Scenario 1: Creating order with product from different tenant
 * Order order = new Order();
 * order.setTenantId("tenant-A");
 * order.setProductId(productFromTenantB.getId()); // INVALID!
 * 
 * // This should throw CrossTenantReferenceException
 * validator.validateReference(order, product, "productId");
 * }</pre>
 * 
 * <h3>Logging</h3>
 * <p>All cross-tenant reference attempts should be logged for security
 * auditing. Include source entity, target entity, and both tenant IDs.</p>
 * 
 * @author Brix Platform Team
 * @since 3.1.0
 * @see io.brix.platform.tenant.validation.TenantReferenceValidator
 */
public class CrossTenantReferenceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Error code for HTTP response.
     */
    public static final String ERROR_CODE = "CROSS_TENANT_REFERENCE";

    /**
     * The type of the source entity (e.g., "Order").
     */
    private final String sourceEntityType;

    /**
     * The ID of the source entity.
     */
    private final Object sourceEntityId;

    /**
     * The tenant ID of the source entity.
     */
    private final String sourceTenantId;

    /**
     * The type of the referenced entity (e.g., "Product").
     */
    private final String targetEntityType;

    /**
     * The ID of the referenced entity.
     */
    private final Object targetEntityId;

    /**
     * The tenant ID of the referenced entity.
     */
    private final String targetTenantId;

    /**
     * The name of the field that contains the reference.
     */
    private final String fieldName;

    /**
     * Creates a CrossTenantReferenceException with full details.
     * 
     * @param sourceEntityType the type of the source entity
     * @param sourceEntityId the ID of the source entity
     * @param sourceTenantId the tenant ID of the source entity
     * @param targetEntityType the type of the referenced entity
     * @param targetEntityId the ID of the referenced entity
     * @param targetTenantId the tenant ID of the referenced entity
     * @param fieldName the field containing the reference
     */
    public CrossTenantReferenceException(
            String sourceEntityType,
            Object sourceEntityId,
            String sourceTenantId,
            String targetEntityType,
            Object targetEntityId,
            String targetTenantId,
            String fieldName) {
        super(buildMessage(sourceEntityType, sourceTenantId, targetEntityType, targetTenantId, fieldName));
        this.sourceEntityType = sourceEntityType;
        this.sourceEntityId = sourceEntityId;
        this.sourceTenantId = sourceTenantId;
        this.targetEntityType = targetEntityType;
        this.targetEntityId = targetEntityId;
        this.targetTenantId = targetTenantId;
        this.fieldName = fieldName;
    }

    /**
     * Creates a CrossTenantReferenceException with a simple message.
     * 
     * @param message the exception message
     */
    public CrossTenantReferenceException(String message) {
        super(message);
        this.sourceEntityType = null;
        this.sourceEntityId = null;
        this.sourceTenantId = null;
        this.targetEntityType = null;
        this.targetEntityId = null;
        this.targetTenantId = null;
        this.fieldName = null;
    }

    /**
     * Builds a detailed message for logging.
     */
    private static String buildMessage(
            String sourceEntityType,
            String sourceTenantId,
            String targetEntityType,
            String targetTenantId,
            String fieldName) {
        return String.format(
            "Cross-tenant reference detected: %s (tenant=%s) references %s (tenant=%s) via field '%s'",
            sourceEntityType, sourceTenantId,
            targetEntityType, targetTenantId,
            fieldName
        );
    }

    /**
     * Returns the type of the source entity.
     * @return source entity type
     */
    public String getSourceEntityType() {
        return sourceEntityType;
    }

    /**
     * Returns the ID of the source entity.
     * @return source entity ID
     */
    public Object getSourceEntityId() {
        return sourceEntityId;
    }

    /**
     * Returns the tenant ID of the source entity.
     * @return source tenant ID
     */
    public String getSourceTenantId() {
        return sourceTenantId;
    }

    /**
     * Returns the type of the referenced entity.
     * @return target entity type
     */
    public String getTargetEntityType() {
        return targetEntityType;
    }

    /**
     * Returns the ID of the referenced entity.
     * @return target entity ID
     */
    public Object getTargetEntityId() {
        return targetEntityId;
    }

    /**
     * Returns the tenant ID of the referenced entity.
     * @return target tenant ID
     */
    public String getTargetTenantId() {
        return targetTenantId;
    }

    /**
     * Returns the field name containing the reference.
     * @return field name
     */
    public String getFieldName() {
        return fieldName;
    }

    /**
     * Returns the error code for HTTP response.
     * @return "CROSS_TENANT_REFERENCE"
     */
    public String getErrorCode() {
        return ERROR_CODE;
    }

    /**
     * Returns a sanitized message suitable for HTTP response.
     * 
     * <p>This message omits tenant and entity IDs to prevent
     * information leakage in API responses.</p>
     * 
     * @return a user-safe error message
     */
    public String getSanitizedMessage() {
        if (fieldName != null) {
            return String.format("Invalid reference in field '%s': referenced entity not found", fieldName);
        }
        return "Invalid entity reference: referenced entity not found";
    }
}
