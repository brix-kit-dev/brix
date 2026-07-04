package io.brix.platform.tenant.service;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import io.brix.platform.tenant.dto.AuditEvent;
import io.runtime.sdk.capability.AuthCapability;
import io.runtime.sdk.capability.AuthorizationCapability;
import io.runtime.sdk.capability.DataScope;
import io.runtime.sdk.capability.ObjectAccessDeniedException;
import io.runtime.sdk.capability.TenantCapability;
import io.runtime.sdk.capability.registry.Capability;
import io.runtime.sdk.capability.registry.CapabilityLevel;

/**
 * Default object-level authorization implementation backed by tenant and auth capabilities.
 */
@Service
@Capability(
    type = AuthorizationCapability.class,
    name = "tenant-object-authorization",
    description = "Tenant-aware object-level authorization. Subject can access own objects; Actor requires permission and data scope.",
    level = CapabilityLevel.CORE,
    aliases = {"authorization", "objectAuthorization", "AuthorizationCapability"}
)
public class AuthorizationCapabilityImpl implements AuthorizationCapability {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationCapabilityImpl.class);

    private static final String SCOPE_ALL = "ALL";
    private static final String SCOPE_SELF = "SELF";
    private static final String SCOPE_TENANT = "TENANT";
    private static final String SCOPE_SUBJECT = "SUBJECT";
    private static final String SCOPE_MEMBER = "MEMBER";

    private final TenantCapability tenantCapability;
    private final ObjectProvider<AuthCapability> authCapabilityProvider;
    private final ObjectProvider<AuditService> auditServiceProvider;

    public AuthorizationCapabilityImpl(TenantCapability tenantCapability,
                                       ObjectProvider<AuthCapability> authCapabilityProvider,
                                       ObjectProvider<AuditService> auditServiceProvider) {
        this.tenantCapability = tenantCapability;
        this.authCapabilityProvider = authCapabilityProvider;
        this.auditServiceProvider = auditServiceProvider;
    }

    @Override
    public void requireObjectAccess(ObjectRef objectRef, String action) {
        if (objectRef == null) {
            throw new IllegalArgumentException("objectRef must not be null");
        }
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("action must not be blank");
        }

        String normalizedAction = action.trim();
        String currentTenantId = tenantCapability.getCurrentTenantId();
        if (!Objects.equals(currentTenantId, objectRef.tenantId())) {
            deny(objectRef, normalizedAction, "Object belongs to a different tenant");
        }

        AuthCapability authCapability = authCapabilityProvider.getIfAvailable();
        if (authCapability == null) {
            deny(objectRef, normalizedAction, "AuthCapability is not available");
        }

        if (tenantCapability.isSubject()) {
            requireSubjectAccess(objectRef, normalizedAction);
            return;
        }

        if (tenantCapability.isActor()) {
            requireActorAccess(authCapability, objectRef, normalizedAction);
            return;
        }

        deny(objectRef, normalizedAction, "Current identity is neither Actor nor Subject");
    }

    private void requireSubjectAccess(ObjectRef objectRef, String action) {
        String principalId = tenantCapability.getCurrentRefId();
        if (principalId != null && principalId.equals(objectRef.subjectPrincipalId())) {
            return;
        }
        String userId = tenantCapability.getCurrentUserId();
        if (userId != null && userId.equals(objectRef.createdBy())) {
            return;
        }
        deny(objectRef, action, "Subject may only access objects bound to its own principal ID");
    }

    private void requireActorAccess(AuthCapability authCapability, ObjectRef objectRef, String action) {
        if (!authCapability.hasPermission(action)) {
            deny(objectRef, action, "Actor lacks permission: " + action);
        }

        Set<DataScope> scopes = authCapability.getAuthorizedScopes();
        if (scopes == null || scopes.isEmpty()) {
            deny(objectRef, action, "Actor has no data scope for object access");
        }
        Set<DataScope> authorizedScopes = Objects.requireNonNull(scopes);
        if (authorizedScopes.stream().anyMatch(scope -> scopeAllows(scope, objectRef))) {
            return;
        }
        deny(objectRef, action, "Actor data scope does not include requested object");
    }

    private boolean scopeAllows(DataScope scope, ObjectRef objectRef) {
        if (scope == null) {
            return false;
        }
        String type = scope.getType().toUpperCase(Locale.ROOT);
        String value = scope.getValue();
        return switch (type) {
            case SCOPE_ALL -> "*".equals(value) || objectRef.tenantId().equals(value);
            case SCOPE_TENANT -> objectRef.tenantId().equals(value);
            case SCOPE_SELF -> valueEqualsAny(value, objectRef.createdBy(), objectRef.actorMemberId());
            case SCOPE_MEMBER -> valueEqualsAny(value, objectRef.actorMemberId(), tenantCapability.getCurrentRefId());
            case SCOPE_SUBJECT -> valueEqualsAny(value, objectRef.subjectPrincipalId());
            default -> false;
        };
    }

    private boolean valueEqualsAny(String expected, String... candidates) {
        if (expected == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (expected.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private void deny(ObjectRef objectRef, String action, String reason) {
        recordDeniedAccess(objectRef, action, reason);
        throw new ObjectAccessDeniedException(reason, objectRef.objectType(), objectRef.objectId(), action);
    }

    private void recordDeniedAccess(ObjectRef objectRef, String action, String reason) {
        AuditService auditService = auditServiceProvider.getIfAvailable();
        if (auditService == null) {
            return;
        }
        try {
            auditService.log(AuditEvent.builder()
                    .createdBy(parseLongSilently(currentUserId()))
                    .tenantId(parseLongSilently(objectRef.tenantId()))
                    .ownerMemberId(parseLongSilently(objectRef.actorMemberId()))
                    .action(AuditEvent.ACTION_PERMISSION_CHANGE)
                    .resourceType(objectRef.objectType().toUpperCase(Locale.ROOT))
                    .resourceId(objectRef.objectId())
                    .description("Object access denied: action=" + action + ", reason=" + reason)
                    .success(false)
                    .errorCode(ObjectAccessDeniedException.ERROR_CODE)
                    .errorMessage(reason)
                    .build());
        } catch (RuntimeException ex) {
            log.warn("Failed to write object authorization denial audit log: objectType={}, objectId={}, action={}",
                    objectRef.objectType(), objectRef.objectId(), action, ex);
        }
    }

    private String currentUserId() {
        try {
            return tenantCapability.getCurrentUserId();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Long parseLongSilently(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}