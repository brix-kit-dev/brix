/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 */
package io.brix.platform.auth.endpoint;

/**
 * Public login request DTO for Runtime Shell auth endpoints.
 *
 * @param loginId stable login identifier
 * @param password user password
 */
public record LoginRequestDto(String loginId, String password) {
}
