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
package io.brix.platform.tenant.validation;

import io.brix.platform.common.tenant.TenantContext;
import io.brix.platform.tenant.exception.CrossTenantReferenceException;
import io.brix.platform.tenant.exception.InvalidReferenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.function.Function;

/**
 * Validator for ensuring tenant reference consistency.
 * 
 * <p>This validator ensures that entity references do not cross tenant boundaries.
 * It provides various validation methods for different scenarios, including
 * single references, collection references, and bulk validation.</p>
 * 
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Implementation Layer (platform-tenant module)</p>
 * 
 * <h3>Design Rationale</h3>
 * <p>In a multi-tenant system, cross-tenant references are a critical security
 * vulnerability. This validator provides a consistent way to detect and prevent
 * such references at the service layer, before data reaches the database.</p>
 * 
 * <h3>Usage Pattern</h3>
 * <p>The validator should be called in service layer methods that create or
 * update entities with foreign key references:</p>
 * <pre>{@code
 * @Service
 * public class OrderService {
 *     
 *     private final TenantReferenceValidator validator;
 *     private final ProductRepository productRepository;
 *     
 *     public Order createOrder(CreateOrderRequest request) {
 *         // Validate product reference belongs to current tenant
 *         Product product = productRepository.findById(request.getProductId())
 *             .orElseThrow(() -> new InvalidReferenceException(
 *                 "Order", null, "productId", request.getProductId(), "Product"));
 *         
 *         validator.validateReference(
 *             TenantContext.getCurrentTenantId(),  // Source tenant (from context)
 *             product.getTenantId(),                // Target tenant (from product)
 *             "Order", null,                        // Source entity info
 *             "Product", product.getId(),           // Target entity info
 *             "productId"                           // Field name
 *         );
 *         
 *         // Safe to proceed - product belongs to correct tenant
 *         Order order = new Order();
 *         order.setProductId(product.getId());
 *         // ...
 *     }
 * }
 * }</pre>
 * 
 * <h3>Validation Modes</h3>
 * <ul>
 *   <li><b>Strict mode (default):</b> Throws exception on any cross-tenant reference</li>
 *   <li><b>Lenient mode:</b> Only logs warning, does not throw (for migration scenarios)</li>
 * </ul>
 * 
 * <h3>Performance Considerations</h3>
 * <ul>
 *   <li>Validation is O(1) for single references</li>
 *   <li>Bulk validation is O(n) where n is collection size</li>
 *   <li>All operations are synchronous and thread-safe</li>
 * </ul>
 * 
 * @author Brix Platform Team
 * @since 3.1.0
 * @see CrossTenantReferenceException
 * @see InvalidReferenceException
 */
public class TenantReferenceValidator {

    private static final Logger log = LoggerFactory.getLogger(TenantReferenceValidator.class);

    /**
     * Whether to throw exceptions on cross-tenant references.
     * When false, only logs warnings (useful for migration).
     */
    private final boolean strictMode;

    /**
     * Creates a TenantReferenceValidator in strict mode.
     */
    public TenantReferenceValidator() {
        this(true);
    }

    /**
     * Creates a TenantReferenceValidator with specified mode.
     * 
     * @param strictMode true to throw exceptions, false to only log warnings
     */
    public TenantReferenceValidator(boolean strictMode) {
        this.strictMode = strictMode;
    }

    /**
     * Validates that a reference does not cross tenant boundaries.
     * 
     * <p>This method compares the source tenant (typically from TenantContext)
     * with the target entity's tenant and throws an exception if they differ.</p>
     * 
     * @param sourceTenantId the tenant ID of the source entity (or current context)
     * @param targetTenantId the tenant ID of the referenced entity
     * @param sourceEntityType the type name of the source entity
     * @param sourceEntityId the ID of the source entity (may be null for new entities)
     * @param targetEntityType the type name of the referenced entity
     * @param targetEntityId the ID of the referenced entity
     * @param fieldName the name of the field containing the reference
     * @throws CrossTenantReferenceException if tenants don't match (in strict mode)
     */
    public void validateReference(
            String sourceTenantId,
            String targetTenantId,
            String sourceEntityType,
            Object sourceEntityId,
            String targetEntityType,
            Object targetEntityId,
            String fieldName) {
        
        // Null checks - null tenant IDs should not be compared
        if (sourceTenantId == null || targetTenantId == null) {
            log.warn("Cannot validate reference with null tenant ID: source={}, target={}",
                sourceTenantId, targetTenantId);
            return;
        }

        // Check if tenants match
        if (!sourceTenantId.equals(targetTenantId)) {
            String message = String.format(
                "Cross-tenant reference: %s (tenant=%s) -> %s (tenant=%s) via '%s'",
                sourceEntityType, sourceTenantId,
                targetEntityType, targetTenantId,
                fieldName
            );

            if (strictMode) {
                log.error("SECURITY: {}", message);
                throw new CrossTenantReferenceException(
                    sourceEntityType, sourceEntityId, sourceTenantId,
                    targetEntityType, targetEntityId, targetTenantId,
                    fieldName
                );
            } else {
                log.warn("Cross-tenant reference detected (lenient mode): {}", message);
            }
        }
    }

