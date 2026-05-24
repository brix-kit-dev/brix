/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package io.brix.platform.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request for creating the first formal platform super administrator. */
public record CreateFirstAdminRequest(
        @NotBlank(message = "username must not be blank")
        @Size(max = 128, message = "username must not exceed 128 characters")
        String username,

        @NotBlank(message = "email must not be blank")
        @Email(message = "email must be a valid email address")
        @Size(max = 256, message = "email must not exceed 256 characters")
        String email,

        @Size(max = 1000, message = "notes must not exceed 1000 characters")
        String notes
) {}