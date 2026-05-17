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
 * Exception thrown when an entity reference is invalid.
 * 
 * <p>This exception indicates that a referenced entity does not exist
 * or cannot be found. This differs from {@link CrossTenantReferenceException}
 * which specifically handles cross-tenant violations.</p>
 * 
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Implementation Layer (platform-tenant module)</p>
 * 
 * <h3>Use Cases</h3>
 * <ul>
 *   <li>Referenced entity ID does not exist in database</li>
 *   <li>Referenced entity was deleted (soft or hard)</li>
 *   <li>Referenced entity ID is malformed</li>
 *   <li>Required reference field is null</li>
 * </ul>
 * 
 * <h3>HTTP Response</h3>
 * <p>This exception should be mapped to HTTP 400 (Bad Request) by the
 * global exception handler. The field name should be included to help
 * clients identify which reference is invalid.</p>
 * 
 * <h3>Example Scenarios</h3>
 * <pre>{@code
 * // Scenario 1: Order references non-existent product
 * Order order = new Order();
 * order.setProductId(999999L); // ID does not exist
 * 
 * // This should throw InvalidReferenceException
 * validator.validateReference(order, "productId", Product.class);
 * 
 * // Scenario 2: Order references deleted customer
 * Customer deletedCustomer = customerRepository.findById(customerId);
 * if (deletedCustomer == null) {
 *     throw new InvalidReferenceException("Order", orderId, "customerId", customerId);
 * }
 * }</pre>
 * 
 * <h3>Difference from CrossTenantReferenceException</h3>
 * <table>
 *   <tr><th>Exception</th><th>Cause</th><th>Security Level</th></tr>
 *   <tr>
 *     <td>InvalidReferenceException</td>
 *     <td>Entity doesn't exist</td>
 *     <td>Data integrity issue</td>
 *   </tr>
 *   <tr>
 *     <td>CrossTenantReferenceException</td>
 *     <td>Entity exists but wrong tenant</td>
 *     <td>Security violation</td>
 *   </tr>
 * </table>
 * 
 * @author Brix Platform Team
 * @since 3.1.0
 * @see io.brix.platform.tenant.validation.TenantReferenceValidator
 * @see CrossTenantReferenceException
 */
public class InvalidReferenceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Error code for HTTP response.
     */
    public static final String ERROR_CODE = "INVALID_REFERENCE";

    /**
     * The type of the entity containing the reference.
     */
    private final String entityType;

    /**
     * The ID of the entity containing the reference.
     */
    private final Object entityId;

    /**
     * The name of the field containing the invalid reference.
     */
    private final String fieldName;

    /**
     * The invalid reference value.
     */
    private final Object referenceValue;

    /**
     * The expected type of the referenced entity.
     */
    private final String expectedType;

    /**
     * Creates an InvalidReferenceException with full details.
     * 
     * @param entityType the type of the entity containing the reference
     * @param entityId the ID of the entity containing the reference
     * @param fieldName the field containing the invalid reference
     * @param referenceValue the invalid reference value
     */
    public InvalidReferenceException(
            String entityType,
            Object entityId,
            String fieldName,
            Object referenceValue) {
        this(entityType, entityId, fieldName, referenceValue, null);
    }

    /**
     * Creates an InvalidReferenceException with full details including expected type.
     * 
     * @param entityType the type of the entity containing the reference
     * @param entityId the ID of the entity containing the reference
     * @param fieldName the field containing the invalid reference
     * @param referenceValue the invalid reference value
     * @param expectedType the expected type of the referenced entity
     */
    public InvalidReferenceException(
            String entityType,
            Object entityId,
            String fieldName,
            Object referenceValue,
            String expectedType) {
        super(buildMessage(entityType, fieldName, referenceValue, expectedType));
        this.entityType = entityType;
        this.entityId = entityId;
        this.fieldName = fieldName;
        this.referenceValue = referenceValue;
        this.expectedType = expectedType;
    }

    /**
     * Creates an InvalidReferenceException with a simple message.
     * 
     * @param message the exception message
     */
    public InvalidReferenceException(String message) {
        super(message);
        this.entityType = null;
        this.entityId = null;
        this.fieldName = null;
        this.referenceValue = null;
        this.expectedType = null;
    }

    /**
     * Creates an InvalidReferenceException with message and cause.
     * 
     * @param message the exception message
     * @param cause the underlying cause
     */
    public InvalidReferenceException(String message, Throwable cause) {
        super(message, cause);
        this.entityType = null;
        this.entityId = null;
        this.fieldName = null;
        this.referenceValue = null;
        this.expectedType = null;
    }

    /**
     * Builds a detailed message for logging.
     */
    private static String buildMessage(
            String entityType,
            String fieldName,
            Object referenceValue,
            String expectedType) {
        StringBuilder sb = new StringBuilder();
        sb.append("Invalid reference in ").append(entityType);
        sb.append(".").append(fieldName);
        sb.append(": referenced ");
        if (expectedType != null) {
            sb.append(expectedType).append(" ");
        }
        sb.append("entity with ID '").append(referenceValue).append("' not found");
        return sb.toString();
    }

    /**
     * Returns the type of the entity containing the reference.
     * @return entity type
     */
    public String getEntityType() {
        return entityType;
    }

    /**
     * Returns the ID of the entity containing the reference.
     * @return entity ID
     */
    public Object getEntityId() {
        return entityId;
    }

    /**
     * Returns the field name containing the invalid reference.
     * @return field name
     */
    public String getFieldName() {
        return fieldName;
    }

    /**
     * Returns the invalid reference value.
     * @return reference value
     */
    public Object getReferenceValue() {
        return referenceValue;
    }

    /**
     * Returns the expected type of the referenced entity.
     * @return expected type, or null if not specified
     */
    public String getExpectedType() {
        return expectedType;
    }

    /**
     * Returns the error code for HTTP response.
     * @return "INVALID_REFERENCE"
     */
    public String getErrorCode() {
        return ERROR_CODE;
    }

    /**
     * Returns a sanitized message suitable for HTTP response.
     * 
     * @return a user-safe error message
     */
    public String getSanitizedMessage() {
        if (fieldName != null) {
            return String.format("Invalid reference in field '%s'", fieldName);
        }
        return "Invalid entity reference";
    }
}