    /**
     * Validates a reference using current tenant context as source.
     * 
     * <p>This is a convenience method that extracts the source tenant from
     * {@link TenantContext#getCurrentTenantId()}.</p>
     * 
     * @param targetTenantId the tenant ID of the referenced entity
     * @param sourceEntityType the type name of the source entity
     * @param sourceEntityId the ID of the source entity
     * @param targetEntityType the type name of the referenced entity
     * @param targetEntityId the ID of the referenced entity
     * @param fieldName the field containing the reference
     * @throws CrossTenantReferenceException if tenants don't match
     * @throws IllegalStateException if tenant context is not set
     */
    public void validateReferenceFromContext(
            String targetTenantId,
            String sourceEntityType,
            Object sourceEntityId,
            String targetEntityType,
            Object targetEntityId,
            String fieldName) {
        
        String currentTenantId = TenantContext.getCurrentTenantId();
        validateReference(
            currentTenantId, targetTenantId,
            sourceEntityType, sourceEntityId,
            targetEntityType, targetEntityId,
            fieldName
        );
    }

    /**
     * Validates that an entity exists and belongs to the expected tenant.
     * 
     * <p>This method combines existence check with tenant validation.
     * If the entity is null, it throws {@link InvalidReferenceException}.
     * If the entity exists but belongs to wrong tenant, it throws
     * {@link CrossTenantReferenceException}.</p>
     * 
     * @param <T> the entity type
     * @param entity the entity to validate (may be null)
     * @param expectedTenantId the expected tenant ID
     * @param tenantIdExtractor function to extract tenant ID from entity
     * @param entityType the type name for error messages
     * @param entityId the ID that was used to look up the entity
     * @param fieldName the field containing the reference
     * @return the validated entity (never null)
     * @throws InvalidReferenceException if entity is null
     * @throws CrossTenantReferenceException if entity belongs to different tenant
     */
    public <T> T validateEntityReference(
            T entity,
            String expectedTenantId,
            Function<T, String> tenantIdExtractor,
            String entityType,
            Object entityId,
            String fieldName) {
        
        // Check existence
        if (entity == null) {
            throw new InvalidReferenceException(
                entityType, null, fieldName, entityId
            );
        }

        // Check tenant
        String actualTenantId = tenantIdExtractor.apply(entity);
        if (expectedTenantId != null && !expectedTenantId.equals(actualTenantId)) {
            if (strictMode) {
                log.error("SECURITY: Cross-tenant reference to {} id={}", entityType, entityId);
                throw new CrossTenantReferenceException(
                    "context", null, expectedTenantId,
                    entityType, entityId, actualTenantId,
                    fieldName
                );
            } else {
                log.warn("Cross-tenant reference detected (lenient mode): {} id={}", entityType, entityId);
            }
        }

        return entity;
    }

    /**
     * Validates a collection of references.
     * 
     * <p>This method validates that all entities in a collection belong
     * to the expected tenant. It fails fast on the first violation.</p>
     * 
     * @param <T> the entity type
     * @param entities the collection of entities to validate
     * @param expectedTenantId the expected tenant ID
     * @param tenantIdExtractor function to extract tenant ID from entity
     * @param idExtractor function to extract entity ID for error messages
     * @param entityType the type name for error messages
     * @param fieldName the field containing the references
     * @throws CrossTenantReferenceException if any entity belongs to different tenant
     */
    public <T> void validateCollectionReferences(
            Collection<T> entities,
            String expectedTenantId,
            Function<T, String> tenantIdExtractor,
            Function<T, Object> idExtractor,
            String entityType,
            String fieldName) {
        
        if (entities == null || entities.isEmpty()) {
            return;
        }

        for (T entity : entities) {
            String actualTenantId = tenantIdExtractor.apply(entity);
            Object entityId = idExtractor.apply(entity);
            
            if (expectedTenantId != null && !expectedTenantId.equals(actualTenantId)) {
                if (strictMode) {
                    log.error("SECURITY: Cross-tenant reference in collection to {} id={}", 
                        entityType, entityId);
                    throw new CrossTenantReferenceException(
                        "context", null, expectedTenantId,
                        entityType, entityId, actualTenantId,
                        fieldName
                    );
                } else {
                    log.warn("Cross-tenant reference detected (lenient mode) in collection: {} id={}", 
                        entityType, entityId);
                }
            }
        }
    }

    /**
     * Validates that a new entity's tenant matches the current context.
     * 
     * <p>This method should be called before persisting new entities to ensure
     * they are assigned to the correct tenant.</p>
     * 
     * @param entityTenantId the tenant ID assigned to the entity
     * @param entityType the type name for error messages
     * @throws IllegalStateException if tenant context is not set
     * @throws IllegalArgumentException if entity tenant doesn't match context
     */
    public void validateNewEntityTenant(String entityTenantId, String entityType) {
        String contextTenantId = TenantContext.getCurrentTenantId();
        
        if (!contextTenantId.equals(entityTenantId)) {
            String message = String.format(
                "New %s has tenant='%s' but context tenant='%s'",
                entityType, entityTenantId, contextTenantId
            );
            log.error("SECURITY: {}", message);
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Returns whether the validator is in strict mode.
     * 
     * @return true if exceptions are thrown, false if only warnings logged
     */
    public boolean isStrictMode() {
        return strictMode;
    }
}
