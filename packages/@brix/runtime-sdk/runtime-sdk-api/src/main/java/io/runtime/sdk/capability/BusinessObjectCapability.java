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

import java.util.List;

import io.runtime.sdk.annotation.Since;

/**
 * Business Object Capability — optional contract for industry plugins.
 *
 * <p>Defines the contract for querying business objects (domain entities)
 * associated with a Subject (C-side principal). Industry plugins implement
 * this capability to expose their domain-specific entities in a uniform way.</p>
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2A: SDK Capability Contract (runtime-sdk-api). Zero dependencies.
 * Implementations reside in industry plugins (Layer 1 or Layer 4).</p>
 *
 * <h3>B2B2C Model Context</h3>
 * <p>In the B2B2C model, each tenant has:
 * <ul>
 *   <li><b>Actors</b> (B-side): Staff who provide services (sys_tenant_member)</li>
 *   <li><b>Subjects</b> (C-side): Customers who receive services (sys_tenant_principal)</li>
 *   <li><b>Business Objects</b>: Domain entities representing the service relationship
 *       (e.g., medical cases, enrollments, orders) — managed by industry plugins</li>
 * </ul>
 *
 * <h3>Plugin Implementation Example</h3>
 * <pre>{@code
 * @Component
 * public class MedicalCaseCapability implements BusinessObjectCapability {
 *
 *     @Override
 *     public List<BusinessObjectSummary> listBySubject(String principalId) {
 *         return caseRepository.findByPatientPrincipalId(Long.parseLong(principalId))
 *             .stream()
 *             .map(c -> new BusinessObjectSummary(
 *                 String.valueOf(c.getId()),
 *                 "medical_case",
 *                 c.getDiagnosis(),
 *                 c.getStatus().name(),
 *                 c.getCreatedAt().toInstant(),
 *                 c.getUpdatedAt().toInstant(),
 *                 Map.of("doctorName", c.getDoctorName())))
 *             .toList();
 *     }
 *
 *     @Override
 *     public boolean hasActiveBusinessRelation(String principalId) {
 *         return caseRepository.existsByPatientPrincipalIdAndStatusIn(
 *             Long.parseLong(principalId),
 *             List.of(CaseStatus.OPEN, CaseStatus.IN_PROGRESS));
 *     }
 * }
 * }</pre>
 *
 * <h3>Optional Nature</h3>
 * <p>This capability is optional — not all tenants or plugins provide business
 * objects. The platform core never depends on this capability being present.
 * Consumers should use {@code CapabilityRegistry.getOptional(BusinessObjectCapability.class)}.</p>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 * @see BusinessObjectSummary
 * @see io.runtime.sdk.event.tenant.PrincipalJoinedEvent
 */
@Since("3.2.0")
public interface BusinessObjectCapability {

    /**
     * Lists business objects associated with a Subject (principal).
     *
     * <p>Returns domain-specific business entities that are linked to the
     * given principal ID. The returned list may span multiple object types
     * if the plugin manages more than one entity type.</p>
     *
     * @param principalId the principal ID (sys_tenant_principal.id as String)
     * @return list of business object summaries, empty if none found
     */
    List<BusinessObjectSummary> listBySubject(String principalId);

    /**
     * Checks whether the principal has any active business relationship.
     *
     * <p>Used to determine if a Subject still has ongoing business with
     * the tenant. This influences principal lifecycle decisions — for example,
     * warning an admin before revoking a principal with active cases.</p>
     *
     * <p>The definition of "active" is plugin-specific (e.g., open cases,
     * pending orders, active enrollments).</p>
     *
     * @param principalId the principal ID (sys_tenant_principal.id as String)
     * @return true if the principal has at least one active business relation
     */
    boolean hasActiveBusinessRelation(String principalId);
}
