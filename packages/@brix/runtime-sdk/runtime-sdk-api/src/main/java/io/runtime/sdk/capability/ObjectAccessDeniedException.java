package io.runtime.sdk.capability;

import io.runtime.sdk.annotation.Since;

/**
 * Exception thrown when the current context is not allowed to access a business object.
 *
 * <p>The exception is part of the Runtime SDK contract so plugins can depend on a
 * stable, implementation-neutral error type. HTTP adapters should translate this
 * exception to {@code 403 Forbidden}.</p>
 *
 * @since 3.2.2
 */
@Since("3.2.2")
public class ObjectAccessDeniedException extends RuntimeException {

    /** Error code for object-level authorization failures. */
    public static final String ERROR_CODE = "AUTHZ-OA-403";

    private final String errorCode;
    private final String objectType;
    private final String objectId;
    private final String action;

    /**
     * Creates a new denial exception.
     *
     * @param message denial message
     * @param objectType business object type
     * @param objectId business object ID, or {@code null} for tenant-scope checks
     * @param action requested action
     */
    public ObjectAccessDeniedException(String message, String objectType, String objectId, String action) {
        super(message);
        this.errorCode = ERROR_CODE;
        this.objectType = objectType;
        this.objectId = objectId;
        this.action = action;
    }

    /**
     * Returns the stable machine-readable error code.
     *
     * @return error code
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * Returns the business object type.
     *
     * @return object type
     */
    public String getObjectType() {
        return objectType;
    }

    /**
     * Returns the business object ID.
     *
     * @return object ID, or {@code null} for tenant-scope checks
     */
    public String getObjectId() {
        return objectId;
    }

    /**
     * Returns the requested action.
     *
     * @return action
     */
    public String getAction() {
        return action;
    }
}