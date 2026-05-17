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

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import io.brix.platform.tenant.exception.TenantMismatchException;
import io.brix.platform.tenant.resolver.HeaderTenantResolver;
import io.brix.platform.tenant.resolver.TenantResolver;
import io.brix.platform.tenant.resolver.TenantResolverChain;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Unit tests for {@link TenantResolverChain}.
 *
 * <p>This test class validates the resolver chain behavior including priority-based
 * resolution, conflict detection, and edge case handling.
 *
 * <h3>Test Categories</h3>
 * <ul>
 *   <li>Priority Resolution Tests - Tests that resolvers are processed in correct order</li>
 *   <li>Conflict Detection Tests - Tests for tenant mismatch detection between sources</li>
 *   <li>Edge Case Tests - Tests for empty chain, no matching resolver, etc.</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TenantResolverChain Tests")
class TenantResolverChainTest {

    @Mock
    private HttpServletRequest mockRequest;

    private TenantResolverChain chain;

    @BeforeEach
    void setUp() {
        chain = new TenantResolverChain();
    }

    // =========================================================================
    // Priority Resolution Tests
    // =========================================================================

    @Nested
    @DisplayName("Priority Resolution Tests")
    class PriorityResolutionTests {

        /**
         * For priority tests, we use conflict detection disabled to focus on priority logic.
         */
        private TenantResolverChain noConflictChain;

        @BeforeEach
        void setPriorityTestChain() {
            noConflictChain = new TenantResolverChain(false);
        }

        @Test
        @DisplayName("should resolve tenant from highest priority resolver")
        void shouldResolveFromHighestPriorityResolver() {
            // Given - create resolvers with different priorities
            // Use lenient mocks because lower priority resolver may not be called
            TenantResolver highPriority = createMockResolverLenient("high", 10, "tenant-high");
            TenantResolver lowPriority = createMockResolverLenient("low", 100, "tenant-low");

            noConflictChain.addResolver(lowPriority)
                           .addResolver(highPriority); // Added second but should be tried first

            // When
            Optional<String> result = noConflictChain.resolve(mockRequest);

            // Then
            assertTrue(result.isPresent());
            assertEquals("tenant-high", result.get());
        }

        @Test
        @DisplayName("should fall back to lower priority resolver when higher returns empty")
        void shouldFallBackToLowerPriorityResolver() {
            // Given - use lenient since some stubbing may not be invoked
            TenantResolver highPriority = createMockResolverLenient("high", 10, null); // Returns empty
            TenantResolver lowPriority = createMockResolverLenient("low", 100, "tenant-low");

            noConflictChain.addResolver(highPriority)
                           .addResolver(lowPriority);

            // When
            Optional<String> result = noConflictChain.resolve(mockRequest);

            // Then
            assertTrue(result.isPresent());
            assertEquals("tenant-low", result.get());
        }

        @Test
        @DisplayName("should skip resolvers that do not support the request")
        void shouldSkipUnsupportedResolvers() {
            // Given
            TenantResolver unsupported = mock(TenantResolver.class);
            when(unsupported.supports(mockRequest)).thenReturn(false);
            when(unsupported.getOrder()).thenReturn(10); // Highest priority
            lenient().when(unsupported.getName()).thenReturn("UnsupportedResolver");

            TenantResolver supported = createMockResolverLenient("supported", 100, "tenant-supported");

            noConflictChain.addResolver(unsupported)
                           .addResolver(supported);

            // When
            Optional<String> result = noConflictChain.resolve(mockRequest);

            // Then
            assertTrue(result.isPresent());
            assertEquals("tenant-supported", result.get());
            verify(unsupported, never()).resolve(any()); // Should not attempt resolution
        }

        @Test
        @DisplayName("should process resolvers in ascending order priority")
        void shouldProcessInAscendingOrderPriority() {
            // Given - add resolvers in reverse order
            // Use lenient mocks since not all may be called
            TenantResolver priority100 = createMockResolverLenient("p100", 100, "tenant-100");
            TenantResolver priority50 = createMockResolverLenient("p50", 50, "tenant-50");
            TenantResolver priority0 = createMockResolverLenient("p0", 0, "tenant-0");

            noConflictChain.addResolver(priority100)
                           .addResolver(priority50)
                           .addResolver(priority0);

            // When
            Optional<String> result = noConflictChain.resolve(mockRequest);

            // Then
            assertTrue(result.isPresent());
            assertEquals("tenant-0", result.get()); // Priority 0 should be resolved first
        }
    }

