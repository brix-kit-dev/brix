/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.enums;

/**
 * Tenant invitation lifecycle state.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public enum InvitationStatus {

    /** Token is usable until expiry or revocation. */
    PENDING,

    /** Token has been accepted and must not be reused. */
    ACCEPTED,

    /** Token expired before use. */
    EXPIRED,

    /** Token was explicitly revoked. */
    REVOKED
}
