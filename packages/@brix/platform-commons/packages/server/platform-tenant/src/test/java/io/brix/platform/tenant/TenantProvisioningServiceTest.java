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

import io.brix.platform.tenant.core.IdGenerator;
import io.brix.platform.tenant.dto.CreateTenantRequest;
import io.brix.platform.tenant.entity.Organization;
import io.brix.platform.tenant.entity.Tenant;
import io.brix.platform.tenant.entity.TenantMember;
import io.brix.platform.tenant.enums.TenantMemberType;
import io.brix.platform.tenant.enums.TenantStatus;
import io.brix.platform.tenant.exception.InvalidReferenceException;
import io.brix.platform.tenant.repository.IdentityRepository;
import io.brix.platform.tenant.repository.OrganizationRepository;
import io.brix.platform.tenant.repository.TenantMemberRepository;
import io.brix.platform.tenant.repository.TenantRepository;
import io.brix.platform.tenant.service.TenantProvisioningService;
import io.brix.platform.tenant.service.TenantProvisioningServiceImpl;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TenantProvisioningService}.
 *
 * <p>This test class validates the tenant provisioning workflow including
 * creation, suspension, and activation. Uses Mockito to isolate the service
 * from database dependencies.
 *
 * <h3>Test Categories</h3>
 * <ul>
 *   <li>Create Tenant Tests - Tests for the complete provisioning workflow</li>
 *   <li>Suspend Tenant Tests - Tests for tenant suspension state transitions</li>
 *   <li>Activate Tenant Tests - Tests for tenant activation state transitions</li>
 *   <li>Validation Tests - Tests for input validation and error handling</li>
 * </ul>
 *
 * <h3>Transaction Behavior</h3>
 * <p>Note: Transaction rollback behavior cannot be fully tested in unit tests.
 * Integration tests with actual database are needed for transaction semantics.</p>
 *
 * @author Brix Platform Team
 * @since 3.1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TenantProvisioningService Tests")
class TenantProvisioningServiceTest {

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private TenantMemberRepository tenantMemberRepository;

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private IdentityRepository identityRepository;

    @Mock
    private IdGenerator idGenerator;

    private TenantProvisioningService service;

    @BeforeEach
    void setUp() {
        service = new TenantProvisioningServiceImpl(
            tenantRepository,
            tenantMemberRepository,
            organizationRepository,
            identityRepository,
            idGenerator
        );
    }

    // =========================================================================
    // Create Tenant Tests
    // =========================================================================

    @Nested
    @DisplayName("Create Tenant Tests")
    class CreateTenantTests {