    // =========================================================================
    // Conflict Detection Tests
    // =========================================================================

    @Nested
    @DisplayName("Conflict Detection Tests")
    class ConflictDetectionTests {

        @Test
        @DisplayName("should throw TenantMismatchException when resolvers return different tenants")
        void shouldThrowExceptionOnConflict() {
            // Given - two resolvers that return different tenants
            TenantResolver primary = createMockResolver("jwt", 0, "tenant-from-jwt");
            TenantResolver secondary = createMockResolver("header", 100, "tenant-from-header");

            chain.addResolver(primary)
                 .addResolver(secondary);

            // When & Then
            TenantMismatchException exception = assertThrows(
                TenantMismatchException.class,
                () -> chain.resolve(mockRequest)
            );

            assertEquals("tenant-from-jwt", exception.getPrimaryTenant());
            assertEquals("tenant-from-header", exception.getConflictingTenant());
        }

        @Test
        @DisplayName("should not throw exception when all resolvers return same tenant")
        void shouldNotThrowWhenAllResolversAgree() {
            // Given - two resolvers that return the same tenant
            TenantResolver primary = createMockResolver("jwt", 0, "same-tenant");
            TenantResolver secondary = createMockResolver("header", 100, "same-tenant");

            chain.addResolver(primary)
                 .addResolver(secondary);

            // When
            Optional<String> result = chain.resolve(mockRequest);

            // Then
            assertTrue(result.isPresent());
            assertEquals("same-tenant", result.get()); // No exception
        }

        @Test
        @DisplayName("should not check conflicts when secondary resolver returns empty")
        void shouldNotCheckConflictsWhenSecondaryReturnsEmpty() {
            // Given
            TenantResolver primary = createMockResolver("jwt", 0, "tenant-from-jwt");
            TenantResolver secondary = createMockResolver("header", 100, null); // Returns empty

            chain.addResolver(primary)
                 .addResolver(secondary);

            // When
            Optional<String> result = chain.resolve(mockRequest);

            // Then - no exception, empty doesn't conflict
            assertTrue(result.isPresent());
            assertEquals("tenant-from-jwt", result.get());
        }

        @Test
        @DisplayName("should allow conflict detection to be disabled")
        void shouldAllowConflictDetectionDisabled() {
            // Given - chain with conflict detection disabled
            TenantResolverChain noConflictChain = new TenantResolverChain(false);
            TenantResolver primary = createMockResolverLenient("jwt", 0, "tenant-from-jwt");
            TenantResolver secondary = createMockResolverLenient("header", 100, "different-tenant");

            noConflictChain.addResolver(primary)
                           .addResolver(secondary);

            // When - should NOT throw exception
            Optional<String> result = noConflictChain.resolve(mockRequest);

            // Then
            assertTrue(result.isPresent());
            assertEquals("tenant-from-jwt", result.get());
            assertFalse(noConflictChain.isDetectConflicts());
        }
    }

