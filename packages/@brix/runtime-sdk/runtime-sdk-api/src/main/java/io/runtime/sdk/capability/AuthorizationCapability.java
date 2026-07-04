package io.runtime.sdk.capability;

import java.util.Objects;
import java.util.Optional;

import io.runtime.sdk.annotation.Since;

/**
 * Object-level authorization capability contract.
 *
 * <p>Plugins use this contract to ask the Runtime Shell whether the current
 * Actor or Subject may access a specific tenant-owned business object. The
 * contract intentionally accepts only an object reference plus an action; it
 * does not expose platform tenant tables or infrastructure implementation
 * details to plugins.</p>
 *
 * <h3>Security semantics</h3>
 * <ul>
 *   <li>Subject contexts may access only objects associated with their own
 *       principal ID.</li>
 *   <li>Actor contexts must satisfy both operation permission and data scope.</li>
 *   <li>The object's tenant ID must match the authenticated tenant context.</li>
 * </ul>
 *
 * @since 3.2.2
 */
@Since("3.2.2")
public interface AuthorizationCapability {

    /** Action suffix for read access. */
    String ACTION_READ = "read";

    /** Action suffix for create access. */
    String ACTION_CREATE = "create";

    /** Action suffix for update access. */
    String ACTION_UPDATE = "update";

    /** Action suffix for delete access. */
    String ACTION_DELETE = "delete";

    /**
     * Requires access to a business object.
     *
     * @param objectRef tenant-owned business object reference
     * @param action requested action, for example {@code case:read}
     * @throws ObjectAccessDeniedException when access is denied
     */
    void requireObjectAccess(ObjectRef objectRef, String action);

    /**
     * Checks access to a business object without throwing.
     *
     * @param objectRef tenant-owned business object reference
     * @param action requested action
     * @return {@code true} when access is allowed
     */
    default boolean canAccessObject(ObjectRef objectRef, String action) {
        try {
            requireObjectAccess(objectRef, action);
            return true;
        } catch (ObjectAccessDeniedException ex) {
            return false;
        }
    }

    /**
     * Tenant-owned business object reference supplied by a plugin.
     *
     * @param objectType stable object type, for example {@code case}
     * @param objectId object ID, or {@code null} for tenant-scope create/list checks
     * @param tenantId owning tenant ID
     * @param subjectPrincipalId associated Subject principal ID, when the object belongs to a Subject
     * @param actorMemberId associated Actor member ID, when the object belongs to an Actor
     * @param createdBy global identity or user ID that created the object, when known
     */
    record ObjectRef(String objectType,
                     String objectId,
                     String tenantId,
                     String subjectPrincipalId,
                     String actorMemberId,
                     String createdBy) {

        /**
         * Creates an object reference and validates required fields.
         */
        public ObjectRef {
            if (objectType == null || objectType.isBlank()) {
                throw new IllegalArgumentException("objectType must not be blank");
            }
            if (tenantId == null || tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId must not be blank");
            }
            objectType = objectType.trim();
            objectId = normalize(objectId).orElse(null);
            tenantId = tenantId.trim();
            subjectPrincipalId = normalize(subjectPrincipalId).orElse(null);
            actorMemberId = normalize(actorMemberId).orElse(null);
            createdBy = normalize(createdBy).orElse(null);
        }

        /**
         * Builds a reference for a tenant-owned object.
         *
         * @param objectType object type
         * @param objectId object ID
         * @param tenantId tenant ID
         * @param subjectPrincipalId owning Subject principal ID
         * @param actorMemberId owning Actor member ID
         * @param createdBy creator ID
         * @return object reference
         */
        public static ObjectRef of(String objectType, String objectId, String tenantId,
                                   String subjectPrincipalId, String actorMemberId, String createdBy) {
            return new ObjectRef(objectType, objectId, tenantId, subjectPrincipalId, actorMemberId, createdBy);
        }

        /**
         * Builds a reference for tenant-scope create or list checks.
         *
         * @param objectType object type
         * @param tenantId tenant ID
         * @param subjectPrincipalId Subject principal ID target, when applicable
         * @return object reference
         */
        public static ObjectRef tenantScope(String objectType, String tenantId, String subjectPrincipalId) {
            return new ObjectRef(objectType, null, tenantId, subjectPrincipalId, null, null);
        }

        private static Optional<String> normalize(String value) {
            return Optional.ofNullable(value)
                    .map(String::trim)
                    .filter(v -> !v.isBlank());
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof ObjectRef that)) {
                return false;
            }
            return Objects.equals(objectType, that.objectType)
                    && Objects.equals(objectId, that.objectId)
                    && Objects.equals(tenantId, that.tenantId)
                    && Objects.equals(subjectPrincipalId, that.subjectPrincipalId)
                    && Objects.equals(actorMemberId, that.actorMemberId)
                    && Objects.equals(createdBy, that.createdBy);
        }

        @Override
        public int hashCode() {
            return Objects.hash(objectType, objectId, tenantId, subjectPrincipalId, actorMemberId, createdBy);
        }
    }
}