        @Test
        @DisplayName("should create tenant with all related entities")
        void shouldCreateTenantWithAllRelatedEntities() {
            // Given
            Long tenantId = 1001L;
            Long memberId = 2001L;
            Long orgId = 3001L;
            Long ownerIdentityId = 100L;

            CreateTenantRequest request = CreateTenantRequest.builder()
                .code("acme-corp")
                .name("Acme Corporation")
                .ownerIdentityId(ownerIdentityId)
                .build();

            when(tenantRepository.existsByCode("acme-corp")).thenReturn(false);
            when(identityRepository.existsById(ownerIdentityId)).thenReturn(true);
            when(idGenerator.nextId()).thenReturn(tenantId, memberId, orgId);
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));
            when(tenantMemberRepository.save(any(TenantMember.class))).thenAnswer(inv -> inv.getArgument(0));
            when(organizationRepository.save(any(Organization.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            Tenant result = service.createTenant(request);

            // Then - verify tenant created correctly
            assertNotNull(result);
            assertEquals(tenantId, result.getId());
            assertEquals("acme-corp", result.getCode());
            assertEquals("Acme Corporation", result.getName());
            assertEquals(TenantStatus.PENDING_ACTIVATION, result.getStatus());

            // Verify tenant member created
            ArgumentCaptor<TenantMember> memberCaptor = ArgumentCaptor.forClass(TenantMember.class);
            verify(tenantMemberRepository).save(memberCaptor.capture());
            TenantMember savedMember = memberCaptor.getValue();
            assertEquals(memberId, savedMember.getId());
            assertEquals(tenantId, savedMember.getTenantId());
            assertEquals(ownerIdentityId, savedMember.getIdentityId());
            assertEquals(TenantMemberType.OWNER, savedMember.getMemberType());

            // Verify organization created
            ArgumentCaptor<Organization> orgCaptor = ArgumentCaptor.forClass(Organization.class);
            verify(organizationRepository).save(orgCaptor.capture());
            Organization savedOrg = orgCaptor.getValue();
            assertEquals(orgId, savedOrg.getId());
            assertEquals(tenantId, savedOrg.getTenantId());
            assertEquals("default", savedOrg.getCode());
            assertEquals("Acme Corporation", savedOrg.getName());
        }

        @Test
        @DisplayName("should throw exception when tenant code already exists")
        void shouldThrowWhenTenantCodeExists() {
            // Given
            CreateTenantRequest request = CreateTenantRequest.builder()
                .code("existing-code")
                .name("Test Company")
                .ownerIdentityId(100L)
                .build();

            when(tenantRepository.existsByCode("existing-code")).thenReturn(true);

            // When & Then
            IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.createTenant(request)
            );

            assertTrue(exception.getMessage().contains("already exists"));
            verify(tenantRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw exception when owner identity not found")
        void shouldThrowWhenOwnerIdentityNotFound() {
            // Given
            Long nonExistentIdentityId = 999L;
            CreateTenantRequest request = CreateTenantRequest.builder()
                .code("new-tenant")
                .name("Test Company")
                .ownerIdentityId(nonExistentIdentityId)
                .build();

            when(tenantRepository.existsByCode("new-tenant")).thenReturn(false);
            when(identityRepository.existsById(nonExistentIdentityId)).thenReturn(false);

            // When & Then
            assertThrows(
                InvalidReferenceException.class,
                () -> service.createTenant(request)
            );

            verify(tenantRepository, never()).save(any());
        }

        @Test
        @DisplayName("should generate unique IDs for each entity")
        void shouldGenerateUniqueIdsForEachEntity() {
            // Given
            CreateTenantRequest request = CreateTenantRequest.builder()
                .code("unique-test")
                .name("Unique ID Test")
                .ownerIdentityId(100L)
                .build();

            when(tenantRepository.existsByCode(any())).thenReturn(false);
            when(identityRepository.existsById(any())).thenReturn(true);
            when(idGenerator.nextId()).thenReturn(1L, 2L, 3L); // Three different IDs
            when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(tenantMemberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(organizationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.createTenant(request);

            // Then - idGenerator.nextId() should be called exactly 3 times
            verify(idGenerator, times(3)).nextId();
        }
    }

    // =========================================================================
    // Validation Tests
    // =========================================================================

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("should throw exception for null request")
        void shouldThrowForNullRequest() {
            // When & Then
            assertThrows(
                IllegalArgumentException.class,
                () -> service.createTenant(null)
            );
        }

        @Test
        @DisplayName("should throw exception for null tenant code")
        void shouldThrowForNullCode() {
            // Given
            CreateTenantRequest request = CreateTenantRequest.builder()
                .code(null)
                .name("Test Company")
                .ownerIdentityId(100L)
                .build();

            // When & Then
            assertThrows(
                IllegalArgumentException.class,
                () -> service.createTenant(request)
            );
        }

        @Test
        @DisplayName("should throw exception for empty tenant code")
        void shouldThrowForEmptyCode() {
            // Given
            CreateTenantRequest request = CreateTenantRequest.builder()
                .code("")
                .name("Test Company")
                .ownerIdentityId(100L)
                .build();

            // When & Then
            assertThrows(
                IllegalArgumentException.class,
                () -> service.createTenant(request)
            );
        }

        @Test
        @DisplayName("should throw exception for null tenant name")
        void shouldThrowForNullName() {
            // Given
            CreateTenantRequest request = CreateTenantRequest.builder()
                .code("test-code")
                .name(null)
                .ownerIdentityId(100L)
                .build();

            // When & Then
            assertThrows(
                IllegalArgumentException.class,
                () -> service.createTenant(request)
            );
        }

        @Test
        @DisplayName("should throw exception for null owner identity ID")
        void shouldThrowForNullOwnerIdentityId() {
            // Given
            CreateTenantRequest request = CreateTenantRequest.builder()
                .code("test-code")
                .name("Test Company")
                .ownerIdentityId(null)
                .build();

            // When & Then
            assertThrows(
                IllegalArgumentException.class,
                () -> service.createTenant(request)
            );
        }
    }

    // =========================================================================
    // Suspend Tenant Tests
    // =========================================================================

    @Nested
    @DisplayName("Suspend Tenant Tests")
    class SuspendTenantTests {

        @Test
        @DisplayName("should suspend active tenant successfully")
        void shouldSuspendActiveTenant() {
            // Given
            Long tenantId = 1L;
            Tenant tenant = createActiveTenant(tenantId, "test-tenant");

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.suspendTenant(tenantId);

            // Then
            ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
            verify(tenantRepository).save(captor.capture());
            assertEquals(TenantStatus.SUSPENDED, captor.getValue().getStatus());
        }

        @Test
        @DisplayName("should throw exception for null tenant ID")
        void shouldThrowForNullTenantId() {
            // When & Then
            assertThrows(
                IllegalArgumentException.class,
                () -> service.suspendTenant(null)
            );
        }

        @Test
        @DisplayName("should throw exception when tenant not found")
        void shouldThrowWhenTenantNotFound() {
            // Given
            Long nonExistentId = 999L;
            when(tenantRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // When & Then
            assertThrows(
                EntityNotFoundException.class,
                () -> service.suspendTenant(nonExistentId)
            );
        }

        @Test
        @DisplayName("should throw exception when suspending non-active tenant")
        void shouldThrowWhenSuspendingNonActiveTenant() {
            // Given
            Long tenantId = 1L;
            Tenant tenant = createPendingTenant(tenantId, "pending-tenant");

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

            // When & Then
            assertThrows(
                IllegalStateException.class,
                () -> service.suspendTenant(tenantId)
            );
        }
    }

    // =========================================================================
    // Activate Tenant Tests
    // =========================================================================

    @Nested
    @DisplayName("Activate Tenant Tests")
    class ActivateTenantTests {

        @Test
        @DisplayName("should activate pending tenant successfully")
        void shouldActivatePendingTenant() {
            // Given
            Long tenantId = 1L;
            Tenant tenant = createPendingTenant(tenantId, "pending-tenant");

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.activateTenant(tenantId);

            // Then
            ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
            verify(tenantRepository).save(captor.capture());
            assertEquals(TenantStatus.ACTIVE, captor.getValue().getStatus());
        }

        @Test
        @DisplayName("should reactivate suspended tenant successfully")
        void shouldReactivateSuspendedTenant() {
            // Given
            Long tenantId = 1L;
            Tenant tenant = createSuspendedTenant(tenantId, "suspended-tenant");

            when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(inv -> inv.getArgument(0));

            // When
            service.activateTenant(tenantId);

            // Then
            ArgumentCaptor<Tenant> captor = ArgumentCaptor.forClass(Tenant.class);
            verify(tenantRepository).save(captor.capture());
            assertEquals(TenantStatus.ACTIVE, captor.getValue().getStatus());
        }

        @Test
        @DisplayName("should throw exception for null tenant ID")
        void shouldThrowForNullTenantIdOnActivate() {
            // When & Then
            assertThrows(
                IllegalArgumentException.class,
                () -> service.activateTenant(null)
            );
        }

        @Test
        @DisplayName("should throw exception when tenant not found")
        void shouldThrowWhenTenantNotFoundOnActivate() {
            // Given
            Long nonExistentId = 999L;
            when(tenantRepository.findById(nonExistentId)).thenReturn(Optional.empty());

            // When & Then
            assertThrows(
                EntityNotFoundException.class,
                () -> service.activateTenant(nonExistentId)
            );
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    /**
     * Creates a tenant in PENDING_ACTIVATION status for testing.
     */
    private Tenant createPendingTenant(Long id, String code) {
        Tenant tenant = new Tenant(code, "Test Tenant " + code);
        tenant.setId(id);
        tenant.setStatus(TenantStatus.PENDING_ACTIVATION);
        return tenant;
    }

    /**
     * Creates a tenant in ACTIVE status for testing.
     */
    private Tenant createActiveTenant(Long id, String code) {
        Tenant tenant = createPendingTenant(id, code);
        tenant.activate(); // Transition to ACTIVE
        return tenant;
    }

    /**
     * Creates a tenant in SUSPENDED status for testing.
     */
    private Tenant createSuspendedTenant(Long id, String code) {
        Tenant tenant = createActiveTenant(id, code);
        tenant.suspend(); // Transition to SUSPENDED
        return tenant;
    }
}