    // =========================================================================
    // Edge Case Tests
    // =========================================================================

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("should return empty when chain has no resolvers")
        void shouldReturnEmptyWhenNoResolvers() {
            // Given - empty chain

            // When
            Optional<String> result = chain.resolve(mockRequest);

            // Then
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("should return empty when no resolver can determine tenant")
        void shouldReturnEmptyWhenNoResolverSucceeds() {
            // Given - resolvers that all return empty
            TenantResolver resolver1 = createMockResolverLenient("r1", 10, null);
            TenantResolver resolver2 = createMockResolverLenient("r2", 20, null);

            chain.addResolver(resolver1)
                 .addResolver(resolver2);

            // When
            Optional<String> result = chain.resolve(mockRequest);

            // Then
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("should throw exception when adding null resolver")
        void shouldThrowExceptionForNullResolver() {
            // When & Then
            assertThrows(IllegalArgumentException.class, () -> chain.addResolver(null));
        }

        @Test
        @DisplayName("should report correct size after adding resolvers")
        void shouldReportCorrectSize() {
            // Given - use lenient mocks since we only care about size
            chain.addResolver(createMockResolverLenient("r1", 10, "t1"))
                 .addResolver(createMockResolverLenient("r2", 20, "t2"));

            // Then
            assertEquals(2, chain.size());
        }

        @Test
        @DisplayName("should return minimum order of all resolvers")
        void shouldReturnMinimumOrder() {
            // Given - use lenient mocks since we only care about order
            chain.addResolver(createMockResolverLenient("r1", 50, "t1"))
                 .addResolver(createMockResolverLenient("r2", 10, "t2"))
                 .addResolver(createMockResolverLenient("r3", 100, "t3"));

            // Then
            assertEquals(10, chain.getOrder());
        }

        @Test
        @DisplayName("should return MAX_VALUE order when chain is empty")
        void shouldReturnMaxOrderWhenEmpty() {
            // Then
            assertEquals(Integer.MAX_VALUE, chain.getOrder());
        }

        @Test
        @DisplayName("should handle request being null by throwing NPE from resolver")
        void shouldHandleNullRequest() {
            // Given
            chain.addResolver(new HeaderTenantResolver());

            // When & Then - HeaderTenantResolver will throw NPE on null request
            assertThrows(NullPointerException.class, () -> chain.resolve(null));
        }
    }

    // =========================================================================
    // Integration with HeaderTenantResolver
    // =========================================================================

    @Nested
    @DisplayName("Integration with HeaderTenantResolver")
    class HeaderResolverIntegrationTests {

        @Test
        @DisplayName("should resolve tenant from HeaderTenantResolver")
        void shouldResolveFromHeaderResolver() {
            // Given
            when(mockRequest.getHeader("X-Tenant-ID")).thenReturn("tenant-from-header");
            chain.addResolver(new HeaderTenantResolver());

            // When
            Optional<String> result = chain.resolve(mockRequest);

            // Then
            assertTrue(result.isPresent());
            assertEquals("tenant-from-header", result.get());
        }

        @Test
        @DisplayName("should return empty when header is not present")
        void shouldReturnEmptyWhenHeaderNotPresent() {
            // Given
            when(mockRequest.getHeader("X-Tenant-ID")).thenReturn(null);
            chain.addResolver(new HeaderTenantResolver());

            // When
            Optional<String> result = chain.resolve(mockRequest);

            // Then
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("should trim whitespace from header value")
        void shouldTrimWhitespaceFromHeader() {
            // Given
            when(mockRequest.getHeader("X-Tenant-ID")).thenReturn("  tenant-123  ");
            chain.addResolver(new HeaderTenantResolver());

            // When
            Optional<String> result = chain.resolve(mockRequest);

            // Then
            assertTrue(result.isPresent());
            assertEquals("tenant-123", result.get());
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Creates a mock TenantResolver with specified behavior.
     *
     * @param name      the resolver name
     * @param order     the priority order
     * @param tenantId  the tenant ID to return (null for Optional.empty())
     * @return a mock TenantResolver
     */
    private TenantResolver createMockResolver(String name, int order, String tenantId) {
        TenantResolver resolver = mock(TenantResolver.class);
        when(resolver.getName()).thenReturn(name);
        when(resolver.getOrder()).thenReturn(order);
        when(resolver.supports(any())).thenReturn(true);
        when(resolver.resolve(any())).thenReturn(
            tenantId != null ? Optional.of(tenantId) : Optional.empty()
        );
        return resolver;
    }

    /**
     * Creates a mock TenantResolver with lenient stubbing to avoid UnnecessaryStubbingException.
     * Use this for tests where the mock may not actually be called.
     *
     * @param name      the resolver name
     * @param order     the priority order
     * @param tenantId  the tenant ID to return (null for Optional.empty())
     * @return a mock TenantResolver with lenient stubbing
     */
    private TenantResolver createMockResolverLenient(String name, int order, String tenantId) {
        TenantResolver resolver = mock(TenantResolver.class);
        lenient().when(resolver.getName()).thenReturn(name);
        lenient().when(resolver.getOrder()).thenReturn(order);
        lenient().when(resolver.supports(any())).thenReturn(true);
        lenient().when(resolver.resolve(any())).thenReturn(
            tenantId != null ? Optional.of(tenantId) : Optional.empty()
        );
        return resolver;
    }
}
