/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.enums;

/**
 * Authority domain that created a tenant invitation.
 *
 * @author Brix Platform Team
 * @since 3.2.0
 */
public enum InvitationInviterType {

    /** Platform administrator acting through the platform operational surface. */
    PLATFORM_ADMIN,

    /** Existing tenant member acting within an activated tenant context. */
    TENANT_MEMBER
}
