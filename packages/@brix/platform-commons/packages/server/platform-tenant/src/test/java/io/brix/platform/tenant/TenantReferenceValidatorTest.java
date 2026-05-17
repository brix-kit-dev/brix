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
package io.brix.platform.tenant;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.brix.platform.common.tenant.TenantContext;
import io.brix.platform.tenant.exception.CrossTenantReferenceException;
import io.brix.platform.tenant.exception.InvalidReferenceException;
import io.brix.platform.tenant.validation.TenantReferenceValidator;

/**
 * Unit tests for {@link TenantReferenceValidator}.
 *
 * <p>This test class validates the reference consistency validation logic,
 * ensuring that cross-tenant references are properly detected and rejected.
 *
 * <h3>Test Categories</h3>
 * <ul>
 *   <li>Single Reference Tests - Tests for validating individual entity references</li>
 *   <li>Collection Reference Tests - Tests for validating collections of references</li>
 *   <li>Context-Based Tests - Tests for validation using TenantContext</li>
 *   <li>Mode Tests - Tests for strict vs lenient mode behavior</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 */
@DisplayName("TenantReferenceValidator Tests")
class TenantReferenceValidatorTest {

    private TenantReferenceValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TenantReferenceValidator();
    }

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    // =========================================================================
    // Single Reference Validation Tests
    // =========================================================================

    @Nested
    @DisplayName("Single Reference Validation Tests")
    class SingleReferenceTests {

        @Test
        @DisplayName("should pass validation when tenant IDs match")
        void shouldPassWhenTenantIdsMatch() {
            // Given
            String sameTenant = "tenant-123";

            // When & Then - no exception
            assertDoesNotThrow(() -> validator.validateReference(
                sameTenant,         // source tenant
                sameTenant,         // target tenant (same)
                "Order", 1L,        // source entity
                "Product", 100L,    // target entity
                "productId"         // field name
            ));
        }

        @Test
        @DisplayName("should throw CrossTenantReferenceException when tenant IDs differ")
        void shouldThrowWhenTenantIdsDiffer() {
            // Given
            String sourceTenant = "tenant-A";
            String targetTenant = "tenant-B";

            // When & Then
            CrossTenantReferenceException exception = assertThrows(
                CrossTenantReferenceException.class,
                () -> validator.validateReference(
                    sourceTenant,
                    targetTenant,
                    "Order", 1L,
                    "Product", 100L,
                    "productId"
                )
            );

            assertEquals(sourceTenant, exception.getSourceTenantId());
            assertEquals(targetTenant, exception.getTargetTenantId());
            assertEquals("Order", exception.getSourceEntityType());
            assertEquals("Product", exception.getTargetEntityType());
            assertEquals("productId", exception.getFieldName());
        }

        @Test
        @DisplayName("should skip validation when source tenant is null")
        void shouldSkipValidationWhenSourceTenantNull() {
            // Given
            String targetTenant = "tenant-B";

            // When & Then - should not throw, just skip validation
            assertDoesNotThrow(() -> validator.validateReference(
                null,               // source tenant is null
                targetTenant,
                "Order", 1L,
                "Product", 100L,
                "productId"
            ));
        }

        @Test
        @DisplayName("should skip validation when target tenant is null")
        void shouldSkipValidationWhenTargetTenantNull() {
            // Given
            String sourceTenant = "tenant-A";

            // When & Then - should not throw, just skip validation
            assertDoesNotThrow(() -> validator.validateReference(
                sourceTenant,
                null,               // target tenant is null
                "Order", 1L,
                "Product", 100L,
                "productId"
            ));
        }
    }

    // =========================================================================
    // Context-Based Validation Tests
    // =========================================================================

    @Nested
    @DisplayName("Context-Based Validation Tests")
    class ContextBasedTests {

        @Test
        @DisplayName("should validate using current tenant context")
        void shouldValidateUsingTenantContext() {
            // Given
            String currentTenant = "tenant-123";
            TenantContext.setTenantId(currentTenant);

            // When & Then - target matches context
            assertDoesNotThrow(() -> validator.validateReferenceFromContext(
                currentTenant,      // target tenant same as context
                "Order", 1L,
                "Product", 100L,
                "productId"
            ));
        }

        @Test
        @DisplayName("should throw when target tenant differs from context")
        void shouldThrowWhenTargetDiffersFromContext() {
            // Given
            String currentTenant = "tenant-A";
            String targetTenant = "tenant-B";
            TenantContext.setTenantId(currentTenant);

            // When & Then
            assertThrows(
                CrossTenantReferenceException.class,
                () -> validator.validateReferenceFromContext(
                    targetTenant,
                    "Order", 1L,
                    "Product", 100L,
                    "productId"
                )
            );
        }

        @Test
        @DisplayName("should throw IllegalStateException when context not set")
        void shouldThrowWhenContextNotSet() {
            // Given - no tenant context set
            TenantContext.clear();

            // When & Then
            assertThrows(
                IllegalStateException.class,
                () -> validator.validateReferenceFromContext(
                    "any-tenant",
                    "Order", 1L,
                    "Product", 100L,
                    "productId"
                )
            );
        }
    }

    // =========================================================================
    // Entity Reference Validation Tests
    // =========================================================================

    @Nested
    @DisplayName("Entity Reference Validation Tests")
    class EntityReferenceTests {

        @Test
        @DisplayName("should throw InvalidReferenceException when entity is null")
        void shouldThrowWhenEntityNull() {
            // When & Then
            InvalidReferenceException exception = assertThrows(
                InvalidReferenceException.class,
                () -> validator.validateEntityReference(
                    null,               // entity is null
                    "tenant-A",
                    entity -> "tenant-X", // extractor (won't be called)
                    "Product",
                    100L,
                    "productId"
                )
            );

            assertEquals("Product", exception.getEntityType());
            assertEquals(100L, exception.getReferenceValue());
            assertEquals("productId", exception.getFieldName());
        }

        @Test
        @DisplayName("should return entity when validation passes")
        void shouldReturnEntityWhenValid() {
            // Given
            String tenantId = "tenant-123";
            TestEntity entity = new TestEntity(1L, tenantId);

            // When
            TestEntity result = validator.validateEntityReference(
                entity,
                tenantId,
                TestEntity::getTenantId,
                "Product",
                1L,
                "productId"
            );

            // Then
            assertSame(entity, result);
        }

        @Test
        @DisplayName("should throw CrossTenantReferenceException when entity tenant differs")
        void shouldThrowWhenEntityTenantDiffers() {
            // Given
            TestEntity entity = new TestEntity(1L, "tenant-B");

            // When & Then
            assertThrows(
                CrossTenantReferenceException.class,
                () -> validator.validateEntityReference(
                    entity,
                    "tenant-A",             // expected tenant
                    TestEntity::getTenantId, // actual tenant = "tenant-B"
                    "Product",
                    1L,
                    "productId"
                )
            );
        }
    }

    // =========================================================================
    // Collection Reference Validation Tests
    // =========================================================================

    @Nested
    @DisplayName("Collection Reference Validation Tests")
    class CollectionReferenceTests {

        @Test
        @DisplayName("should pass validation for empty collection")
        void shouldPassForEmptyCollection() {
            // Given
            List<TestEntity> emptyList = Collections.emptyList();

            // When & Then - no exception
            assertDoesNotThrow(() -> validator.validateCollectionReferences(
                emptyList,
                "tenant-A",
                TestEntity::getTenantId,
                TestEntity::getId,
                "Product",
                "productIds"
            ));
        }

        @Test
        @DisplayName("should pass validation when all entities match tenant")
        void shouldPassWhenAllEntitiesMatch() {
            // Given
            String tenantId = "tenant-123";
            List<TestEntity> entities = Arrays.asList(
                new TestEntity(1L, tenantId),
                new TestEntity(2L, tenantId),
                new TestEntity(3L, tenantId)
            );

            // When & Then - no exception
            assertDoesNotThrow(() -> validator.validateCollectionReferences(
                entities,
                tenantId,
                TestEntity::getTenantId,
                TestEntity::getId,
                "Product",
                "productIds"
            ));
        }

        @Test
        @DisplayName("should throw on first mismatch in collection")
        void shouldThrowOnFirstMismatch() {
            // Given
            String expectedTenant = "tenant-A";
            List<TestEntity> entities = Arrays.asList(
                new TestEntity(1L, expectedTenant),      // OK
                new TestEntity(2L, "tenant-B"),          // MISMATCH
                new TestEntity(3L, expectedTenant)       // Won't be checked
            );

            // When & Then
            CrossTenantReferenceException exception = assertThrows(
                CrossTenantReferenceException.class,
                () -> validator.validateCollectionReferences(
                    entities,
                    expectedTenant,
                    TestEntity::getTenantId,
                    TestEntity::getId,
                    "Product",
                    "productIds"
                )
            );

            assertEquals("tenant-B", exception.getTargetTenantId());
            assertEquals(2L, exception.getTargetEntityId());
        }

        @Test
        @DisplayName("should handle null collection gracefully")
        void shouldHandleNullCollection() {
            // When & Then - no exception for null collection
            assertDoesNotThrow(() -> validator.validateCollectionReferences(
                null,
                "tenant-A",
                TestEntity::getTenantId,
                TestEntity::getId,
                "Product",
                "productIds"
            ));
        }
    }

    // =========================================================================
    // Validation Mode Tests
    // =========================================================================

    @Nested
    @DisplayName("Validation Mode Tests")
    class ValidationModeTests {

        @Test
        @DisplayName("should throw in strict mode (default)")
        void shouldThrowInStrictMode() {
            // Given - strict mode is default
            TenantReferenceValidator strictValidator = new TenantReferenceValidator(true);

            // When & Then
            assertThrows(
                CrossTenantReferenceException.class,
                () -> strictValidator.validateReference(
                    "tenant-A",
                    "tenant-B",
                    "Order", 1L,
                    "Product", 100L,
                    "productId"
                )
            );
        }

        @Test
        @DisplayName("should only log in lenient mode")
        void shouldOnlyLogInLenientMode() {
            // Given - lenient mode
            TenantReferenceValidator lenientValidator = new TenantReferenceValidator(false);

            // When & Then - should NOT throw
            assertDoesNotThrow(() -> lenientValidator.validateReference(
                "tenant-A",
                "tenant-B",
                "Order", 1L,
                "Product", 100L,
                "productId"
            ));
        }

        @Test
        @DisplayName("lenient mode entity validation should return entity")
        void lenientModeEntityValidationShouldReturnEntity() {
            // Given
            TenantReferenceValidator lenientValidator = new TenantReferenceValidator(false);
            TestEntity entity = new TestEntity(1L, "tenant-B");

            // When - should return entity without throwing
            TestEntity result = lenientValidator.validateEntityReference(
                entity,
                "tenant-A",             // different tenant
                TestEntity::getTenantId,
                "Product",
                1L,
                "productId"
            );

            // Then
            assertNotNull(result);
            assertSame(entity, result);
        }
    }

    // =========================================================================
    // Test Helper Class
    // =========================================================================

    /**
     * Simple test entity for validation testing.
     */
    private static class TestEntity {
        private final Long id;
        private final String tenantId;

        TestEntity(Long id, String tenantId) {
            this.id = id;
            this.tenantId = tenantId;
        }

        Long getId() {
            return id;
        }

        String getTenantId() {
            return tenantId;
        }
    }
}
