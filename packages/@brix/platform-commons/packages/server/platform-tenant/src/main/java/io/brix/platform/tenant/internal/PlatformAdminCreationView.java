/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 */
package io.brix.platform.tenant.internal;

/**
 * Platform administrator creation view without setup-token material.
 *
 * @param id platform-admin grant id
 * @param identityId identity id
 * @param setupLinkSent whether managed notification was accepted
 */
public record PlatformAdminCreationView(Long id, Long identityId, boolean setupLinkSent) {
}
