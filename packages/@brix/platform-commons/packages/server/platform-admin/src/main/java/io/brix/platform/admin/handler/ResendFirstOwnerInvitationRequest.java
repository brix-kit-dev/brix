/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

/**
 * Request to revoke and resend the current pending FIRST_OWNER invitation.
 *
 * @param locale optional notification locale
 * @author Brix Platform Team
 * @since 3.2.0
 */
public record ResendFirstOwnerInvitationRequest(
        String locale) {
}
