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
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import io.brix.platform.common.tenant.TenantContext;

/**
 * Unit tests for {@link TenantContext} class.
 *
 * <p>This test class validates the core functionality of the TenantContext,
 * including tenant ID management, context propagation across threads, and
 * thread safety guarantees.
 *
 * <h3>Test Categories</h3>
 * <ul>
 *   <li>Basic API Tests - Tests for get/set/clear operations</li>
 *   <li>Propagation Tests - Tests for wrap() method and async context propagation</li>
 *   <li>Thread Safety Tests - Tests for multi-threaded scenarios</li>
 *   <li>Edge Case Tests - Tests for null/empty handling and boundary conditions</li>
 * </ul>
 *
 * <h3>Test Design Principles</h3>
 * <ul>
 *   <li>Each test is isolated - context is cleared after each test</li>
 *   <li>Tests use meaningful names following BDD style</li>
 *   <li>Complex scenarios are broken into nested test classes</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 */
@DisplayName("TenantContext Tests")
class TenantContextTest {

    /**
     * Clean up tenant context after each test to ensure isolation.
     * This prevents test pollution where one test's context leaks into another.
     */
    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    // =========================================================================
    // Basic API Tests
    // =========================================================================

    @Nested
    @DisplayName("Basic API Tests")
    class BasicApiTests {

        @Test
        @DisplayName("should set and get tenant ID successfully")
        void shouldSetAndGetTenantId() {
            // Given
            String tenantId = "tenant-123";

            // When
            TenantContext.setTenantId(tenantId);

            // Then
            Optional<String> result = TenantContext.getTenantId();
            assertTrue(result.isPresent());
            assertEquals(tenantId, result.get());
        }

        @Test
        @DisplayName("should return empty when tenant ID not set")
        void shouldReturnEmptyWhenNotSet() {
            // When
            Optional<String> result = TenantContext.getTenantId();

            // Then
            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("should return true for hasTenant when tenant ID is set")
        void shouldReturnTrueForHasTenant() {
            // Given
            TenantContext.setTenantId("tenant-456");

            // Then
            assertTrue(TenantContext.hasTenant());
        }

        @Test
        @DisplayName("should return false for hasTenant when not set")
        void shouldReturnFalseForHasTenantWhenNotSet() {
            // Then
            assertFalse(TenantContext.hasTenant());
        }

        @Test
        @DisplayName("should clear tenant context successfully")
        void shouldClearTenantContext() {
            // Given
            TenantContext.setTenantId("tenant-789");
            TenantContext.setUserId("user-123");
            assertTrue(TenantContext.hasTenant());

            // When
            TenantContext.clear();

            // Then
            assertFalse(TenantContext.hasTenant());
            assertFalse(TenantContext.hasUser());
        }

        @Test
        @DisplayName("should throw exception when setting null tenant ID")
        void shouldThrowExceptionForNullTenantId() {
            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                TenantContext.setTenantId(null);
            });
        }

