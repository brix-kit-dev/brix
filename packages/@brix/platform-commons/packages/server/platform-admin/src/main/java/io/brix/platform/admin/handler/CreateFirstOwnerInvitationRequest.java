/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.admin.handler;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request to send the initial tenant OWNER invitation.
 *
 * @param inviteeEmail target actor email
 * @param inviteBaseUrl externally configured invite acceptance base URL
 * @param locale optional notification locale
 * @author Brix Platform Team
 * @since 3.2.0
 */
public record CreateFirstOwnerInvitationRequest(
        @NotBlank(message = "inviteeEmail must not be blank")
        @Email(message = "inviteeEmail must be a valid email address")
        String inviteeEmail,
        @NotBlank(message = "inviteBaseUrl must not be blank")
        @Size(max = 2048, message = "inviteBaseUrl must not exceed 2048 characters")
        String inviteBaseUrl,
        String locale) {
}
