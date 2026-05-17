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
package io.brix.platform.tenant.enums;

/**
 * Principal admission modes — how a Subject entered a tenant.
 *
 * <p>Determines the source/cause of a principal (Subject) being admitted
 * to a tenant. Used for audit logging and traceability.
 *
 * <h3>Architecture Layer</h3>
 * <p>Layer 2C: Platform Commons enum.</p>
 *
 * <h3>Admission Modes</h3>
 * <ul>
 *   <li>{@link #INVITE} — Tenant actor (admin/staff) invited the subject</li>
 *   <li>{@link #SELF_BIND} — Subject self-registered or accepted an invite link</li>
 *   <li>{@link #BUSINESS_TRIGGER} — Business action automatically triggered admission</li>
 * </ul>
 *
 * @author Brix Platform Team
 * @since 3.2.0
 * @see io.brix.platform.tenant.service.PrincipalAdmissionService
 */
public enum AdmissionMode {

    /**
     * Tenant actor explicitly invited the subject.
     *
     * <p>Scenarios: admin adds a patient from the backend; staff adds
     * a student from the management console.
     */
    INVITE("Invite", "Actor (admin/staff) invited the subject"),

    /**
     * Subject self-registered or accepted an invite link.
     *
     * <p>Scenarios: subject clicked an invite link; subject scanned
     * a QR code and confirmed joining.
     */
    SELF_BIND("Self-Bind", "Subject self-registered or accepted invite"),

    /**
     * Business action automatically triggered the admission.
     *
     * <p>Scenarios: subject submitted an appointment; subject completed
     * a registration form; subject placed an order — triggering automatic
     * principal creation without explicit invite.
     */
    BUSINESS_TRIGGER("Business Trigger", "Business action auto-triggered admission");

    private final String displayName;
    private final String description;

    AdmissionMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