        @Test
        @DisplayName("should throw exception when setting blank tenant ID")
        void shouldThrowExceptionForBlankTenantId() {
            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                TenantContext.setTenantId("   ");
            });
        }

        @Test
        @DisplayName("should throw exception for requireTenantId when not set")
        void shouldThrowExceptionForRequireTenantIdWhenNotSet() {
            // When & Then
            assertThrows(IllegalStateException.class, TenantContext::requireTenantId);
        }

        @Test
        @DisplayName("should return default tenant ID when not set")
        void shouldReturnDefaultTenantIdWhenNotSet() {
            // When
            String result = TenantContext.getTenantIdOrDefault();

            // Then
            assertEquals(TenantContext.DEFAULT_TENANT_ID, result);
        }

        @Test
        @DisplayName("should correctly identify system tenant")
        void shouldIdentifySystemTenant() {
            // Given
            TenantContext.setTenantId(TenantContext.SYSTEM_TENANT_ID);

            // Then
            assertTrue(TenantContext.isSystemTenant());
        }

        @Test
        @DisplayName("should correctly identify non-system tenant")
        void shouldIdentifyNonSystemTenant() {
            // Given
            TenantContext.setTenantId("regular-tenant");

            // Then
            assertFalse(TenantContext.isSystemTenant());
        }
    }

    // =========================================================================
    // User ID Tests
    // =========================================================================

    @Nested
    @DisplayName("User ID Tests")
    class UserIdTests {

        @Test
        @DisplayName("should set and get user ID successfully")
        void shouldSetAndGetUserId() {
            // Given
            String userId = "user-456";

            // When
            TenantContext.setUserId(userId);

            // Then
            Optional<String> result = TenantContext.getUserId();
            assertTrue(result.isPresent());
            assertEquals(userId, result.get());
        }

        @Test
        @DisplayName("should throw exception when setting null user ID")
        void shouldThrowExceptionForNullUserId() {
            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                TenantContext.setUserId(null);
            });
        }

        @Test
        @DisplayName("should throw exception for requireUserId when not set")
        void shouldThrowExceptionForRequireUserIdWhenNotSet() {
            // When & Then
            assertThrows(IllegalStateException.class, TenantContext::requireUserId);
        }
    }

    // =========================================================================
    // Context Switching Tests
    // =========================================================================

    @Nested
    @DisplayName("Context Switching Tests")
    class ContextSwitchingTests {

        @Test
        @DisplayName("should run with different tenant and restore original")
        void shouldRunWithTenantAndRestore() {
            // Given
            String originalTenant = "original-tenant";
            String tempTenant = "temp-tenant";
            TenantContext.setTenantId(originalTenant);

            // When
            TenantContext.runWithTenant(tempTenant, () -> {
                // Verify tenant is switched
                assertEquals(tempTenant, TenantContext.getCurrentTenantId());
            });

            // Then - original tenant should be restored
            assertEquals(originalTenant, TenantContext.getCurrentTenantId());
        }

        @Test
        @DisplayName("should run with tenant and return value")
        void shouldRunWithTenantAndReturnValue() {
            // Given
            String tempTenant = "temp-tenant";

            // When
            String result = TenantContext.runWithTenant(tempTenant, () -> {
                return "result-from-" + TenantContext.getCurrentTenantId();
            });

            // Then
            assertEquals("result-from-temp-tenant", result);
        }

        @Test
        @DisplayName("should restore original tenant even when exception thrown")
        void shouldRestoreTenantOnException() {
            // Given
            String originalTenant = "original-tenant";
            TenantContext.setTenantId(originalTenant);

            // When & Then
            assertThrows(RuntimeException.class, () -> {
                TenantContext.runWithTenant("error-tenant", () -> {
                    throw new RuntimeException("Test exception");
                });
            });

            // Verify original tenant is restored
            assertEquals(originalTenant, TenantContext.getCurrentTenantId());
        }

        @Test
        @DisplayName("should run as system tenant")
        void shouldRunAsSystemTenant() {
            // Given
            String originalTenant = "regular-tenant";
            TenantContext.setTenantId(originalTenant);

            // When
            TenantContext.runAsSystem(() -> {
                // Verify system tenant is active
                assertTrue(TenantContext.isSystemTenant());
            });

            // Then - original tenant should be restored
            assertEquals(originalTenant, TenantContext.getCurrentTenantId());
        }
    }

    // =========================================================================
    // Wrap Propagation Tests (Critical for async operations)
    // =========================================================================

    @Nested
    @DisplayName("Wrap Propagation Tests")
    class WrapPropagationTests {

        @Test
        @DisplayName("should propagate tenant context to wrapped Runnable")
        void shouldPropagateTenantToWrappedRunnable() throws Exception {
            // Given
            String tenantId = "tenant-for-async";
            TenantContext.setTenantId(tenantId);

            AtomicReference<String> capturedTenantId = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);

            // When - create wrapped runnable and execute in another thread
            Runnable wrapped = TenantContext.wrap(() -> {
                capturedTenantId.set(TenantContext.getTenantId().orElse(null));
                latch.countDown();
            });

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                executor.submit(wrapped);
                assertTrue(latch.await(5, TimeUnit.SECONDS), "Task should complete within timeout");

                // Then
                assertEquals(tenantId, capturedTenantId.get());
            } finally {
                executor.shutdown();
            }
        }

        @Test
        @DisplayName("should propagate tenant context to wrapped Callable")
        void shouldPropagateTenantToWrappedCallable() throws Exception {
            // Given
            String tenantId = "tenant-for-callable";
            String userId = "user-for-callable";
            TenantContext.setTenantId(tenantId);
            TenantContext.setUserId(userId);

            // When - create wrapped callable
            Callable<String> wrapped = TenantContext.wrap(() -> {
                return TenantContext.getCurrentTenantId() + "-" + TenantContext.getCurrentUserId();
            });

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<String> future = executor.submit(wrapped);
                String result = future.get(5, TimeUnit.SECONDS);

                // Then
                assertEquals(tenantId + "-" + userId, result);
            } finally {
                executor.shutdown();
            }
        }

        @Test
        @DisplayName("should not affect other threads when wrapped task completes")
        void shouldNotAffectOtherThreadsAfterCompletion() throws Exception {
            // Given
            String originalTenantId = "original-tenant";
            TenantContext.setTenantId(originalTenantId);

            CountDownLatch taskStarted = new CountDownLatch(1);
            CountDownLatch taskEnded = new CountDownLatch(1);
            AtomicReference<String> otherThreadTenantInside = new AtomicReference<>();
            AtomicReference<String> otherThreadTenantAfter = new AtomicReference<>();

            // Create a thread pool with a single thread
            ExecutorService executor = Executors.newSingleThreadExecutor();

            try {
                // First task: wrapped with tenant context
                Runnable wrapped = TenantContext.wrap(() -> {
                    try {
                        taskStarted.countDown();
                        otherThreadTenantInside.set(TenantContext.getTenantId().orElse("none"));
                        Thread.sleep(50); // Simulate some work
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });

                executor.submit(wrapped);
                assertTrue(taskStarted.await(5, TimeUnit.SECONDS));

                // Wait a bit then submit second task (same thread, no wrapping)
                executor.submit(() -> {
                    otherThreadTenantAfter.set(TenantContext.getTenantId().orElse("none"));
                    taskEnded.countDown();
                });

                assertTrue(taskEnded.await(5, TimeUnit.SECONDS));

                // Then - first task should have had tenant, second should NOT
                assertEquals(originalTenantId, otherThreadTenantInside.get());
                assertEquals("none", otherThreadTenantAfter.get());
            } finally {
                executor.shutdown();
            }
        }

        @Test
        @DisplayName("should throw exception when wrapping null Runnable")
        void shouldThrowExceptionForNullRunnable() {
            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                TenantContext.wrap((Runnable) null);
            });
        }

        @Test
        @DisplayName("should throw exception when wrapping null Callable")
        void shouldThrowExceptionForNullCallable() {
            // When & Then
            assertThrows(IllegalArgumentException.class, () -> {
                TenantContext.wrap((Callable<?>) null);
            });
        }

        @Test
        @DisplayName("should handle case when no tenant context exists during wrap")
        void shouldHandleNoTenantContextDuringWrap() throws Exception {
            // Given - no tenant context set
            TenantContext.clear();

            AtomicReference<String> capturedTenant = new AtomicReference<>();
            CountDownLatch latch = new CountDownLatch(1);

            // When - wrap without tenant context
            Runnable wrapped = TenantContext.wrap(() -> {
                capturedTenant.set(TenantContext.getTenantId().orElse("not-set"));
                latch.countDown();
            });

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                executor.submit(wrapped);
                assertTrue(latch.await(5, TimeUnit.SECONDS));

                // Then - should have no tenant
                assertEquals("not-set", capturedTenant.get());
            } finally {
                executor.shutdown();
            }
        }

        @Test
        @DisplayName("should clean up context after wrapped task completes with exception")
        void shouldCleanupAfterWrappedTaskException() throws Exception {
            // Given
            String tenantId = "tenant-for-exception";
            TenantContext.setTenantId(tenantId);

            AtomicReference<Boolean> hadTenantAfterException = new AtomicReference<>(true);

            // When - wrap task that throws exception
            Runnable throwingTask = () -> {
                throw new RuntimeException("Intentional test exception");
            };
            Runnable wrapped = TenantContext.wrap(throwingTask);

            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                executor.submit(wrapped).get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Expected - exception from wrapped task
            }

            // Submit another task to check if context was cleaned
            CountDownLatch latch = new CountDownLatch(1);
            executor.submit(() -> {
                hadTenantAfterException.set(TenantContext.hasTenant());
                latch.countDown();
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            executor.shutdown();

            // Then - context should have been cleaned up
            assertFalse(hadTenantAfterException.get());
        }
    }

    // =========================================================================
    // Thread Safety Tests
    // =========================================================================

    @Nested
    @DisplayName("Thread Safety Tests")
    class ThreadSafetyTests {

        @Test
        @DisplayName("should isolate tenant context between threads")
        void shouldIsolateTenantContextBetweenThreads() throws Exception {
            // Given
            int threadCount = 10;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicReference<Boolean> allCorrect = new AtomicReference<>(true);

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            try {
                // Create threads that each set their own tenant
                for (int i = 0; i < threadCount; i++) {
                    final String threadTenant = "tenant-" + i;

                    executor.submit(() -> {
                        try {
                            startLatch.await(); // Wait for all threads to be ready

                            // Set tenant for this thread
                            TenantContext.setTenantId(threadTenant);

                            // Do some work
                            Thread.sleep(50);

                            // Verify tenant is still the one we set
                            String currentTenant = TenantContext.getCurrentTenantId();
                            if (!threadTenant.equals(currentTenant)) {
                                allCorrect.set(false);
                            }
                        } catch (Exception e) {
                            allCorrect.set(false);
                        } finally {
                            TenantContext.clear();
                            endLatch.countDown();
                        }
                    });
                }

                // When - release all threads simultaneously
                startLatch.countDown();
                assertTrue(endLatch.await(30, TimeUnit.SECONDS));

                // Then
                assertTrue(allCorrect.get(), "All threads should maintain their own tenant context");
            } finally {
                executor.shutdown();
            }
        }
    }
}
