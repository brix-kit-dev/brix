/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

/**
 * Request to revoke a tenant's pending FIRST_OWNER invitation. The path names both, and its schema lets nothing else
 * come with them.
 *
 * @param tenantId the tenant
 * @param invitationId its pending FIRST_OWNER invitation
 * @author Brix Platform Team
 * @since 3.2.0
 */
public record RevokeFirstOwnerInvitationRequest(
        Long tenantId,
        Long invitationId) {
}
